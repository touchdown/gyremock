import akka.actor.ActorSystem
import io.github.touchdown.gyremock.{GyremockServer, GyremockSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

class GyremockServerSpec extends AnyWordSpecLike with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("GyremockServerSpec")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val localhost = "127.0.0.1"
  private val timeout = 10.seconds

  override def afterAll(): Unit = {
    system.terminate().futureValue(Timeout(Span(10, org.scalatest.time.Seconds)))
  }

  "GyremockServer" should {
    "Succeed to run in fixed port" in {
      val fixedPort = 10000

      val serverBindingFuture = new GyremockServer(new GyremockSettings(localhost, fixedPort, timeout, None), services = Seq.empty).run()

      serverBindingFuture.futureValue.localAddress.getPort shouldBe fixedPort
    }

    "Succeed to run in dynamic port" in {
      def startNewGyremockServer = new GyremockServer(new GyremockSettings(localhost, 0, timeout, None), services = Seq.empty).run()

      val serverBindingFuture1 = startNewGyremockServer
      val serverBindingFuture2 = startNewGyremockServer

      val port1 = serverBindingFuture1.futureValue.localAddress.getPort
      val port2 = serverBindingFuture2.futureValue.localAddress.getPort

      port1 shouldNot be(port2)
    }
  }
}
