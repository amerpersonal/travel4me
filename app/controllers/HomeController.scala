package controllers

import javax.inject._

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._

import play.api.Play.current
import play.api.i18n.Messages.Implicits._
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() extends ApplicationController {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def index = Action { implicit request =>
    val logged_in_user = helpers.SessionHelpers.loggedInUser(request)
    Ok(views.html.index(logged_in_user))
  }

}
