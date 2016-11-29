package models
import java.util.UUID

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import org.elasticsearch.search.aggregations.support.format.ValueFormatter.DateTime
import org.elasticsearch.search.sort.SortOrder
import play.Play
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._
import play.api.Logger
import scala.util.Try
import javax.inject.Inject
import play.cache.CacheApi
import scala.util.{Success, Failure}

/**
  * Created by amer.zildzic on 9/21/16.
  */

import scala.concurrent.Future
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import scala.util.matching.Regex
import scala.collection.JavaConverters._

import services.Search
import org.joda.time.DateTime
import org.joda.time.format._
import play.api.cache.Cache
import org.json4s.jackson.Serialization
import org.joda.time._

case class Trip (var id: Option[String] = None,
                 val title: String,
                 val place: String,
                 val description: String,
                 val public: Boolean,
                 val startDate: org.joda.time.DateTime,
                 val endDate: org.joda.time.DateTime,
                 val userId: Option[String],
                 val labels: Option[List[String]],
                 val image_collection: Option[List[String]] = None,
                 val updated_timestamp: org.joda.time.DateTime = null) extends Base {
  val uuid_regex = new Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  if(id == None) id = Some(UUID.randomUUID().toString)
  // not using this custom validator currently
  def validate(id: Option[String] = None, title: String, description: String, public: Boolean, startDate: org.joda.time.DateTime,
               endDate: org.joda.time.DateTime, labels: Option[List[String]] = None, image_collection: Option[List[String]] = None) = {
    if ((id != None && uuid_regex != id) || title.trim.isEmpty || description.trim.isEmpty) None
    else Some(Trip(id, title, place, description, public, startDate, endDate, None, labels))
  }

  def isValid: Boolean = (id == None || uuid_regex.pattern.matcher(id.get).matches) && !title.trim.isEmpty && !description.trim.isEmpty && endDate.isAfter(startDate.getMillis)

  def hasLabel(label: String): Boolean = labels match {
    case Some(l) => l.toSet.contains(label)
    case None => false
  }

  def customLabel: String = labels match {
    case Some(labels) => labels.filter(label => !Trip.labels.exists(_._1 == label)).headOption.getOrElse("")
    case None => ""
  }

  def isNew: Boolean = title.isEmpty && description.isEmpty

  override def serialize: String = ???

}

object Trip extends Search {
  val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z")
  val formDateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("MM/dd/yyyy")
  val dateFormatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd 0:0:0 ")

  var default_image_path: String = null

  def set_default_image(path: String) = {
    println("set default image path")
    default_image_path = path
  }

  implicit val tripReader: Reads[Trip] = {
    (
      (__ \ "id").readNullable[String] and
        (__ \ "title").read[String](notEmpty) and
        (__ \ "place").read[String] and
        (__ \ "description").read[String](notEmpty) and
        (__ \ "type").readNullable[String].map(v => v.getOrElse("") == "public") and
        (__ \ "start_date").read[String](validDate).map(formDateFormatter.parseDateTime(_)) and
        (__ \ "end_date").read[String](validDate).map(formDateFormatter.parseDateTime(_)) and
        (__ \ "user_id").readNullable[String] and
        (__ \ "labels").readNullable[List[String]] and
        (__ \ "image_collection").readNullable[List[String]] and
        Reads.pure(org.joda.time.DateTime.now)
      ) (Trip.apply _)
  }

  implicit val tripWriter: Writes[Trip] = (
    (JsPath \ "id").write[Option[String]] and
      (JsPath \ "title").write[String] and
      (JsPath \ "place").write[String] and
      (JsPath \ "description").write[String] and
      (JsPath \ "public").write[Boolean] and
      (JsPath \ "start_date").write[org.joda.time.DateTime] and
      (JsPath \ "end_date").write[org.joda.time.DateTime] and
      (JsPath \ "user_id").write[Option[String]] and
      (JsPath \ "labels").write[Option[List[String]]] and
      (JsPath \ "image_collection").write[Option[List[String]]] and
      (JsPath \ "updated_timestamp").write[org.joda.time.DateTime]
    ) (unlift(Trip.unapply))

  object StringList {
    def apply(labels: List[String]) = {
      var list = new java.util.ArrayList[String]()
      labels.foreach(l => list.add(l))
      list
    }

    def unapply(elements: Object): Option[List[String]] = {
      Try(elements.asInstanceOf[java.util.ArrayList[String]]).toOption match {
        case Some(java_list: Any) => Some(java_list.asScala.toList)
        case _ => None
      }

    }
  }

