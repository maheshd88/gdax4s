package com.gdax.client

import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.gdax.models.ImplicitsReads._
import com.gdax.error.ErrorCode
import com.gdax.models._
import com.gdax.models.OrderParams.CancelAfter.CancelAfter
import com.gdax.models.OrderParams.{OrderType, TimeInForce}
import com.gdax.models.OrderParams.OrderType.OrderType
import com.gdax.models.OrderParams.Side.Side
import com.gdax.models.OrderParams.TimeInForce.TimeInForce
import com.gdax.models.{AccountWithProfile, Book, FullBook, Ticker}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import play.api.libs.ws.{EmptyBody, InMemoryBody, StandaloneWSRequest}
import play.shaded.ahc.org.asynchttpclient.util.Base64
import com.gdax.models.ImplicitWrites._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthenticatedGDaxClient(url: String) extends PublicGDaxClient(url) {

  def account(accountId: String): Future[Either[ErrorCode, AccountWithProfile]] = {
    val uri = s"$url/accounts/$accountId"
    authorizedGet[AccountWithProfile](uri)
  }

  def accounts(): Future[Either[ErrorCode, List[Account]]] = {
    val uri = s"$url/accounts"
    authorizedGet[List[Account]](uri)
  }

  def paymentMethods(): Future[Either[ErrorCode, List[PaymentMethod]]] = {
    val uri = s"$url/payment-methods"
    authorizedGet[List[PaymentMethod]](uri)
  }

  def coinbaseAccounts(): Future[Either[ErrorCode, List[CoinBaseAccount]]] = {
    val uri = s"$url/coinbase-accounts"
    authorizedGet[List[CoinBaseAccount]](uri)
  }

  def depositFromPaymentMethod(amount: Double, currency: String, paymentMethodId: String): Future[Either[ErrorCode, PaymentMethodDeposit]] = {
    import play.api.libs.ws.JsonBodyWritables._
    val uri = s"$url/deposits/payment-method"
    val postParam: JsValue = Json.toJson(DepositFromPaymentMethodPost(amount, currency, paymentMethodId))
    val requestWithHeaders = addAuthorizationHeaders(ws.url(uri).withBody(postParam))
    requestWithHeaders.post(postParam).map(parseResponse[PaymentMethodDeposit](_))
  }

  def depositFromCoinbaseAccount(amount: Double, currency: String, coinbaseAccountId: String): Future[Either[ErrorCode, CoinBaseDeposit]]  = {
    val uri = s"$url/deposits/coinbase-account"
    val params = Seq(("amount", amount.toString), ("currency", currency.toString), ("coinbase_account_id", coinbaseAccountId))
    authorizedPost[CoinBaseDeposit](uri, params:_*)
  }

  /*
  TODO
  def limitOrder(productId: String, side: Side, price: Double, size: Double, timeInForce: Option[TimeInForce] = None,
                 cancelAfter: Option[CancelAfter] = None,  stp: Option[Boolean] = None,
                 postOnly: Option[Boolean] = None, clientId: Option[String] = None) = {

    if(cancelAfter.isDefined && !timeInForce.contains(TimeInForce.GTT)) throw new Exception("cancel_after [optional]* min, hour, day * Requires time_in_force to be GTT")
    if(postOnly.isDefined && (timeInForce.contains(TimeInForce.IOC) || timeInForce.contains(TimeInForce.FOK))) throw new Exception("post_only [optional]** Post only flag ** Invalid when time_in_force is IOC or FOK")

    val orderType = OrderType.Limit
  }

  def marketOrder(orderType: OrderType, productId: String, side: Side, stp: Option[Boolean] = None, clientId: Option[String] = None, size: Option[Double] = None, funds: Option[Double] = None) = {
    if(size.isEmpty && funds.isEmpty) throw new Exception("* One of size or funds is required.")
  }

  def order(orderType: OrderType, productId: String, side: Side, stp: Option[Boolean] = None, clientId: Option[String] = None) = {

  }
*/
  private def authorizedPost[A: Reads](uri: String, parameters: (String, String)*): Future[Either[ErrorCode, A]] = {
    import play.api.libs.ws.JsonBodyWritables._
    logger.debug(s"Sent URI: $uri")
    val requestBody = Json.obj(parameters.map(t => (t._1, Json.toJsFieldJsValueWrapper(t._2))):_*)
    val jsonString = requestBody.toString()
    val requestWithHeaders = addAuthorizationHeaders(ws.url(uri).withBody(requestBody))
    requestWithHeaders.post(requestBody).map(parseResponse[A](_))
  }

  private def authorizedGet[A: Reads](uri: String, parameters: (String, String)*): Future[Either[ErrorCode, A]] = {
    logger.debug(s"Sent URI: $uri")
    val requestWithHeaders = addAuthorizationHeaders(ws.url(uri).withQueryStringParameters(parameters: _*))
    requestWithHeaders.get().map(parseResponse[A](_))
  }

  private def addAuthorizationHeaders(request: StandaloneWSRequest): StandaloneWSRequest = {
    val apiKey: String = System.getProperty("apiKey")
    val secretKey: String = System.getProperty("secretKey")
    val passphrase: String = System.getProperty("passphrase")
    addAuthorizationHeaders(apiKey, secretKey, passphrase, request)
  }

  private def addAuthorizationHeaders(apiKey: String, secretKey: String, passphrase: String, request: StandaloneWSRequest): StandaloneWSRequest = {
    val CryptoFunction = "HmacSHA256"
    val timestamp: Long = Instant.now().getEpochSecond

    val body = request.body match {
      case EmptyBody => ""
      case body: InMemoryBody => body.bytes.utf8String
    }

    val path = request.uri.getPath
    val message: String = timestamp + request.method.toUpperCase + path + body
    val hmacKey: Array[Byte] = Base64.decode(secretKey)
    val secretKeySpec = new SecretKeySpec(hmacKey, CryptoFunction)
    val mac: Mac = Mac.getInstance(CryptoFunction)
    mac.init(secretKeySpec)
    val signature: String = Base64.encode(mac.doFinal(message.getBytes))

    val headers = Seq(("CB-ACCESS-SIGN", signature),
      ("CB-ACCESS-TIMESTAMP", timestamp.toString),
      ("CB-ACCESS-KEY", apiKey),
      ("CB-ACCESS-PASSPHRASE", passphrase),
      ("Content-Type", "application/json; charset=utf-8"))

    request.withHttpHeaders(headers: _*)
  }
}

object AuthenticatedGDaxClient {
  def apply(url: String): AuthenticatedGDaxClient = new AuthenticatedGDaxClient(url)
}