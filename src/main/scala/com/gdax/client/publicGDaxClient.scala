package com.gdax.client

import java.time.Instant

import com.gdax.error._
import com.gdax.models.ImplicitsReads._
import com.gdax.models._
import play.api.libs.json.Reads

import scala.concurrent.Future

class PublicGDaxClient(url: String) extends GDaxClient(url) {

  def products(): Future[Either[ErrorCode, List[GDaxProduct]]] = {
    val uri = s"$url/products"
    publicRequest[List[GDaxProduct]](uri)
  }

  def ticker(productId: String): Future[Either[ErrorCode, Ticker]] = {
    val uri = s"$url/products/$productId/ticker"
    publicRequest[Ticker](uri)
  }

  def topBook(productId: String): Future[Either[ErrorCode, Book]] = {
    val uri = s"$url/products/$productId/book"
    publicRequest[Book](uri)
  }

  def fullBooks(productId: String): Future[Either[ErrorCode, FullBook]] = {
    val uri = s"$url/products/$productId/book"
    publicRequest[FullBook](uri, ("level", "3"))
  }

  def top50Books(productId: String): Future[Either[ErrorCode, Book]] = {
    val uri = s"$url/products/$productId/book"
    publicRequest[Book](uri, ("level", "2"))
  }

  def trades(productId: String, before: Option[Int] = None, after: Option[Int] = None, limit: Option[Int] = None): Future[Either[ErrorCode, List[Trades]]] = {
    val uri = s"$url/products/$productId/trades"
    val parameters = Seq(before.map(v => ("before", v.toString)), after.map(v => ("after", v.toString)), limit.map(v => ("limit", v.toString))).flatten
    publicRequest[List[Trades]](uri, parameters: _*)
  }

  def candles(productId: String, start: Instant, end: Instant, granularity: Long): Future[Either[ErrorCode, List[Candle]]] = {
    val uri = s"$url/products/$productId/candles"
    val startParam = ("start", start.toString)
    val endParam = ("end", end.toString)
    val granularityParam = ("granularity", granularity.toString)
    publicRequest[List[Candle]](uri, Seq(startParam, endParam, granularityParam): _*)
  }

  def time(): Future[Either[ErrorCode, Time]] = {
    val uri = url + "/time"
    publicRequest[Time](uri)
  }

  def currencies(): Future[Either[ErrorCode, List[Currencies]]] = {
    val uri = s"$url/currencies"
    publicRequest[List[Currencies]](uri)
  }

  def dailyStats(productId: String): Future[Either[ErrorCode, DailyStats]] = {
    val uri = s"$url/products/$productId/stats"
    publicRequest[DailyStats](uri)
  }

  private def publicRequest[A: Reads](uri: String, parameters: (String, String)*): Future[Either[ErrorCode, A]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    logger.debug(s"Sent URI: $uri")
    ws.url(uri).withQueryStringParameters(parameters: _*).get().map(parseResponse[A](_))
  }
}


object PublicGDaxClient {
  def apply(url: String): PublicGDaxClient = new PublicGDaxClient(url)
}
