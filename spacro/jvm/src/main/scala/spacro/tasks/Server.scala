package spacro.tasks

import spacro._

import java.io.InputStream
import java.security.{ SecureRandom, KeyStore }
import javax.net.ssl.{ SSLContext, TrustManagerFactory, KeyManagerFactory }

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{ RouteResult, Route, Directives }
import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext, Http }
import akka.stream.Materializer
import akka.stream.ActorMaterializer
import com.typesafe.sslconfig.akka.AkkaSSLConfig

import scala.util.{Try, Success, Failure}

import com.typesafe.scalalogging.StrictLogging

/** Takes a list of `TaskSpecification`s and hosts an HTTP server that can serve them all concurrently.
  * Requires a file `application.conf` in your resources folder with requisite fields defining the interface
  * and ports (see the file in the sample project for reference).
  * For testing, HTTP is fine, but if you want to run on MTurk, you need HTTPS to work.
  * For this, you need (also in your resources folder) a globally trusted SSL certificate,
  * a PKCS12-formatted keystore named `<domain>.p12`, a password named `<domain>-keystore-password`.
  *
  * Starts upon construction.
  */
class Server(tasks: List[TaskSpecification])(implicit config: TaskConfig) extends StrictLogging {
  implicit val system: ActorSystem = config.actorSystem
  implicit val materializer: Materializer = ActorMaterializer()
  import system.dispatcher

  val service = new Webservice(tasks)

  val httpBinding = Http().bindAndHandle(service.route, config.interface, config.httpPort)
  httpBinding.onComplete {
    case Success(binding) ⇒
      val localAddress = binding.localAddress
      logger.info(s"Server is listening on http://${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) ⇒
      logger.error(s"HTTP binding failed: $e")
  }

  val httpsServer = Try {
    // Manual HTTPS configuration

    val password: Array[Char] = new java.util.Scanner(
      getClass.getClassLoader.getResourceAsStream(s"${config.serverDomain}-keystore-password")
    ).next.toCharArray

    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream = getClass.getClassLoader.getResourceAsStream(s"${config.serverDomain}.p12")

    require(keystore != null, "Keystore required!")
    ks.load(keystore, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)

    val https = ConnectionContext.https(sslContext)

    val httpsBinding = Http().bindAndHandle(service.route, config.interface, config.httpsPort, connectionContext = https)
    httpsBinding.onComplete {
      case Success(binding) ⇒
        val localAddress = binding.localAddress
        logger.info(s"Server is listening on https://${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) ⇒
        logger.warn(s"HTTPS binding failed: $e")
    }
  }

  httpsServer match {
    case Success(_) => ()
    case Failure(e) =>
      logger.warn(s"HTTPS configuration failed: $e")
  }
}
