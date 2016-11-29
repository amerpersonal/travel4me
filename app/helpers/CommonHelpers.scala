package helpers

/**
  * Created by amer.zildzic on 10/31/16.
  */
object CommonHelper {

  def stripText(text: String, max_chars: Int = 100) = {
    if(text.length <= max_chars) text
    else text.dropRight(text.length - max_chars).concat("...")
  }

}
