package part2_lowlevelserver_my

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import part2_lowlevelserver_my.LowLevelHttps.getClass


/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-05 21:50
  */

object HttpsContexts{
  // Step 1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  // alternatives : new FileInputStream(new File("src/main/resources/keystore.pkcs12"))
  val password = "akka-https".toCharArray  // fetch the password from a secure place
  ks.load(keyStoreFile,password)

  // Step 2: initialize a key manager
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")  // PKI = public key infrastructure
  keyManagerFactory.init(ks,password)

  // Step 3: initialize a trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // Step 4: initialize an SSL context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  // Step 5: return the https connection context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)
}

object LowLevelHttps extends App {

  implicit val system = ActorSystem("LowLevelHttps")
  implicit val materializer = ActorMaterializer()



  val requestHandler : HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET,_,_,_,_) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |   <body>
            |     Hello Akka HTTP !
            |   </body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |   <body>
            |     NOT FOUND
            |   </body>
            |</html>
            |""".stripMargin
        )
      )
  }

  val httpsBinding = Http().bindAndHandleSync(requestHandler,"localhost",8443,HttpsContexts.httpsConnectionContext)

}
