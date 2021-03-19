package com.aggregate.service.domain

import cats.effect.{Concurrent, Timer}
import com.aggregate.model.domain.core.{Aggregate, Query}
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Shipment,
  Track
}
import fs2.{Chunk, Stream}
import fs2.concurrent.Topic

import scala.concurrent.duration.FiniteDuration

object Collector {

  def apply[F[_]: Concurrent: Timer](
      shipmentTopic: Topic[F, Shipment],
      trackTopic: Topic[F, Track],
      pricingTopic: Topic[F, Pricing]
  )(
      query: Query
  )(
      timeout: FiniteDuration
  ): Stream[F, Aggregate] = {

    val shipmentCollector = collector[F, Shipment, String](shipmentTopic)(
      query.shipments
    )((value, list) => list.contains(value.orderId))(_.orderId != "")

    val trackCollector =
      collector[F, Track, String](trackTopic)(query.track)((value, list) =>
        list.contains(value.orderId)
      )(_.orderId != "")

    val pricingCollector = collector[F, Pricing, ISO2CountryCode](pricingTopic)(
      query.pricing
    )((value, list) => list.contains(value.iso2CountryCode))(_ => true)

    shipmentCollector
      .merge(trackCollector)
      .merge(pricingCollector)
      .interruptAfter(timeout)
      .through { in: Stream[F, Product] =>
        in.scanChunksOpt(
            (
              query.shipments.length + query.track.length + query.pricing.length,
              Aggregate(Nil, Nil, Nil)
            )
          ) {
            case (n, aggregate) =>
              if (n == 0) None
              else {
                Some { chunk: Chunk[Product] =>
                  val agg = chunk.toList.foldLeft(aggregate) {
                    case (result, product: Product) =>
                      product match {
                        case shipment: Shipment =>
                          result
                            .copy(shipments =
                              shipment :: result.shipments.toList
                            )
                        case track: Track =>
                          result.copy(track = track :: result.track.toList)
                        case pricing: Pricing =>
                          result
                            .copy(pricing = pricing :: result.pricing.toList)
                      }
                  }
                  ((n - 1, agg), Chunk.seq(Seq(agg)))
                }
              }
          }
          .lastOr(Aggregate(Nil, Nil, Nil))

      }
  }

  def collector[F[_]: Concurrent: Timer, A, B](
      topic: Topic[F, A]
  )(
      queries: Seq[B]
  )(f: (A, Seq[B]) => Boolean)(filter: A => Boolean): Stream[F, A] =
    topic
      .subscribe(100)
      .filter(filter)
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
