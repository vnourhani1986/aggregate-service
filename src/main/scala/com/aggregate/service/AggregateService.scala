package com.aggregate.service

import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import com.aggregate.model.domain.core.Query
import com.aggregate.model.domain.generic.{Pricing, Shipment, Track}
import com.aggregate.service.domain.Dispatcher
import com.aggregate.service.infrastructure.{ClientHandler, RouteHandler}
import fs2.Stream
import fs2.concurrent.Topic
import org.http4s.implicits.http4sKleisliResponseSyntax
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

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
      queryTopic <- Stream.eval(Topic[IO, Query](Query.empty))
      shipmentTopic <- Stream.eval(Topic[IO, Shipment](Shipment.empty))
      trackTopic <- Stream.eval(Topic[IO, Track](Track.empty))
      pricingTopic <- Stream.eval(Topic[IO, Pricing](Pricing.empty))
      clientHandler <- ClientHandler[IO](
        urlsConfig.shipments,
        urlsConfig.track,
        urlsConfig.pricing
      )(
        nonBlockingContext
      )
      _ <- Dispatcher[IO](queryTopic)(shipmentTopic, trackTopic, pricingTopic)(
        clientHandler
      )(schedulerConfig.maxBufferSize, schedulerConfig.maxTimePeriod)
      routeHandler <- Stream(
        RouteHandler[IO](queryTopic)(shipmentTopic, trackTopic, pricingTopic)
      )
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
