package helpers

import javax.inject.Inject
import models.User
import org.json4s.jackson.JsonMethods
import org.mindrot.jbcrypt.BCrypt
import play.api.mvc.Request
import org.joda.time.DateTime
import play.api.Configuration
/**
  * Created by amer.zildzic on 10/10/16.
  */

//TODO: extend session every time when loggedInUser return user
object SessionHelpers {
  implicit val formats = org.json4s.DefaultFormats
  private val session_duration_ms = play.api.Play.current.configuration.underlying.getLong("play.http.session.duration") * 1000

  def hasSession(request: Request[Any]) : Boolean = {
    if(sessionExpired(request)) false
    else request.session.get("current_user") != None
  }

  def loggedInUser(request: Request[Any]) : User = {
    val usr = if(sessionExpired(request)) None else request.session.get("current_user")
    usr match {
      case Some(value) => {
        val usr_map: Map[String, String] = JsonMethods.parse(value).extract[Map[String, String]]
        User(usr_map.get("id"), usr_map("email"), usr_map("password"), usr_map("password")).set_salt(usr_map("salt"))
      }
      case None => null
    }
  }

  def verificationString(request: Request[Any], default_ts: Long = 0): String = {
    val usr = if(sessionExpired(request)) None else request.session.get("current_user")
    usr match {
      case Some(value) => {
        val usr_map: Map[String, String] = JsonMethods.parse(value).extract[Map[String, String]]
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

  def sessionExpired(request: Request[Any]): Boolean = {
    request.session.get("started") match {
      case Some(millis_str) => millis_str.toLong + session_duration_ms < DateTime.now.getMillis
      case None => true
    }
  }

}
