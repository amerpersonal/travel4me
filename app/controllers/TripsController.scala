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
import com.sksamuel.elastic4s.ElasticDsl._
import org.joda.time.DateTime
import models._
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.libs.json._

import scala.util.{Failure, Success}
import java.io.File

import akka.actor.ActorSystem
import play.api.{Configuration, Logger}
import play.api.Application

import scala.concurrent.duration._


class TripsController @Inject()(cs: ClusterSetup, ef: PlayElasticFactory, actorSystem: ActorSystem, app: Provider[Application])(implicit exec: ExecutionContext) extends ApplicationController(ef: PlayElasticFactory, cs: ClusterSetup) {
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
              "created_timestamp" -> DateTime.now.toString("y-M-d H:m:s Z"),
              "updated_timestamp" -> DateTime.now.toString("y-M-d H:m:s Z"),
              "user_id" -> (loggedInUser(request) match {
                case user: User => user.id.get
                case null => null
              }),
              "public" -> (loggedInUser(request) == null || trip.public)
              )
          }.onComplete {
            case Success(res) => p.success(Ok(JsObject(List(("id", JsString(res.getId))))))
            case Failure(ex) => p.success(InternalServerError("Error. Try again later"))
          }

          val timeoutFuture = akka.pattern.after(10.millis, actorSystem.scheduler)(p.future)
          timeoutFuture

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
      val filepath = app.get.path.getAbsolutePath + s"/public/images/" + filename

      Logger.info(s"Saving image to path $filepath")
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
        Logger.error(ex.getMessage)
        Future { InternalServerError("System down. Try again later") }
      }
    }

  }

  def remove(id: String) = OwnerRestrictedAction.async { implicit request =>
    val p = Promise[Result]()
    client.execute {
      delete id id from "trips/trip"
    }.onComplete {
      case Success(r) => p.success(Redirect(routes.HomeController.index("all")))
      case Failure(ex) => p.success(Redirect(routes.HomeController.index("all")))
    }

    val timeoutFuture = akka.pattern.after(10.millis, actorSystem.scheduler)(p.future)
    timeoutFuture
  }

  def my(id: String = "") = AuthRestrictedAction.async { implicit request =>
    Trip.forUser(client, loggedInUser(request).id.get).map { trips =>
      val trip: Trip = if(! id.isEmpty) trips.filter(t => t.id.get == id).head else trips.head
      Ok(views.html.my(trip, trips))
    }
  }

  def change(id: String) = OwnerRestrictedAction.async { implicit request =>
    val title = request.body.asMultipartFormData.get.asFormUrlEncoded.get("title").getOrElse(Vector("")).head
    val description = request.body.asMultipartFormData.get.asFormUrlEncoded.get("description").getOrElse(Vector("")).head
    val custom_label = request.body.asMultipartFormData.get.asFormUrlEncoded.get("custom_label").getOrElse(Vector()).filter(!_.isEmpty)
    val pred_labels = request.body.asMultipartFormData.get.asFormUrlEncoded.get("check_labels").getOrElse(Vector())
    val labels = pred_labels ++ custom_label
    val public = request.body.asMultipartFormData.get.asFormUrlEncoded.get("type").getOrElse(Vector("")).head == "public"

    val trip = Trip(Some(id), title, description, public, loggedInUser(request).id, Some(labels.toList), None, org.joda.time.DateTime.now)

    if(trip.isValid) {
      val p = Promise[Result]()

      client.execute {
        get id id from "trips/trip" fields "title"
      }.map(res => {
        if(res.isExists){
          val res = client.execute {
            update id id in "trips/trip" doc Map("title" -> trip.title, "description" -> trip.description, "labels" -> trip.labels.get, "public" -> trip.public, "updated_timestamp" -> trip.updated_timestamp.toString("y-M-d H:m:s Z"))
          }.onComplete {
            case Success(res) => p.success(Redirect(routes.TripsController.my(trip.id.get)))
            case Failure(ex) => p.success(BadRequest(s"Error while updating trip: $ex.getMessage"))
          }
        }
        else p.success(BadRequest("Not Found"))
      })

      val timeoutFuture = akka.pattern.after(2.millis, actorSystem.scheduler)(p.future)
      timeoutFuture

    }
    else {
      Future { Redirect(routes.TripsController.my(id)).flashing("error" -> "Title and description needs to be provided") }
    }


  }

  def removeImage(id: String) = OwnerRestrictedAction.async { implicit request =>
    val p = Promise[Result]()
    val image_name = request.body.asFormUrlEncoded.get("image_to_remove").head

    if(image_name.isEmpty) p.success(Redirect(routes.TripsController.my(id)))
    else {
      client.execute {
        get id id from "trips/trip" fields("title", "image_collection")
      }.map(res => {
        if (res.isExists) {
          val res = client.execute {
            update id id in "trips/trip" script {
              script("ctx._source.image_collection.remove(fn)").param("fn", image_name)
            }
          }.onComplete {
            case Success(res) => {
              if(image_name.contains(id)) {
                val file = new java.io.File(s"/public/images/$image_name")
                file.delete()
              }
              p.success(Redirect(routes.TripsController.my(id)))
            }
            case Failure(ex) => p.success(BadRequest(s"Error while updating trip: $ex.getMessage"))
          }
        }
        else p.success(BadRequest("Not Found"))
      })
    }

    val timeoutFuture = akka.pattern.after(10.millis, actorSystem.scheduler)(p.future)
    timeoutFuture
  }

}