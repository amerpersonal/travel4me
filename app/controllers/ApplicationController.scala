package controllers

import javax.inject.Inject

import akka.stream.Materializer
import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import com.sksamuel.elastic4s.ElasticDsl._
import play.api.mvc._
import models._
import play.api.libs.json._
import org.json4s.jackson.Serialization
import helpers.SessionHelper

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
/**
  * Created by amer.zildzic on 10/10/16.
  */
class ApplicationController @Inject()(ef: PlayElasticFactory, cs: ClusterSetup)(implicit exec: ExecutionContext) extends Controller with SessionHelper {
  protected lazy val client = ef(cs)

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

  case class AuthRestricted[T](action: Action[T]) extends Action[T] {
    def apply(request: Request[T]): Future[Result] = {
      if(hasSession(request)) action(request)
      else Future { Redirect(routes.LoginController.signin()).withNewSession }
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
      val logged_in_user: User = loggedInUser(request)
      if(logged_in_user != null){
        val p = Promise[Result]

        val id = request.path.split("/").tail.tail.head
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

    override def composeAction[T](action: Action[T]): Action[T] = {
      AuthRestricted(OwnerRestricted(action))
    }
  }

  protected def toJson(obj: AnyRef) = Serialization.write(obj)
}
