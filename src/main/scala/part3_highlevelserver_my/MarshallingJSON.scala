package part3_highlevelserver_my

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.util.Timeout
// step 1 add Spray facilities
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._


/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-08 09:57
  */
case class Player(nickName:String, characterClass: String , level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickName: String)
  case class GetPlayerByClass(characterClass: String)
  case class AddPlayer(player:Player)
  case class RemovePlayer(player:Player)
  case object OperationSuccess
}

// step 2 : define the protocol trait
trait PlayerJSONProtocol extends DefaultJsonProtocol {
  implicit val playerFormat = jsonFormat3(Player)
}

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._

  var players = Map[String,Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting all players")
      sender() ! players.values.toList

    case GetPlayer(nickName) =>
      log.info(s"Getting player with nickName $nickName")
      sender() ! players.get(nickName)

    case GetPlayerByClass(characterClass) =>
      log.info(s"Getting all players with the character class $characterClass")
      sender() ! players.values.toList.filter(_.characterClass == characterClass)

    case AddPlayer(player) =>
      log.info(s"Trying to add player $player")
      players = players + (player.nickName -> player)
      sender() ! OperationSuccess

    case RemovePlayer(player) =>
      log.info(s"Trying to remove $player")
      players = players - player.nickName
      sender() ! OperationSuccess
  }
}

object MarshallingJSON extends App
  // step3 : mix everything in
  with PlayerJSONProtocol
  // step4
  with SprayJsonSupport {

  implicit val system = ActorSystem("MarshallingJSON")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import GameAreaMap._
  val gameMap = system.actorOf(Props[GameAreaMap], "GameAreaMap")
  val playersList = List(
    Player("martin_kill_z" , "Warrior", 70),
    Player("roland_braveheart" , "Elf", 67),
    Player("daniel_rt" , "Warrior", 30)
  )
  playersList.foreach{
    player => gameMap ! AddPlayer(player)
  }

  /*
     - GET  /api/player , return all the players in the map , as JSON
     - GET  /api/player/nickname , returns the player with the given nickname (as JSON)
     - GET  /api/player?nickname=X done the same
     - GET  /api/player/class/(characterClass) return all the players with the given character class
     - POST /api/player with JSON payload , add the player to the map
     - (Exercise) DELETE /api/player with JSON payload, removes the player from the map
   */

  implicit val timeout = Timeout(2 seconds)
  val gameRoute =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment){ characterClass =>
          // 1: get all the players with characterClass
          val eventualPlayers: Future[List[Player]] = (gameMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
          // Magic 1 : send objects directly : auto-marshalled to JSON
          complete(eventualPlayers)
        } ~
        (path(Segment) | parameter('nickname)){nickname =>
          // 2: get the player with the nickname
          complete((gameMap ? GetPlayer(nickname)).mapTo[Option[Player]])
        } ~
        pathEndOrSingleSlash {
          // 3: get all the player
          complete((gameMap ? GetAllPlayers).mapTo[List[Player]])
        }
      } ~
      post {
        // 4. add a player
        // entity(as[Player]) { player =>
        // Magic 2: receive objects directly : auto-unmarshalled from JSON payloads
        entity(implicitly[FromRequestUnmarshaller[Player]]) { player =>
          complete((gameMap ? AddPlayer(player)).map(_ => StatusCodes.OK))
        }
      } ~
      delete {
        // 5.delete a player
        entity(as[Player]){player =>
          complete((gameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
        }
      }
    }

  Http().bindAndHandle(gameRoute,"localhost",8080)

}
