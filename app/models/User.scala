package models

import java.util.UUID

import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Writes, _}

import scala.util.matching.Regex
/**
  * Created by amer.zildzic on 10/7/16.
  */
case class User(var id: Option[String], val email: String, val password: String, val password_confirmation: String, val salt: Option[String] = None){
  val uuid_regex = new Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  if(id == None) id = Some(UUID.randomUUID().toString)

}

object User {
  val email_regex = new Regex("^[-a-z0-9~!$%^&*_=+}{\\'?]+(\\.[-a-z0-9~!$%^&*_=+}{\\'?]+)*@([a-z0-9_][-a-z0-9_]*(\\.[-a-z0-9_]+)*\\.(aero|arpa|biz|com|coop|edu|gov|info|int|mil|museum|name|net|org|pro|travel|mobi|[a-z][a-z])|([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}))(:[0-9]{1,5})?$")
  val uuid_regex = new Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

  def validate(id: Option[String], email: String, password: String, password_confirmation: String) = {
    if ((id != None && uuid_regex != id) || email.trim.isEmpty || password.trim.isEmpty || password_confirmation.isEmpty
      || password != password_confirmation) None
    else Some(User(id, email, password, password_confirmation, None))
  }

  def notEmpty(implicit r:Reads[String]):Reads[String] = Reads.filterNot(ValidationError("validate.error.unexpected.value", ""))(_.trim().eq(""))

  implicit val userReader: Reads[User] = (
  (__ \ "id").readNullable[String] and
      (__ \ "email").read[String](notEmpty) and
      (__ \ "password").read[String](notEmpty) and
      (__ \ "password_confirmation").read[String] and
        (__ \ "salt").readNullable[String]
    )(User.apply _)

  implicit val tripWriter: Writes[User] = (
    (JsPath \ "id").write[Option[String]] and
      (JsPath \ "email").write[String] and
      (JsPath \ "password").write[String] and
      (JsPath \ "password_confirmation").write[String] and
      (JsPath \ "salt").write[Option[String]]
    )(unlift(User.unapply))


}
