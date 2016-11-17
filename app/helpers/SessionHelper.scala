package helpers

import models.User
import org.json4s.jackson.JsonMethods
import org.mindrot.jbcrypt.BCrypt
import play.api.mvc.{Request, RequestHeader}
import org.joda.time.DateTime
import play.api.Logger

import scala.util.Try
/**
  * Created by amer.zildzic on 10/10/16.
  */

trait SessionHelper {
  implicit val formats = org.json4s.DefaultFormats


  def hasSession(request: Request[Any]) : Boolean = request.session.get("current_user") != None

  def loggedInUser(request: Request[Any]) : User = {
    request.session.get("current_user") match {
      case Some(user) => {
        val usr_map: Map[String, String] = JsonMethods.parse(user).extract[Map[String, String]]
        User(usr_map.get("id"), usr_map("email"), usr_map("password"), usr_map("password")).set_salt(usr_map("salt"))
      }
      case None => null
    }
  }

  def verificationString(request: Request[Any], default_ts: Long = 0): String = {
    request.session.get("current_user") match {
      case Some(user) => {
        val usr_map: Map[String, String] = JsonMethods.parse(user).extract[Map[String, String]]
        val usr = User(usr_map.get("id"), usr_map("email"), usr_map("password"), usr_map("password")).set_salt(usr_map("salt"))
        val ts = if(default_ts == 0) DateTime.now().getMillis().toString() else default_ts.toString()
        usr.salt match {
          case salt:String => {
            BCrypt.hashpw(usr.password + ":" + ts, salt) + ":" + ts
          }
          case _ => null
        }
      }
      case None => null
    }
  }



}

object SessionHelper extends SessionHelper {
  var session_duration_ms: Long = 999999999

  def set_duration(duration: Long) = {
    session_duration_ms = duration
  }

  def isSessionExpired(request: RequestHeader): Boolean = {
    val st = request.session.get("started")

    request.session.get("started") match {
      case Some(started) => Try(started.toLong).toOption match {
        case Some(int_started) => {
          val sessionStartedAgo = org.joda.time.DateTime.now.getMillis - int_started
          println("started ago: " + sessionStartedAgo)
          println("session_duration: " + session_duration_ms)
          sessionStartedAgo > session_duration_ms
        }
        case None => false
      }
      case None => false
    }
  }
}
