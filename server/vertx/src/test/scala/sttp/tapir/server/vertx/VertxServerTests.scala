package sttp.tapir.server.vertx

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.implicits._
import io.vertx.lang.scala.VertxExecutionContext
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.HttpServerOptions
import io.vertx.scala.ext.web.{Route, Router}
import org.scalatest.BeforeAndAfterEach
import sttp.tapir.Endpoint
import sttp.tapir.server.{DecodeFailureHandler, ServerDefaults}
import sttp.tapir.server.tests.ServerTests
import sttp.tapir.tests.{Port, PortCounter}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class VertxServerTests extends ServerTests[Future, Any, Router => Route] with BeforeAndAfterEach {

  override def multipleValueHeaderSupport: Boolean = false // FIXME: implement
  override def multipartInlineHeaderSupport: Boolean = false // FIXME: implement
  override def streamingSupport: Boolean = false // FIXME: implement

  private var vertx: Vertx = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    vertx = Vertx.vertx()
    vertx.exceptionHandler { e =>
      e.printStackTrace()
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    vertx.close()
  }

  override def pureResult[T](t: T): Future[T] = Future.successful(t)
  override def suspendResult[T](t: => T): Future[T] = vertx.executeBlocking(() => t)

  override def route[I, E, O](e: Endpoint[I, E, O, Any], fn: I => Future[Either[E, O]], decodeFailureHandler: Option[DecodeFailureHandler]): Router => Route =
    e.asRoute(fn)(VertxServerOptions(decodeFailureHandler.getOrElse(ServerDefaults.decodeFailureHandler)))

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => Future[O])(implicit eClassTag: ClassTag[E]): Router => Route =
    e.asRouteRecoverErrors(fn)(VertxServerOptions(ServerDefaults.decodeFailureHandler))

  override def server(routes: NonEmptyList[Router => Route], port: Port): Resource[IO, Unit] = {
    val router = Router.router(vertx)
    val server = vertx.createHttpServer(HttpServerOptions().setPort(port)).requestHandler(router)
    val listenIO = IO.fromFuture(IO(server.listenFuture(port)))
    routes.toList.foreach(_.apply(router))
    Resource.make(listenIO)(s => IO(s.closeFuture())).void
  }

  override lazy val portCounter: PortCounter = new PortCounter(2000)

}
