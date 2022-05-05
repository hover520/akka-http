package part2_lowlevelserver_my

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-04 16:07
  */
object LowLevelAPI extends App {

  implicit val system = ActorSystem("LowLevelAPI")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost",8000)
  val connectionSink = Sink.foreach[IncomingConnection]{connection =>
    println(s"Accepted incoming connection from : ${connection.remoteAddress}")
  }

//  val serverBindingFuture = serverSource.to(connectionSink).run()
//  serverBindingFuture.onComplete{
//    case Success(binding) =>
//      println("Server binding successful.")
//      binding.terminate(2 seconds)
//    case Failure(ex) => println(s"Server binding failed: $ex")
//  }

  /*
    Method 1: synchronously serve HTTP response
   */
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, uri, headers, entity, protocol) =>
      HttpResponse(
        StatusCodes.OK,  // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka Http!
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    OOPS! The resource can't be found.
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection]{connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

  // Http().bind("localhost",8080).runWith(httpSyncConnectionHandler)
  // short hand version:
  // Http().bindAndHandleSync(requestHandler,"localhost",8080)

  /*
     Method 2 : server back HTTP response ASYNCHRONOUSLY
   */
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), headers, entity, protocol) =>  // method, uri, headers, entity, protocol(http1.1/http2.0)
      Future(HttpResponse(
        StatusCodes.OK,  // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka Http!
            | </body>
            |</html>
            |""".stripMargin
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    OOPS! The resource can't be found.
            |  </body>
            |</html>
            |""".stripMargin
        )
      ))
  }
  val httpAsyncConnectionHandler = Sink.foreach[IncomingConnection]{connection =>
    connection.handleWithAsyncHandler(asyncRequestHandler)
  }

  // streams-based "manual" version
  // Http().bind("localhost",8081).runWith(httpAsyncConnectionHandler)
  // shorthand version
  // Http().bindAndHandleAsync(asyncRequestHandler,"localhost",8081)

  /*
    Method 3: async via Akka streams
   */
  val streamsBasedRequestHandler: Flow[HttpRequest ,HttpResponse ,_] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), headers, entity, protocol) =>  // method, uri, headers, entity, protocol(http1.1/http2.0)
      HttpResponse(
        StatusCodes.OK,  // HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka Http!
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    OOPS! The resource can't be found.
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  // "manual" version
//  Http().bind("localhost", 8082).runForeach{connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }

  // shorthand version
//  Http().bindAndHandle(streamsBasedRequestHandler,"localhost",8082)

  /**
    * Exercise: create your own HTTP server running on localhost on 8388, which replies
    *   - with a welcome message on the "front door" localhost:8388
    *   - with a proper HTML on localhost:8388/about
    *   - with a 404 message otherwise
    */

  val myStreamsBasedRequestHandler: Flow[HttpRequest,HttpResponse,_] = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(
        // StatusCodes.OK,  (200) is default
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            | Welcome to MyHouse!!!
            |""".stripMargin
        )
      )
    // path /search redirects to some other part of our website/webapp/microservice
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    HoverKan
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // 404
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    OOPS! The resource can't be found.
            |  </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val bindingFuture = Http().bindAndHandle(myStreamsBasedRequestHandler,"localhost",8388)

  // shutdown the server:
  bindingFuture.flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())


}
