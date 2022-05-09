package part4_client_my

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import part4_client_my.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success}

/**
  * Akka HTTP client: the connection-level API
  *   val connectionFlow = Http().outgoingConnection("http://my.com")
  * A Connection works as a Flow[HttpRequest,HttpResponse,_]
  *  - send one-off requests (not recommended)
  *  - work with streams of requests and responses
  *  requestSource.via(connectionFlow).to(Sink.head).run()
  *
  * HTTPS: pass in a connection context
  *   val connectionFlow = Http().outgoingConnectionHttps("http://my.com", httpsConnContext)
  *
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-09 14:26
  */
object ConnectionLevel extends App with PaymentJsonProtocol {

  implicit val system = ActorSystem("ConnectionLevel")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.baidu.com")

  def oneOffRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

  oneOffRequest(HttpRequest()).onComplete {
    case Success(response) => println(s"Got successful response: $response")
    case Failure(exception) => println(s"sending the request failed: $exception")
  }

  /*
    A small payments system
   */
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
      uri = Uri("/api/payment"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )
  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost",8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()

}
