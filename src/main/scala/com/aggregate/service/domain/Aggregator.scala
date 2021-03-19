package com.aggregate.service.domain

import cats.effect.{ConcurrentEffect, IO, Sync, Timer}
import com.aggregate.model.domain.core.{Aggregate, Query}
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Shipment,
  Track
}
import com.aggregate.service.infrastructure.ClientHandler
import fs2.concurrent.Topic
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

trait Aggregator[F[_]] {
  def publish(query: Query): Stream[F, Unit]
  def collect(query: Query): Stream[F, Aggregate]
}

class AggregatorImpl[F[_]: ConcurrentEffect: Timer](
    queryTopic: Topic[F, Query]
)(
    shipmentTopic: Topic[F, Shipment],
    trackTopic: Topic[F, Track],
    pricingTopic: Topic[F, Pricing]
)(
    clientHandler: ClientHandler[F]
)(
    maxBufferSize: Int,
    maxTimePeriod: Int
)(
    collectTimeout: FiniteDuration
) extends Aggregator[F] {

  override def publish(query: Query): Stream[F, Unit] =
    Stream.eval(queryTopic.publish1(query))

  override def collect(query: Query): Stream[F, Aggregate] =
    Collector[F](shipmentTopic, trackTopic, pricingTopic)(query)(collectTimeout)

  def run: Stream[F, Aggregator[F]] =
    Stream
      .eval(Sync[F].delay(this))
      .concurrently(
        Stream(
          this.shipmentProcess,
          this.trackProcess,
          this.pricingProcess
        ).parJoin(4)
      )

  private def shipmentProcess: Stream[F, Unit] =
    queryTopic
      .subscribe(100)
      .map(_.shipments)
      .flatMap(Stream.emits)
      .through(Scheduler[F, String](maxBufferSize, maxTimePeriod))
      .through(clientHandler.getShipments)
      .flatMap(chunk => Stream.emits(chunk.toList))
      .through(shipmentTopic.publish)

  private def trackProcess: Stream[F, Unit] =
    queryTopic
      .subscribe(100)
      .map(_.track)
      .flatMap(Stream.emits)
      .through(Scheduler[F, String](maxBufferSize, maxTimePeriod))
      .through(clientHandler.getTracks)
      .flatMap(chunk => Stream.emits(chunk.toList))
      .through(trackTopic.publish)

  private def pricingProcess: Stream[F, Unit] =
    queryTopic
      .subscribe(100)
      .map(_.pricing)
      .flatMap(Stream.emits)
      .through(Scheduler[F, ISO2CountryCode](maxBufferSize, maxTimePeriod))
      .through(clientHandler.getPricing)
      .flatMap(chunk => Stream.emits(chunk.toList))
      .through(pricingTopic.publish)

}

object Aggregator {

  def apply[F[_]: ConcurrentEffect: Timer](
      clientHandler: ClientHandler[F]
  )(
      maxBufferSize: Int,
      maxTimePeriod: Int
  )(
      collectTimeout: FiniteDuration
  ): Stream[F, Aggregator[F]] = {
    for {
      queryTopic <- Stream.eval(Topic[F, Query](Query.empty))
      shipmentTopic <- Stream.eval(Topic[F, Shipment](Shipment.empty))
      trackTopic <- Stream.eval(Topic[F, Track](Track.empty))
      pricingTopic <- Stream.eval(Topic[F, Pricing](Pricing.empty))
      aggregator <- Stream.eval(
        Sync[F].delay(
          new AggregatorImpl[F](queryTopic)(
            shipmentTopic,
            trackTopic,
            pricingTopic
          )(clientHandler)(maxBufferSize, maxTimePeriod)(collectTimeout)
        )
      )
      run <- aggregator.run
    } yield run

  }

}
