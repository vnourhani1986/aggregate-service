package com.aggregate.service.domain

import cats.effect.{ConcurrentEffect, Timer}
import com.aggregate.model.domain.core.Query
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Shipment,
  Track
}
import com.aggregate.service.infrastructure.ClientHandler
import fs2.concurrent.Topic
import fs2.{Chunk, Pipe, Stream}

object Dispatcher {

  def apply[F[_]: ConcurrentEffect: Timer](
      topic: Topic[F, Query]
  )(
      shipmentTopic: Topic[F, Shipment],
      trackTopic: Topic[F, Track],
      pricingTopic: Topic[F, Pricing]
  )(
      clientHandler: ClientHandler[F]
  )(
      maxBufferSize: Int,
      maxTimePeriod: Int
  ): Stream[F, Unit] = {

    def shipmentProcess: Stream[F, Unit] =
      topic
        .subscribe(100)
        .map(_.shipments)
        .flatMap(Stream.emits)
        .through(Scheduler[F, String](maxBufferSize, maxTimePeriod))
        .through(clientHandler.getShipments)
        .flatMap(chunk => Stream.emits(chunk.toList))
        .through(shipmentTopic.publish)

    def trackProcess: Stream[F, Unit] =
      topic
        .subscribe(100)
        .map(_.track)
        .flatMap(Stream.emits)
        .through(Scheduler[F, String](maxBufferSize, maxTimePeriod))
        .through(clientHandler.getTracks)
        .flatMap(chunk => Stream.emits(chunk.toList))
        .through(trackTopic.publish)

    def pricingProcess: Stream[F, Unit] =
      topic
        .subscribe(100)
        .map(_.pricing)
        .flatMap(Stream.emits)
        .through(Scheduler[F, ISO2CountryCode](maxBufferSize, maxTimePeriod))
        .through(clientHandler.getPricing)
        .flatMap(chunk => Stream.emits(chunk.toList))
        .through(pricingTopic.publish)

    Stream(shipmentProcess, trackProcess, pricingProcess).parJoin(3)
  }

}
