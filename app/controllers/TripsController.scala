package controllers

/**
  * Created by amer.zildzic on 9/20/16.
  */
import java.util.UUID

import javax.inject._

import play.api.mvc._
import play.Play

import scala.concurrent.{ExecutionContext, Future, Promise}
import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.ElasticDsl._
import org.joda.time.DateTime
import play.api.data.Forms._
import play.api.data.Form
import models._
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.libs.json._

import scala.util.{Failure, Success}
import java.io.File

import scala.collection.JavaConverters._
import org.elasticsearch.search.sort.SortOrder

import scala.util.parsing.json.JSONObject


class TripsController @Inject() (cs: ClusterSetup, ef: PlayElasticFactory)(implicit exec: ExecutionContext) extends ApplicationController {

  private lazy val client = ef(cs)

  implicit object TripHitAs extends HitAs[Trip] {
    override def as(hit: RichSearchHit): Trip = {
      val source = hit.getSource

      val user_id: Option[String] = source.get("user_id") match {
        case Some(userid) => Some(userid.toString)
        case None => None
      }
      Trip(Some(hit.getId), source.get("title").toString, source.get("description").toString,
        source.get("public").asInstanceOf[Boolean], user_id, Some(source.get("labels").asInstanceOf[java.util.ArrayList[String]].asScala.toList))
    }
  }

  implicit object ArrayFormat extends Format[Array[Trip]] {
    override def writes(trips: Array[Trip]): JsValue = {
      JsObject.apply(trips.map(trip => trip.id match {
        case Some(value) => value -> Json.toJson(trip)
        case None => "" ->  Json.toJson(trip)
      }))
    }

    override def reads(json: JsValue): JsResult[Array[Trip]] = {
      JsSuccess(Array.empty)
    }
  }


  def create = Action.async(parse.json) { implicit request =>
    request.body.validate[Trip] match {
      case t: JsSuccess[Trip] => {
        val trip: Trip = t.get
        System.out.println(trip)
        try {
          val p = Promise[Result]
          client.execute {
            index into "trips/trip" id UUID.randomUUID() fields(
              "title" -> trip.title,
              "description" -> trip.description,
              "labels" -> (trip.labels match {
                case Some(vals) => vals
                case None => None
              }),
              "created_timestamp" -> DateTime.now.toString("yyyy-mm-dd HH:mm:ss Z"),
              "user_id" -> (currentUser(request) match {
                case user: User => user.id.get
                case null => null
              }),
              "public" -> (currentUser(request) == null || trip.public)
              )
          }.onComplete {
            case Success(res) => p.success(Ok(JsObject(List(("id", JsString(res.getId))))))
            case Failure(ex) => p.success(InternalServerError("Error. Try again later"))
          }

          p.future

        }
        catch {
            case _ : Throwable => {
              Future { InternalServerError("Error. Try again later") }
            }
        }
    }
      case e: JsError =>  {
        System.out.println(e.errors.head.toString())
        System.out.println(e.errors.head._2)
        Future { BadRequest("Trip title and description have to be provided") }
      }
    }

  }

  def upload(id: String) = Action.async { implicit request =>
    request.body.asMultipartFormData.get.file("image").map { picture =>
      val filename = picture.filename
      var filepath = Play.application().path() + s"/public/images/$id" + "_" + filename
      System.out.println(s"Saving image to path $filepath")
      picture.ref.moveTo(new File(filepath))
      client.execute {
        update id id in "trips/trip" script {
          script("!ctx._source.containsKey(\"image_collection\") ? (ctx._source.image_collection = [fn]) : (ctx._source.image_collection.push(fn))").params("fn" -> filename)
        }

      }.map(r => Ok(Json.obj("filename" -> filename, "id" -> r.getId)))

    }.getOrElse(Future { BadRequest(Json.obj("id" -> id, "error" -> "Missing file")) })
  }

  def browse = Action.async { implicit request =>

    // TODO: resursive function for converting jsobject to map
    val query_body: Map[String, Object] = request.body.asJson match {
      case Some(JsObject(fields)) => fields.map(kv => {
        kv._1 match {
          case "search" | "filter" | "sort" =>
            def pairs_split_strip(pair: String): (String, String) = {
              val res = pair.split(":").map(p => p.substring(1, p.length - 1))
              (res(0), res(1))
            }
            val pairs = kv._2.toString().substring(1, kv._2.toString().length-1).split(",")
            kv._1 -> pairs.map(pair => pairs_split_strip(pair)).toMap
          case "size" | "from" => kv._1 -> kv._2
          case _ => "search" -> kv._2.toString().substring(1, kv._2.toString().length-1).split(",").map(pair => (pair.split(":").toList.head, pair.split(":").toList.tail.head)).toMap
        }
      }).toMap
      case _ => Map.empty
    }


    System.out.println(query_body)
    try {
      client.execute {
        var sr = search in "trips/trip"

        val filters: Map[String, String] = query_body.get("filter") match {
          case Some(vals) => vals.asInstanceOf[Map[String, String]]
          case None => Map.empty[String, String]
        }

        val queries: List[QueryDefinition] = query_body.get("search") match {
          case Some(vals) => vals.asInstanceOf[Map[String, String]].toList.map(kv =>
            filters.get(kv._1) match {
              case Some(value) => List(termQuery(kv._1, value))
              case None => List(prefixQuery(kv._1 + ".analyzed", kv._2), prefixQuery(kv._1 + ".ngram", kv._2), termQuery(kv._1,kv._2))
            }
          ).flatten
          case _ => List(matchAllQuery)
        }

        val sorts: List[SortDefinition] = query_body.get("sort") match {
          case Some(vals) => vals.asInstanceOf[Map[String, String]].toList.map(kv => fieldSort(kv._1).order(if(kv._2 == "desc") SortOrder.DESC else SortOrder.ASC))
          case None => List.empty
        }

        sr.query(should(queries))
        sr.sort(sorts: _*)

        query_body.get("size") match {
          case Some(JsNumber(size)) => sr.size(size.intValue())
          case None => {}
        }

        query_body.get("from") match {
          case Some(JsNumber(from)) => sr.from(from.intValue())
          case None => {}
        }
        System.out.println(sr.toString())
        sr
      }.map { res =>
        val trips = res.as[Trip]
        Ok(Json.toJson(trips))
      }
    }
    catch {
      case ex : Throwable => {
        System.out.println(ex.getMessage)
        Future { InternalServerError("System down. Try again later") }
      }
    }

  }


}