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
import scala.util.Try
import play.api.Logger
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.i18n.Messages

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cs: ClusterSetup, ef: PlayElasticFactory)(implicit exec: ExecutionContext) extends ApplicationController(ef: PlayElasticFactory, cs: ClusterSetup) {

  def index(label: String) = Action.async { implicit request =>
    var query:Map[String, Object] = Map("size" -> JsNumber(50), "sort" -> Map("updated_timestamp" -> "desc"))
    val label_name = Trip.labels.get(label)

    if(label_name != None) {
      Logger.debug(s"yeah $label_name")
      query += "filter" -> Map("labels" -> label_name.get)
      Logger.debug(query.toString())
    }

    Try(Trip.browse(client, query)).toOption match {
      case Some(trips) => trips.map(t => Ok(views.html.index(t)))
      case None => Future { Ok(views.html.index(List())) }
    }


  }


}
