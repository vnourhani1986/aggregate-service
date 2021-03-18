package com.aggregate.service.domain

import cats.Applicative
import cats.effect.{Concurrent, Sync, Timer}
import com.aggregate.model.domain.core.{Aggregate, Query}
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Shipment,
  Track
}
import fs2.{Chunk, Stream}
import fs2.concurrent.Topic

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Collector {

  def apply[F[_]: Concurrent: Timer](
      shipmentTopic: Topic[F, Shipment],
      trackTopic: Topic[F, Track],
      pricingTopic: Topic[F, Pricing]
  )(
      query: Query
  )(
      timeout: FiniteDuration
  ): F[Aggregate] = {
    Applicative[F].map3(
      collector[F, Shipment, String](shipmentTopic)(
        query.shipments
      )((value, list) => list.contains(value.orderId))(timeout).compile.toList,
      collector[F, Track, String](trackTopic)(query.track)((value, list) =>
        list.contains(value.orderId)
      )(timeout).compile.toList,
      collector[F, Pricing, ISO2CountryCode](pricingTopic)(
        query.pricing
      )((value, list) => list.contains(value.iso2CountryCode))(
        timeout
      ).compile.toList
    ) {
      case (shipments, tracks, pricing) =>
        Aggregate(
          shipments = shipments,
          track = tracks,
          pricing = pricing
        )

    }
  }

  def collector[F[_]: Concurrent: Timer, A, B](
      topic: Topic[F, A]
  )(
      queries: Seq[B]
  )(f: (A, Seq[B]) => Boolean)(timeout: FiniteDuration): Stream[F, A] =
    topic
      .subscribe(100)
      .interruptAfter(timeout)
      .through { in: Stream[F, A] =>
        in.scanChunksOpt(List.empty[A]) { list =>
          if (list.length == queries.length) None
          else
            Some { chunk: Chunk[A] =>
              chunk.foldLeft((list, Chunk.empty[A])) {
                case ((l, c), value)
                    if !list.contains(value) && f(value, queries) =>
                  (l ++ List(value), Chunk.seq(c.toList ++ List(value)))
                case ((l, c), _) => (l, c)

              }
            }
        }
      }
}
