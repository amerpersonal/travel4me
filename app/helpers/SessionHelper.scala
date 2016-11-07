package helpers

import models.User
import org.json4s.jackson.JsonMethods
import org.mindrot.jbcrypt.BCrypt
import play.api.mvc.Request
import org.joda.time.DateTime
import play.api.Logger
/**
  * Created by amer.zildzic on 10/10/16.
  */

trait SessionHelper {
  implicit val formats = org.json4s.DefaultFormats
//  private val session_duration_ms = play.api.Play.current.configuration.underlying.getLong("play.http.session.duration") * 1000

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

object SessionHelper extends SessionHelper
