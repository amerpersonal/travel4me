package controllers

import javax.inject.Inject

import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}
import play.api.data.Forms._
import play.api.data.Form
import java.security.MessageDigest

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import models._
import java.util.UUID

import org.joda.time.{DateTime, DateTimeZone}
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json._

import scala.util.Try

/**
  * Created by amer.zildzic on 10/7/16.
  */
class LoginController @Inject() (cs: ClusterSetup, ef: PlayElasticFactory)(implicit exec: ExecutionContext) extends ApplicationController {
  private lazy val client = ef(cs)

  implicit object HitAs extends HitAs[Login] {
    override def as(hit: RichSearchHit): Login = {
      val source = hit.getSource

      Login(source.get("email").toString, source.get("password").toString)
    }
  }

  implicit object HitAsUser extends HitAs[User] {
    override def as(hit: RichSearchHit): User = {
      val source = hit.getSource

      User(Some(hit.id), source.get("email").toString, source.get("password").toString, source.get("password").toString).set_salt(source.get("salt").toString)
    }
  }


  val signinForm = Form(play.api.data.Forms.mapping("email" -> nonEmptyText, "password" -> nonEmptyText(8))
  (Login.apply)(Login.unapply).verifying("Failed form constraints!", login => login match {
    case userData => Login.validate(userData.email, userData.password).isDefined
  }))

  val signupForm = Form(play.api.data.Forms.mapping("id" -> optional(text), "email" -> nonEmptyText, "password" -> nonEmptyText, "password_confirmation" -> nonEmptyText)
  (User.apply)(User.unapply).verifying("Failed form constraints!", user => user match {
    case userData => User.validate(None, userData.email, userData.password, userData.password_confirmation).isDefined
  }))


  def signin = Action { implicit request =>
    if(!helpers.SessionHelpers.hasSession(request)) Ok(views.html.signin()) else Redirect("/")
  }

  def login = Action.async { implicit request =>
    signinForm.bindFromRequest().fold(
      formWithErrors => Future { Redirect("/signin").flashing("error" -> "Email must be valid and password must have 8 chars min") },
      login => {
        try {
          val p = Promise[Result]()
          client.execute {
            search in "users/user" query(bool(List(termQuery("email", login.email)), List(), List()))
          }.onComplete {
            case Success(res) => {
              val response = if (res.getHits.totalHits > 0) {
                val user = res.as[User].head

                if(BCrypt.hashpw(login.password, user.salt) == user.password) Redirect("/").withSession("current_user" -> user.serialize, "started" -> DateTime.now.getMillis.toString)
                else Redirect("/signin").flashing("error" -> "Wrong email or password")

              }
              else Redirect("/signin").flashing("error" -> "Wrong email or password")
              p.success(response)
            }
            case Failure(ex) => p.success(Redirect("/signin").flashing("error" -> "Error. Try again later"))
          }
          p.future
        }
        catch {
          case ex: Throwable => Future { Redirect("/signin").flashing("error" -> "Error. Try again later") }
        }

      }
    )
  }

  def signup = Action { implicit request =>
    if(!helpers.SessionHelpers.hasSession(request)) Ok(views.html.signup()) else Redirect("/")
  }

  def registrate = Action.async { implicit  request =>
    signupForm.bindFromRequest().fold(
      formWithErrors => Future { Redirect("/signup").flashing("error" -> "Email must be valid, password must have 8 chars min and password must match password confirmation") },
      user => {
        val hashed_password = BCrypt.hashpw(user.password, user.salt)
        try {
          val p = Promise[Result]()
          client.execute {
            search in "users/user" query(bool(List(termQuery("email", user.email)), List(), List()))
          }.onComplete {
            case Success(res) => {
              System.out.println("res users: " + res.getHits.totalHits)
              if (res.getHits.totalHits == 0) {
                client.execute {
                  index into "users/user" id UUID.randomUUID() fields(
                    "email" -> user.email,
                    "password" -> hashed_password,
                    "salt" -> user.salt,
                    "created_timestamp" -> DateTime.now.toString("yyyy-mm-dd HH:mm:ss Z")
                    )
                }.onComplete {
                  case Success(res) => {
//                    val usr_json = Json.toJson(user).toString()
                    p.success(Redirect("/").withSession("current_user" -> user.serialize, "started" -> DateTime.now.getMillis.toString))
                  }
                  case Failure(ex) => p.success(Redirect("/signup").flashing("error" -> "Error. Try again later"))

                }
              }
              else p.success(Redirect("/signup").flashing("error" -> "Account already exists"))
            }
            case Failure(ex) => p.success(Redirect("/signup").flashing("error" -> "Error. Try again later"))
          }
          p.future
        }
        catch {
          case ex: Throwable => Future { Redirect("/signup").flashing("error" -> "Error. Try again later") }
        }

      })

  }

  def logout = Action { implicit request =>
    val vs_param = request.queryString.get("p")
    vs_param match {
      case Some(vs) => {
        val vs_orig = Try(java.net.URLDecoder.decode(vs.head, java.nio.charset.Charset.forName("utf8").name())).toOption
        vs_orig match {
          case Some(verification_str) => {
            val ts = verification_str.split(":").toSet.last.toString.toLong

            val session_ts = helpers.SessionHelpers.verificationString(request, ts)
            if(session_ts == vs.head) Redirect("/").withNewSession
            else BadRequest("Access denied")

          }
          case None => BadRequest("Access denied")
        }

      }
      case None => BadRequest("Access denied")
    }
  }

}
