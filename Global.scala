import play.api._
import play.api.cache.Cache
import models.Trip
import helpers.SessionHelper._
import play.api.mvc._
import scala.concurrent.Future


object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    val default_image_path = app.configuration.underlying.getString("general.default_img_path") + "/" +
                              app.configuration.underlying.getString("general.default_img")

    Logger.debug(default_image_path)
    Trip.set_default_image(default_image_path)

    val duration: Long = app.configuration.underlying.getLong("play.http.session.duration") * 1000
    set_duration(duration)
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

}