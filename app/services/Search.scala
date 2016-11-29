package services

import com.google.inject.Singleton
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{QueryDefinition, SearchDefinition, SortDefinition}
import org.elasticsearch.search.sort.SortOrder
import play.Play
import play.api.libs.json.JsNumber
import play.api.Logger
import javax.inject.Inject

import scala.concurrent.ExecutionContext

/**
  * Created by amer.zildzic on 10/18/16.
  */

@Singleton
class Search {
  implicit val exec = ExecutionContext.global

  def prepare_search(options: Map[String, Object], default_queries: List[QueryDefinition]): SearchDefinition = {
    val search_request = search in "trips/trip"
    val filters: Map[String, String] = options.get("filter") match {
      case Some(vals) => vals.asInstanceOf[Map[String, String]]
      case None => Map.empty[String, String]
    }

    Logger.debug(s"filters: $filters")
    val request_queries: List[QueryDefinition] = options.get("search") match {
      case Some(vals) => vals.asInstanceOf[Map[String, String]].toList.map(kv =>
        filters.get(kv._1) match {
          case Some(value) => List(termQuery(kv._1, value))
          case None => {
            List(prefixQuery(kv._1 + ".analyzed", kv._2), prefixQuery(kv._1 + ".ngram", kv._2), termQuery(kv._1,kv._2))

            val analyzedQueries = kv._2.split(" ").map(word => prefixQuery(kv._1 + ".analyzed", word))
            List(should(analyzedQueries), prefixQuery(kv._1 + ".ngram", kv._2), termQuery(kv._1,kv._2))
          }
        }
      ).flatten
      case _ => List()
    }

    val request_filters: List[QueryDefinition] = filters.map(kv => termQuery(kv._1, kv._2)).toList

    val queries = List(should(request_queries)) ::: default_queries ::: request_filters

    val sorts: List[SortDefinition] = options.get("sort") match {
      case Some(vals) => vals.asInstanceOf[Map[String, String]].toList.map(kv => fieldSort(kv._1).order(if(kv._2 == "desc") SortOrder.DESC else SortOrder.ASC))
      case None => List.empty
    }

    search_request.query(must(queries))
    search_request.sort(sorts: _*)

    options.get("size") match {
      case Some(JsNumber(size)) => search_request.size(size.intValue())
      case _ => {}
    }

    options.get("from") match {
      case Some(JsNumber(from)) => search_request.from(from.intValue())
      case _ => {}
    }

    val search_req = search_request.toString()
    Logger.debug(s"Executing search for $search_req")
    search_request
  }
}
