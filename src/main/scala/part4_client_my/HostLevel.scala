package part4_client_my

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import part4_client_my.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success, Try}

/**
  * Host-level API benefits:
  *  - the freedom from managing individual connections
  *  - best for high-volume,short-lived requests
  * Do not use the host-level API for:
  *  - one-off requests (use the request-level API)
  *  - long-lived requests (use the connection-level API)
  *
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-09 16:17
  */
object HostLevel extends App with PaymentJsonProtocol{

  implicit val system = ActorSystem("HostLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // (HttpRequest, Int) : attach data to each request
  // (Try[HttpResponse], Int) : Akka HTTP will attach to the response the same data you attached to the request
  val poolFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), Http.HostConnectionPool] = Http().cachedHostConnectionPool[Int]("www.baidu.com")

  Source(1 to 10)
    .map(i => (HttpRequest(),i))
    .via(poolFlow)
    .map{
      case (Success(response),value) =>
        // VERY IMPORTANT
        response.discardEntityBytes()
        s"Request $value has received response: $response"
      case (Failure(exception), value) =>
        s"Request $value has failed: $exception"
    }
    //.runWith(Sink.foreach(println))

  val creditCards = List(
    CreditCard("3423-3423-3423-3423","4343","tx-test-account"),
    CreditCard("1234-1234-1234-1234","1234","tx-dt-account"),
    CreditCard("3423-1234-3423-3423","4323","tx-rt-account"),
    CreditCard("3423-3423-1234-3423","123","tx-st-account")
  )

  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard,"rt-store-account", 99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payment"),
          entity = HttpEntity(
            ContentTypes.`application/json`,
            paymentRequest.toJson.prettyPrint
          )
      ),
      UUID.randomUUID().toString
      )
  )

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost",8080))
    .runForeach{  // (Try[HttpResponse], String)
      case (Success(response@HttpResponse(StatusCodes.Forbidden,_,_,_)), orderId) =>
        println(s"The order ID $orderId was not allowed to processed: $response")
      case (Success(response),orderId) =>
        println(s"The order ID $orderId was successful and returned the response: $response ")
      case (Failure(ex), orderId) =>
        println(s"The order ID $orderId could not be completed: $ex")
    }

  // high-volume , low-latency requests
}
