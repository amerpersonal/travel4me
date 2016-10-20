package models
import java.util.UUID

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import org.elasticsearch.search.sort.SortOrder
import play.Play
import play.api.libs.json._

import scala.util.Try
/**
  * Created by amer.zildzic on 9/21/16.
  */

import scala.concurrent.Future
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import scala.util.matching.Regex
import scala.collection.JavaConverters._

import services.Search

case class Trip(var id: Option[String] = None,
                val title: String,
                val description: String,
                val public: Boolean,
                val userId: Option[String],
                val labels: Option[List[String]],
                val image_collection: Option[List[String]] = None){
  val uuid_regex = new Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

//
  if(id == None) id = Some(UUID.randomUUID().toString)
  // not using this custom validator currently
  def validate(id: Option[String] = None, title: String, description: String, public: Boolean, labels: Option[List[String]] = None, image_collection: Option[List[String]] = None) = {
    System.out.println("validate")

    if ((id != None && uuid_regex != id) || title.trim.isEmpty || description.trim.isEmpty) None
    else Some(Trip(id, title, description, public, None, labels))
  }
}

object Trip extends Search {
  implicit val exec = scala.concurrent.ExecutionContext.global
  val default_image = play.api.Play.current.configuration.underlying.getString("general.default_img_path") + "/" +
                      play.api.Play.current.configuration.underlying.getString("general.default_img")

  implicit object TripHitAs extends HitAs[Trip] {
    override def as(hit: RichSearchHit): Trip = {
      val source = hit.getSource

      val user_id: Option[String] = source.get("user_id") match {
        case Some(value) => Some(value.toString)
        case _ => None
      }
      val labels = source.get("labels") match {
        case Some(labels) => labels.asInstanceOf[java.util.ArrayList[String]].asScala.toList
        case _ => List()
      }
      val stored_images = source.get("image_collection")
      val images: List[String] = if(stored_images == null) List(default_image) else stored_images.asInstanceOf[java.util.ArrayList[String]].asScala.toList

      Trip(Some(hit.getId), source.get("title").toString, source.get("description").toString,
        source.get("public").asInstanceOf[Boolean], user_id, Some(labels), Some(images))
    }
  }

  def notEmpty(implicit r:Reads[String]):Reads[String] = Reads.filterNot(ValidationError("validate.error.unexpected.value", ""))(_.trim().eq(""))

  implicit val tripReader: Reads[Trip] = {
    (
      (__ \ "id").readNullable[String] and
        (__ \ "title").read[String](notEmpty) and
        (__ \ "description").read[String](notEmpty) and
        (__ \ "type").readNullable[String].map(v => v.getOrElse("") == "public") and
        (__ \ "user_id").readNullable[String] and
        (__ \ "labels").readNullable[List[String]] and
        (__ \ "image_collection").readNullable[List[String]]
      )(Trip.apply _)
  }

  implicit val tripWriter: Writes[Trip] = (
      (JsPath \ "id").write[Option[String]] and
        (JsPath \ "title").write[String] and
        (JsPath \ "description").write[String] and
        (JsPath \ "public").write[Boolean] and
        (JsPath \ "user_id").write[Option[String]] and
        (JsPath \ "labels").write[Option[List[String]]] and
        (JsPath \ "image_collection").write[Option[List[String]]]
    )(unlift(Trip.unapply))

  def browse(client: ElasticClient, options: Map[String, Object]): Future[List[Trip]] = {
    client.execute {
      val default_queries = List(must(existsQuery("title")))
      prepare_search(options, default_queries)
    }.map(r => r.as[Trip].toList)
  }

}







