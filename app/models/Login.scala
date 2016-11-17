package models

import com.sksamuel.elastic4s.{HitAs, RichSearchHit}

import scala.util.matching.Regex

/**
  * Created by amer.zildzic on 10/7/16.
  */
case class Login(val email: String, val password: String) extends Base {
  override def serialize: String = ???
}

object Login {
  val email_regex = new Regex("^[-a-z0-9~!$%^&*_=+}{\\'?]+(\\.[-a-z0-9~!$%^&*_=+}{\\'?]+)*@([a-z0-9_][-a-z0-9_]*(\\.[-a-z0-9_]+)*\\.(aero|arpa|biz|com|coop|edu|gov|info|int|mil|museum|name|net|org|pro|travel|mobi|[a-z][a-z])|([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}))(:[0-9]{1,5})?$")

  def validate(email: String, password: String) = {
    if(password.trim.length > 7) Some(Login(email, password)) else None
  }

  implicit object HitAsLogin extends HitAs[Login] {
    override def as(hit: RichSearchHit): Login = {
      val source = hit.getSource

      Login(source.get("email").toString, source.get("password").toString)
    }
  }
}
