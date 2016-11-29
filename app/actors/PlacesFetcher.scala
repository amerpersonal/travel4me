package actors

import javax.inject._

import akka.actor._
import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.google.inject.AbstractModule
import com.sksamuel.elastic4s.BulkCompatibleDefinition
import com.sksamuel.elastic4s.ElasticDsl._
import models.Trip
import org.elasticsearch.search.sort.SortOrder
import org.json4s._
import org.json4s.jackson.JsonMethods._
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Failure, Success}

/**
  * Created by amer.zildzic on 10/5/16.
  */
class PlacesFetcher @Inject()(config: play.api.Configuration, cs: ClusterSetup, ef: PlayElasticFactory) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  implicit val formats = org.json4s.DefaultFormats

  private lazy val client = ef(cs)
  private val fetchUrl = config.underlying.getString("data.cities.fetch_url")

  case class Place(city: String, country: String)
  case class FetchPlaces()
  case class StorePlaces(cities: List[Map[String, String]])


//  val scheduler = context.system.scheduler.schedule(
//    initialDelay = 40.minutes,
//    message = FetchPlaces,
//    interval = 1.minutes,
//    receiver = self
//  )(executionContext)

  override  def receive:Receive = {
    case FetchPlaces => fetchPlaces
    case StorePlaces(placesDetails: List[Map[String, String]]) => storePlaces(placesDetails)
  }

  def storePlaces(placesDetails: List[Map[String, String]]) = {
    client.execute {
      indexExists("places")
    }.onComplete {
      case Success(res) => {
        log.debug("places exists" + res.isExists)

        if(res.isExists) addPlacesToIndex(placesDetails)
        else {
          val currentTime = org.joda.time.DateTime.now.getMillis
          val indexName = s"places_$currentTime"
          client.execute {
            create index(indexName) refreshInterval(50.millisecond)
          }.onComplete {
            case Success(res) => {
              client.execute {
                add alias "places" on indexName
              }.onComplete {
                case Success(res) => {
                  if(res.isAcknowledged) addPlacesToIndex(placesDetails)
                  else println("add alias not ack")
                }
                case Failure(ex) => println("failure on add alias " + ex.getMessage)
              }
            }
            case Failure(ex) => println("failure when create index " + ex.getMessage)
          }
        }
      }
    }
  }

  def addPlacesToIndex(placesDetails: List[Map[String, String]]) = {
    log.debug("add places to index")

    val indexRequests: List[BulkCompatibleDefinition] = placesDetails.map { place =>
      val id = place("CapitalName").toString.toLowerCase.replaceAll(" ", "_")
      index into "places/city" id id fields("country" -> place("CountryName"), "city" -> place("CapitalName"))
    }

    println(indexRequests)

    client.execute {
      bulk(indexRequests)
    }.onComplete {
      case Success(res) => println("Indexed " + res.items.length + "places")
    }
  }

  def fetchPlaces = {
    println("fetching places")
    val placesJson = Source.fromURL(fetchUrl).mkString

    println("places")
    println(placesJson)

    val placesDetails: List[Map[String, String]] = parse(placesJson).extract[List[Map[String, String]]]
    println("placesDetails")
    println(placesDetails)

    self ! StorePlaces(placesDetails)

  }

  override def postStop(): Unit = {}//scheduler.cancel()
}

class PlacesFetcherModule extends AbstractModule with AkkaGuiceSupport {
  def configure(): Unit =
    bindActor[PlacesFetcher]("placesFetcher")
}
