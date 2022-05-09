package part4_client_my

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import part4_client_my.PaymentSystemDomain.PaymentRequest
import spray.json._
import scala.util.{Failure, Success}

/**
  * Akka HTTP client: request-level API
  *
  * Best for
  *  - low-volume,low-latency requests
  * Do not use the request-level API for:
  *  - high volume(use the host-level API)
  *  - long-lived requests (use the connection-level API)
  *
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-09 17:23
  */
object RequestLevel extends App with PaymentJsonProtocol{

  implicit val system = ActorSystem("RequestLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.baidu.com"))

  responseFuture.onComplete{
    case Success(response) =>
      // VERY IMPORTANT
      response.discardEntityBytes()
      println(s"The request was successful and returned: $response")
    case Failure(exception) =>
      println(s"The request failed with: $exception")
  }

  val creditCards = List(
    CreditCard("3423-3423-3423-3423","4343","tx-test-account"),
    CreditCard("1234-1234-1234-1234","1234","tx-dt-account"),
    CreditCard("3423-1234-3423-3423","4323","tx-rt-account"),
    CreditCard("3423-3423-1234-3423","123","tx-st-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard,"rt-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
      HttpRequest(
        HttpMethods.POST,
        uri = "http://localhost:8080/api/payment",
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      )
  )
  Source(serverHttpRequests)
    .mapAsyncUnordered(10)(request => Http().singleRequest(request))
    .runForeach(println)

}
