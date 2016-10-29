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

import helpers._

class TripsController @Inject() (cs: ClusterSetup, ef: PlayElasticFactory)(implicit exec: ExecutionContext) extends ApplicationController {

//  val tripForm = Form(play.api.data.Forms.mapping("id" -> optional(text), "title" -> nonEmptyText, "description" -> nonEmptyText,
//    "type" -> optional(Boolean))


  case class AuthRestricted[T](action: Action[T]) extends Action[T] {
    def apply(request: Request[T]): Future[Result] = {
      if(SessionHelpers.hasSession(request)) action(request)
      else Future { Redirect(routes.LoginController.signin()) }
    }

    lazy val parser = action.parser
  }

  object AuthRestrictedAction extends ActionBuilder[Request] {
    def invokeBlock[T](request: Request[T], block: (Request[T] => Future[Result])): Future[Result] = {
      block(request)
    }

    override def composeAction[A](action: Action[A]): Action[A] = new AuthRestricted(action)
  }

  case class OwnerRestricted[T](action: Action[T]) extends Action[T] {
    def apply(request: Request[T]): Future[Result] = {
      val logged_in_user: User = helpers.SessionHelpers.loggedInUser(request)
      System.out.println("owner rest")
      if(logged_in_user != null){
        val p = Promise[Result]

        val id = request.path.split("/").last
        System.out.println(s"=== id: $id")
        client.execute {
          get id id from "trips/trip" fields "user_id"
        }.onComplete {
          case Success(res) => {
            if(res.isExists && res.field("user_id").getValue.toString == logged_in_user.id.get) {
              action(request).onComplete {
                case Success(res) => p.success(res)
                case Failure(ex) => p.success(BadRequest("Forbidden"))
              }
            }
          }
          case Failure(ex) => p.success(BadRequest("Forbidden"))
        }

        p.future
      }
      else Future { BadRequest("Forbidden") }
    }
    lazy val parser = action.parser
  }

  object OwnerRestrictedAction extends ActionBuilder[Request] {
    def invokeBlock[T](request: Request[T], block: (Request[T]) => Future[Result]) = {
      block(request)
    }

    override def composeAction[T](action: Action[T]): Action[T] = AuthRestricted(OwnerRestricted(action))
  }


  private lazy val client = ef(cs)

  implicit object TripHitAs extends HitAs[Trip] {
    override def as(hit: RichSearchHit): Trip = {
      val source = hit.getSource

      val user_id: Option[String] = source.get("user_id") match {
        case Some(userid) => Some(userid.toString)
        case None => None
      }
      val l = source.get("labels")
      System.out.println(s"labs: $l")

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
        try {
          val p = Promise[Result]

          client.execute {
            index into "trips/trip" id UUID.randomUUID() fields(
              "title" -> trip.title,
              "description" -> trip.description,
              "labels" -> trip.labels.getOrElse(None),
              "created_timestamp" -> DateTime.now.toString("yyyy-mm-dd HH:mm:ss Z"),
              "updated_timestamp" -> DateTime.now.toString("yyyy-mm-dd HH:mm:ss Z"),
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
      case e: JsError => Future { BadRequest("Trip title and description have to be provided") }
    }

  }

  def upload(id: String) = Action.async { implicit request =>
    request.body.asMultipartFormData.get.file("image").map { picture =>
      val filename = id + "_" + picture.filename
      var filepath = Play.application().path() + s"/public/images/" + filename
      System.out.println(s"Saving image to path $filepath")
      picture.ref.moveTo(new File(filepath))
      client.execute {
        update id id in "trips/trip" script {
          script("!ctx._source.containsKey(\"image_collection\") ? (ctx._source.image_collection = [fn]) : (ctx._source.image_collection.push(fn))").params("fn" -> s"assets/images/$filename")
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

    try {
      val trips: Future[List[Trip]] = Trip.browse(client, query_body)
      trips.map { r => Ok(Json.toJson(r))}
    }
    catch {
      case ex : Throwable => {
        System.out.println(ex.getMessage)
        Future { InternalServerError("System down. Try again later") }
      }
    }

  }

  def remove(id: String) = OwnerRestrictedAction.async { implicit request =>
    client.execute {
      delete id id from "trips/trip"
    }.map(r => Redirect(routes.HomeController.index()))
  }

  def my(id: String = "") = AuthRestrictedAction.async { implicit request =>
    System.out.println(s"tripid: $id")

    Trip.forUser(client, SessionHelpers.loggedInUser(request).id.get).map { trips =>
      val trip: Trip = if(! id.isEmpty) trips.filter(t => t.id.get == id).head else trips.head
      Ok(views.html.my(trip, trips, currentUser(request)))
    }
  }

  def change(id: String) = OwnerRestrictedAction.async { implicit request =>
    val title = request.body.asMultipartFormData.get.asFormUrlEncoded.get("title")
    val description = request.body.asMultipartFormData.get.asFormUrlEncoded.get("description")
    val labels = request.body.asMultipartFormData.get.asFormUrlEncoded.get("check_labels")
    val types = request.body.asMultipartFormData.get.asFormUrlEncoded.get("type")

    System.out.println(s"something title: $title, description: $description, labels: $labels, types: $types")

//    request.body.validate[Trip] match {
//      case t: JsSuccess[Trip] => {
//        client.execute {
//          update id id in "trips/trip" doc Map("title" -> t.get.title, "description" -> t.get.description, "labels" -> t.get.labels.getOrElse(None))
//        }.map(r => Redirect(routes.TripsController.my(t.get.id.getOrElse(""))))
//      }
//      case e: JsError => Future { BadRequest("Invalid trip details") }
//    }

    Future { Ok("ok") }

  }


}