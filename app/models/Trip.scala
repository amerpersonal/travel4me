package models
import java.util.UUID

import com.sksamuel.elastic4s.{HitAs, RichSearchHit}
import play.api.libs.json._
/**
  * Created by amer.zildzic on 9/21/16.
  */

import play.api.data.Forms._
import play.api.data.Form
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError
import scala.util.matching.Regex

case class Trip(var id: Option[String] = None,
                val title: String,
                val description: String,
                val public: Boolean,
                val userId: Option[String],
                val labels: Option[List[String]],
                val image_collection: Option[List[String]] = None){
  val uuid_regex = new Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

//
  if(id == None) id = Some(UUID.randomUUID().toString)
  // not using this custom validator currently
  def validate(id: Option[String] = None, title: String, description: String, public: Boolean, labels: Option[List[String]] = None, image_collection: Option[List[String]] = None) = {
    System.out.println("validate")

    if ((id != None && uuid_regex != id) || title.trim.isEmpty || description.trim.isEmpty) None
    else Some(Trip(id, title, description, public, None, labels))
  }
}

object Trip {
  def notEmpty(implicit r:Reads[String]):Reads[String] = Reads.filterNot(ValidationError("validate.error.unexpected.value", ""))(_.trim().eq(""))

  implicit val tripReader: Reads[Trip] = {
    (
      (__ \ "id").readNullable[String] and
        (__ \ "title").read[String](notEmpty) and
        (__ \ "description").read[String](notEmpty) and
        (__ \ "type").readNullable[String].map(v => v.getOrElse("") == "public") and
        (__ \ "user_id").readNullable[String] and
        (__ \ "labels").readNullable[List[String]] and
        (__ \ "image_collection").readNullable[List[String]]
      )(Trip.apply _)
  }

  implicit val tripWriter: Writes[Trip] = (
      (JsPath \ "id").write[Option[String]] and
        (JsPath \ "title").write[String] and
        (JsPath \ "description").write[String] and
        (JsPath \ "public").write[Boolean] and
        (JsPath \ "user_id").write[Option[String]] and
        (JsPath \ "labels").write[Option[List[String]]] and
        (JsPath \ "image_collection").write[Option[List[String]]]
    )(unlift(Trip.unapply))
}







