import play.api._
import play.api.cache.Cache
import models.Trip

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application has started")

    val default_image_path = app.configuration.underlying.getString("general.default_img_path") + "/" +
                              app.configuration.underlying.getString("general.default_img")

    Logger.debug(default_image_path)
    Trip.set_default_image(default_image_path)
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

}