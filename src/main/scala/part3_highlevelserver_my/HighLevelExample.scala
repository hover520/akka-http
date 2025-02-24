package part3_highlevelserver_my

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import part2_lowlevelserver_my.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-06 21:57
  */
object HighLevelExample extends App with GuitarStoreJsonProtocol{

  implicit val system = ActorSystem("HighLevelExample")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import GuitarDB._
  /*
     GET /api/guitar  fetches ALL the guitars in the store
     GET /api/guitar?id=x fetches the guitar with id X
     GET /api/guitar/X  fetches the guitar with id x
     GET /api/guitar/inventory?inStock=true
   */
  /*
   setup
 */
  val guitarDb = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }
  implicit val timeout = Timeout(2 seconds)
  val guitarServerRoute =
    path("api" / "guitar") {
      // ALWAYS PUT THE MORE SPECIFIC ROUTE FIRST
      parameter('id.as[Int]){(guitarId:Int) =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map { guitarOption =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOption.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      } ~
      get{
        val guitarFuture: Future[List[Guitar]] = (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarFuture.map { guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
    path("api"/ "guitar" / IntNumber) { guitarId =>
      get{
        val guitarFuture: Future[Option[Guitar]] = (guitarDb ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
        val entityFuture = guitarFuture.map { guitarOption =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitarOption.toJson.prettyPrint
          )
        }
        complete(entityFuture)
      }
    } ~
    path("api" / "guitar" / "inventory") {
      get {
        parameter('inStock.as[Boolean]) { inStock =>
          val guitarFuture: Future[List[Guitar]] = (guitarDb ? FinaGuitarsInStock(inStock)).mapTo[List[Guitar]]
          val entityFuture = guitarFuture.map { guitars =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitars.toJson.prettyPrint
            )
          }
          complete(entityFuture)
        }
      }
  }

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`,payload)

  val simplifiedGuitarServerRoute =
    (pathPrefix("api" / "guitar") & get) {
      path("inventory") {
        // inventory
        parameter('inStock.as[Boolean]) { inStock =>
          complete(
            (guitarDb ? FinaGuitarsInStock(inStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      } ~
      (path(IntNumber) |  parameter('id.as[Int])) { guitarId =>
        complete(
          (guitarDb ? FindGuitar(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      } ~
      pathEndOrSingleSlash {
        complete(
          (guitarDb ? FindAllGuitars)
            .mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      }
    }

  Http().bindAndHandle(simplifiedGuitarServerRoute,"localhost",8080)
}
