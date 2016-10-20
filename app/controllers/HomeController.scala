package controllers

import javax.inject._

import com.evojam.play.elastic4s.PlayElasticFactory
import com.evojam.play.elastic4s.configuration.ClusterSetup
import play.api.mvc._
import models._
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.JsNumber

import scala.concurrent.{ExecutionContext, Future}
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cs: ClusterSetup, ef: PlayElasticFactory)(implicit exec: ExecutionContext) extends ApplicationController {
  private lazy val client = ef(cs)
  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def index = Action.async { implicit request =>
    val logged_in_user = helpers.SessionHelpers.loggedInUser(request)
    val query:Map[String, Object] = Map("size" -> JsNumber(10))
    val trips: Future[List[Trip]] = Trip.browse(client, query)

    trips.map(r => Ok(views.html.index(r, logged_in_user)))
  }


}
