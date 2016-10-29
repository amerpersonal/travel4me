package controllers

import javax.inject.Inject

import play.api.mvc._
import models._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.JsonMethods._
import play.api.libs.json.Json
import org.json4s.jackson.Serialization

/**
  * Created by amer.zildzic on 10/10/16.
  */
class ApplicationController @Inject() extends Controller {
  implicit val formats = org.json4s.DefaultFormats

  protected def currentUser(request: Request[Any]): User = {
    helpers.SessionHelpers.loggedInUser(request)
  }

  protected def toJson(obj: AnyRef) = Serialization.write(obj)
}
