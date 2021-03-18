package com.aggregate.service

import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import com.aggregate.service.domain.Aggregator
import com.aggregate.service.infrastructure.{ClientHandler, RouteHandler}
import fs2.Stream
import org.http4s.implicits.http4sKleisliResponseSyntax
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object AggregateService extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    (for {
      nonBlockingPool <- Stream.eval(IO(Executors.newFixedThreadPool(4)))
      nonBlockingContext <-
        Stream.eval(IO(ExecutionContext.fromExecutor(nonBlockingPool)))
      blockingPool <- Stream.eval(IO(Executors.newFixedThreadPool(4)))
      blockingContext <-
        Stream.eval(IO(Blocker.liftExecutorService(blockingPool)))
      loadedServiceConfig <-
        Stream.eval(ServiceConfig.load[IO](blockingContext))
      urlsConfig <- Stream(loadedServiceConfig.client.api.urls)
      schedulerConfig <- Stream(loadedServiceConfig.scheduler)
      clientHandler <- ClientHandler[IO](
        urlsConfig.shipments,
        urlsConfig.track,
        urlsConfig.pricing
      )(
        loadedServiceConfig.timeout.client.seconds
      )(
        nonBlockingContext
      )
      aggregator <- Aggregator[IO](clientHandler)(
        schedulerConfig.maxBufferSize,
        schedulerConfig.maxTimePeriod
      )(loadedServiceConfig.timeout.collect.seconds)
      routeHandler <- Stream.eval(IO(RouteHandler[IO](aggregator)))
      server <-
        BlazeServerBuilder[IO](implicitly[ExecutionContext](nonBlockingContext))
          .bindHttp(
            loadedServiceConfig.host.port,
            host = loadedServiceConfig.host.address
          )
          .withHttpApp(routeHandler.orNotFound)
          .serve
    } yield server).compile.lastOrError

  }

}
