package helpers

import views.html.helper.FieldConstructor

/**
  * Created by amer.zildzic on 9/22/16.
  */
object CustomHtmlHelpers {
  implicit val myFields = FieldConstructor(views.html.helper.custom_field_constructor.f)

}
