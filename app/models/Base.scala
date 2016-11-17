package models

/**
  * Created by amer.zildzic on 11/17/16.
  */
abstract class Base {
  implicit val formats = org.json4s.DefaultFormats

  def serialize: String

}