  implicit object HitAsTrip extends HitAs[Trip] {

    override def as(hit: RichSearchHit): Trip = {
      val source = hit.getSource

      val user_id: Option[String] = source.get("user_id") match {
        case Some(value) => Some(value.toString)
        case value: String => Some(value.toString)
        case _ => None
      }

      val labels = source.get("labels") match {
        case StringList(labels) => labels
        case _ => List()
      }

      val images: List[String] = source.get("image_collection") match {
        case StringList(imgs) => imgs.map(image => if(image.contains("assets/")) image.replace("assets/", "/assets/") else image)
        case _ => List(default_image_path)
      }

      val updated = formatter.parseDateTime(source.get("updated_timestamp").toString)
      val startDate = source.get("start_date") match {
        case value: Object => dateFormatter.parseDateTime(value.toString)
        case _ => org.joda.time.DateTime.now
      }
      val endDate = source.get("end_date") match {
        case value: Object => dateFormatter.parseDateTime(value.toString)
        case _ => org.joda.time.DateTime.now
      }

      Trip(Some(hit.getId), source.get("title").toString, source.get("place").toString, source.get("description").toString,
        source.get("public").asInstanceOf[Boolean], startDate, endDate, user_id, Some(labels), Some(images), updated)
    }
  }

  def getToTrip(hit: RichGetResponse): Trip = {
    val fields = hit.getFields

    val user_id: Option[String] = fields.get("user_id") match {
      case value: AnyRef => Some(value.getValue.toString)
      case _ => None
    }

    val labels = fields.get("labels") match {
      case labels: AnyRef => labels.getValues.toArray.map(x => x.toString).toList
      case _ => List()
    }

    val images: List[String] = fields.get("image_collection") match {
      case imgs: AnyRef => imgs.getValues.toArray.map(x => x.toString).toList.map(image => if(image.contains("assets/")) image.replace("assets/", "/assets/") else image)
      case _ => List(default_image_path)
    }

    val updated = formatter.parseDateTime(fields.get("updated_timestamp").getValue.toString)
    val startDate = fields.get("start_date") match {
      case value: Object => dateFormatter.parseDateTime(value.toString)
      case _ => org.joda.time.DateTime.now
    }
    val endDate = fields.get("end_date") match {
      case value: Object => dateFormatter.parseDateTime(value.toString)
      case _ => org.joda.time.DateTime.now
    }

    Trip(Some(hit.getId), fields.get("title").getValue.toString, fields.get("place").getValue.toString, fields.get("description").getValue.toString,
      fields.get("public").getValue.asInstanceOf[Boolean], startDate, endDate, user_id, Some(labels), Some(images), updated)
  }

  def labels: Map[String, String] = Map("summer_vacation" -> "Summer Vacation", "city_travel" -> "City Travel", "antropology" -> "Antropology")

  def notEmpty(implicit r: Reads[String]): Reads[String] = Reads.filterNot(ValidationError("validate.error.unexpected.value", ""))(_.trim().eq(""))

  def validDate(implicit r: Reads[String]): Reads[String] =
    (Reads.filterNot(ValidationError("validate.error.unexpected.value", ""))(v => Try(formDateFormatter.parseDateTime(v)).toOption == None))

  def browse(client: ElasticClient, options: Map[String, Object]): Future[List[Trip]] = {
    var search_opts = options
    if(search_opts.get("filter") != None){
      var filter = search_opts.get("filter").get.asInstanceOf[Map[String, String]]
      if(filter.get("labels") != None){
        val label = filter.get("labels").get

        if(Trip.labels.get(label) != None) filter = filter.updated("labels", Trip.labels.get(label).get)
      }

      search_opts = search_opts.updated("filter", filter)
    }

    client.execute {
      val default_queries = List(must(existsQuery("title")))
      val sr = prepare_search(search_opts, default_queries)
      Logger.debug(sr.toString())
      sr
    }.map(r => r.as[Trip].toList)
  }

  def forUser(client: ElasticClient, user_id: String): Future[List[Trip]] = {
    client.execute {
      search in "trips/trip" query(termQuery("user_id", user_id))
    }.map(res => res.as[Trip].toList)
  }

  def find(client: ElasticClient, id: String): Future[Trip] = {
    client.execute {
      get id id from "trips/trip" fields("title", "place", "description", "public", "user_id", "labels", "image_collection", "updated_timestamp")
    }.map(r => {
      if(r.isExists) getToTrip(r)
      else null
    })
  }

}







