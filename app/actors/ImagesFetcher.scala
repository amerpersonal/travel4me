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
import models.Trip

/**
  * Created by amer.zildzic on 10/5/16.
  */
class ImagesFetcher @Inject()(config: play.api.Configuration, cs: ClusterSetup, ef: PlayElasticFactory) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  implicit val formats = org.json4s.DefaultFormats

  private lazy val client = ef(cs)
  private val base_url = config.underlying.getString("flickr.search_url")
  private val api_key = config.underlying.getString("flickr.api_key")
  private val default_img = config.underlying.getString("general.default_img_path") + "/" + config.underlying.getString("general.default_img")


  case class FetchSearchTerms()
  case class TermsFetched(trip: Trip)
  case class ImagesFetched(trip_id: String, images: Seq[String])

  val scheduler = context.system.scheduler.schedule(
    initialDelay = 1.minutes,
    message = FetchSearchTerms,
    interval = 1.minutes,
    receiver = self
  )(executionContext)

  override  def receive:Receive = {
    case FetchSearchTerms => fetchSearchTerms()
    case TermsFetched(trip: Trip) => fetchImages(trip: Trip)
    case ImagesFetched(trip_id: String, images: Seq[String]) => log.info(s"Fetched images for trip $trip_id: \n" + images.mkString("\n"))

  }

  def fetchImages(trip: Trip, justPlace: Boolean = false): Unit = {
    System.out.println(s"fetch for trip $trip")

    val term: String = trip.title
    val place: String = trip.place
    val terms: List[String] = term.split(" ").toList.flatMap { _.trim.split(",").map(_.trim) }
//    val terms_string = term.replaceAll(",", " ") + " tourism travel trip nature buildings," + place + " tourism travel trip nature buildings"
    var termsString = place.toLowerCase()
    if(!justPlace) termsString += " nature buildings culture"
    log.info(s"Fetching images for trip $termsString")


    val term_encoded = views.html.helper.urlEncode(termsString)
    val url = s"$base_url&api_key=$api_key&text=$term_encoded"
    println(s"url: $url")
    val images_json = Source.fromURL(url).mkString.replace("jsonFlickrApi(", "").dropRight(1)

    val images_details: Map[String, Any] = parse(images_json).extract[Map[String, Any]].get("photos") match {
      case Some(vals) => vals.asInstanceOf[Map[String, Any]]
      case None => Map.empty[String, Any]
    }


    images_details.get("photo") match {
      case Some(rawPhotos) => {
        val old_images = trip.image_collection match {
          case Some(coll) => coll.filter(img => img != default_img)
          case _ => List.empty[String]
        }

        val photos = rawPhotos.asInstanceOf[List[Map[String, String]]]
        if(photos.isEmpty) fetchImages(trip, true)
        else {
          System.out.println(s"old images $old_images")
          val images = old_images ::: (for (photo <- photos) yield urlForPhoto(photo))
          System.out.println(s"images: $images")

          client.execute {
            update id trip.id.get in "trips/trip" doc (("image_collection", images))
          }.onComplete {
            case Success(res) => self ! ImagesFetched(trip.id.get, images)
            case Failure(ex) => log.error(s"Error while indexing images for trip $trip.id.get" + ex.getMessage)
          }
        }

      }
      case None => {}
    }

  }

  def urlForPhoto(photo: Map[String, String]): String = {
    val photo_id = photo("id")
    val sizes_url = s"https://api.flickr.com/services/rest/?method=flickr.photos.getSizes&api_key=$api_key&photo_id=$photo_id&format=json&nojsoncallback=1"

    val sizes_str = Source.fromURL(sizes_url).mkString
    val sizes_details: Map[String, Any] = parse(sizes_str).extract[Map[String, Any]].get("sizes") match {
      case Some(vals) => vals.asInstanceOf[Map[String, Any]]
      case None => Map.empty[String, Any]
    }

    val sizes: List[Map[String, Any]] = sizes_details.get("size") match {
      case Some(vals) => vals.asInstanceOf[List[Map[String, String]]]
      case None => List()
    }

    val source = if(! sizes.isEmpty) sizes.last("source").toString else "https://farm" + photo("farm") + ".staticflickr.com/" + photo("server") + "/" + photo("id") + "_" + photo("secret") + ".jpg"

    source
  }

  def fetchSearchTerms(): Unit = {
    client.execute{
      search in "trips/trip" query must(not(prefixQuery("image_collection.ngram", "flickr")), existsQuery("title")) sort fieldSort("created_timestamp").order(SortOrder.DESC) size 1
    }.onComplete(r => r match {
      case Success(res) => if(res.getHits.totalHits() > 0) self ! TermsFetched(res.as[Trip].head)
      case Failure(ex) => log.error("Failure while fetching terms: " + ex.getMessage)
    })

  }

  override def postStop(): Unit = scheduler.cancel()
}

class ImagesFetcherModule extends AbstractModule with AkkaGuiceSupport {
  def configure(): Unit =
    bindActor[ImagesFetcher]("tripImagesFetcher")
}
