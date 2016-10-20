package actors

import akka.actor._
import javax.inject._

import scala.concurrent.duration._
import scala.io.Source
import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import models._
import scala.collection.JavaConverters._
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.json._
import org.json4s._
import org.json4s.jackson.JsonMethods._

/**
  * Created by amer.zildzic on 10/5/16.
  */
class ImagesFetcher @Inject()(conf: play.api.Configuration, cs: ClusterSetup, ef: PlayElasticFactory) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  implicit val formats = org.json4s.DefaultFormats

  implicit object TripHitAs extends HitAs[Trip] {
    override def as(hit: RichSearchHit): Trip = {
      val source = hit.getSource

      val user_id: Option[String] = source.get("user_id") match {
        case Some(userid) => Some(userid.toString)
        case _ => None
      }
      Trip(Some(hit.getId), source.get("title").toString, source.get("description").toString,
        source.get("public").asInstanceOf[Boolean], user_id, Some(source.get("labels").asInstanceOf[java.util.ArrayList[String]].asScala.toList))
    }
  }

  private lazy val client = ef(cs)
  private val base_url = conf.underlying.getString("flickr.search_url")
  private val api_key = conf.underlying.getString("flickr.api_key")


  case class FetchSearchTerms()
  case class TermsFetched(trip: Trip)
  case class ImagesFetched(trip_id: String, images: Seq[String])

  val scheduler = context.system.scheduler.schedule(
    initialDelay = 5.minutes,
    message = FetchSearchTerms,
    interval = 1.minute,
    receiver = self
  )(executionContext)

  override  def receive:Receive = {
    case FetchSearchTerms => fetchSearchTerms()
    case TermsFetched(trip: Trip) => fetchImages(trip: Trip)
    case ImagesFetched(trip_id: String, images: Seq[String]) => System.out.println(s"Fetched images for trip $trip_id: \n" + images.mkString("\n"))

  }

  def fetchImages(trip: Trip) = {
    val term: String = trip.title
    val terms: List[String] = term.split(" ").toList.flatMap { _.trim.split(",").map(_.trim) }
    val terms_string = terms.mkString(",")
    System.out.println(s"Fetching images for trip $terms_string")


    val term_encoded = views.html.helper.urlEncode(terms_string)
    val url = s"$base_url&api_key=$api_key&tags=$term_encoded"
    System.out.println(url)
    val images_json = Source.fromURL(url).mkString.replace("jsonFlickrApi(", "").dropRight(1)

    val images_details: Map[String, Any] = parse(images_json).extract[Map[String, Any]].get("photos") match {
      case Some(vals) => vals.asInstanceOf[Map[String, Any]]
      case None => Map.empty[String, Any]
    }


    images_details.get("photo") match {
      case Some(photos) => {
        val old_images = trip.image_collection match {
          case Some(coll) => coll
          case _ => List.empty[String]
        }

        val images = old_images ::: (for (photo <- photos.asInstanceOf[List[Map[String, String]]]) yield urlForPhoto(photo)).toList
        System.out.println(images)

        client.execute {
          update id trip.id.get in "trips/trip" doc(("image_collection", images))
        }.onComplete {
          case Success(res) => self ! ImagesFetched(trip.id.get, images)
          case Failure(ex) => System.out.println(s"Error while indexing images for trip $trip.id.get" + ex.getMessage)
        }

      }
    }

  }

  def urlForPhoto(photo: Map[String, String]): String = {
    "https://farm" + photo("farm") + ".staticflickr.com/" + photo("server") + "/" + photo("id") + "_" + photo("secret") + ".jpg"
  }

  def fetchSearchTerms(): Unit = {
    client.execute{
      val sr = search in "trips/trip" query must(not(prefixQuery("image_collection.ngram", "flickr")), existsQuery("title")) sort fieldSort("created_timestamp").order(SortOrder.DESC) size 1
      System.out.println(sr.toString())
      sr
    }.onComplete(r => r match {
      case Success(res) => {
        val hits = res.getHits
        if(hits.totalHits() > 0) self ! TermsFetched(res.as[Trip].head)
      }
      case Failure(ex) => System.out.println("Failure while fetching terms: " + ex.getMessage)
    })

  }

  override def postStop(): Unit = scheduler.cancel()
}

class ImagesFetcherModule extends AbstractModule with AkkaGuiceSupport {
  def configure(): Unit =
    bindActor[ImagesFetcher]("fetcher")
}
