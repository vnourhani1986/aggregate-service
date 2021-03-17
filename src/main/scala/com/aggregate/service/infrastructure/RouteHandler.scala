package com.aggregate.service.infrastructure

import cats.effect.Sync
import com.aggregate.model.domain.core.Query
import com.aggregate.model.domain.generic.Track
import fs2.{Chunk, Stream}
import fs2.concurrent.Topic
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.dsl.io.{BadRequest, GET, Ok, Root}
import org.http4s.{HttpRoutes, Response}

import scala.util.Try
import cats.implicits._
import cats.Applicative
import com.aggregate.model.domain.core
import com.aggregate.model.domain.core.Aggregate
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Shipment,
  Track
}
import com.aggregate.model.generic.Convert
import com.aggregate.model.generic.Convert.Convert
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl.io._
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._

object RouteHandler {

  import RouteHandler._

  def apply[F[_]: Sync](
      queryTopic: Topic[F, core.Query]
  )(
      shipmentTopic: Topic[F, Shipment],
      trackTopic: Topic[F, Track],
      pricingTopic: Topic[F, Pricing]
  ): HttpRoutes[F] = {

    implicit val aggregateViewEncoder: Encoder[AggregateView] =
      new Encoder[AggregateView] {
        final def apply(aggregateView: AggregateView): Json = {
          val shipments = aggregateView.shipments.asJson
          val track = aggregateView.track.asJson
          val pricing = aggregateView.pricing.asJson
          Json.obj(
            ("shipments", shipments),
            ("track", track),
            ("pricing", pricing)
          )
        }
      }

    implicit def aggregateViewEntityEncoder: EntityEncoder[F, AggregateView] =
      jsonEncoderOf[F, AggregateView]

    HttpRoutes.of[F] {
      case GET -> Root / "aggregation" :?
          ShipmentQueryParamMatcher(shipmentQuery) +&
          TrackQueryParamMatcher(trackQuery) +&
          PricingQueryParamMatcher(pricingQuery) =>
        validateRequest(shipmentQuery, trackQuery, pricingQuery) match {
          case Left(error) =>
            Sync[F].delay(Response(status = BadRequest).withEntity(error))
          case Right(query) =>
            for {
              _ <- queryTopic.publish1(query)
              response <- Applicative[F].map3(
                collector[F, Shipment, String](shipmentTopic)(
                  shipmentQuery.split(",")
                )((value, list) => list.contains(value.orderId)),
                collector[F, Track, String](trackTopic)(trackQuery.split(","))(
                  (value, list) => list.contains(value.orderId)
                ),
                collector[F, Pricing, ISO2CountryCode](pricingTopic)(
                  pricingQuery
                    .split(",")
                    .map(ISO2CountryCode.fromString)
                    .filter(_.isDefined)
                    .map(_.get)
                )((value, list) => list.contains(value.iso2CountryCode))
              ) {
                case (shipments, tracks, pricing) =>
                  val aggregate = Aggregate(
                    shipments = shipments,
                    track = tracks,
                    pricing = pricing
                  )
                  Response[F](status = Ok).withEntity(
                    Convert[Aggregate, AggregateView](aggregate).asJson
                  )
              }
            } yield response
        }
    }
  }

  def validateRequest(
      shipmentQuery: String,
      trackQuery: String,
      pricingQuery: String
  ): Either[String, core.Query] = {

    import cats.data._
    import cats.data.Validated._
    import cats.implicits._

    val validatedShipment: Validated[String, Array[String]] =
      Either
        .cond(
          shipmentQuery
            .split(",")
            .map(x => Try(x.toInt).toOption)
            .exists(_.isEmpty),
          shipmentQuery
            .split(","),
          "shipment order ids are not valid"
        )
        .toValidated

    val validatedTrack: Validated[String, Array[String]] =
      Either
        .cond(
          trackQuery
            .split(",")
            .map(x => Try(x.toInt).toOption)
            .exists(_.isEmpty),
          shipmentQuery
            .split(","),
          "track order ids are not valid"
        )
        .toValidated

    val validatedPricing: Validated[String, Array[ISO2CountryCode]] =
      Either
        .cond(
          pricingQuery
            .split(",")
            .map(ISO2CountryCode.fromString)
            .exists(_.isEmpty),
          pricingQuery.split(",").map(ISO2CountryCode.fromString).map(_.get),
          "iso2 country codes are not valid"
        )
        .toValidated

    (validatedShipment, validatedTrack, validatedPricing)
      .mapN(core.Query(_, _, _))
      .toEither

  }

  def collector[F[_]: Sync, A, B](
      topic: Topic[F, A]
  )(queries: Seq[B])(f: (A, Seq[B]) => Boolean): F[List[A]] =
    topic
      .subscribe(100)
      .through { in: Stream[F, A] =>
        in.scanChunksOpt(List.empty[A]) { list =>
          if (list.length == queries.length) None
          else
            Some { chunk: Chunk[A] =>
              chunk match {
                case chunk
                    if !list.contains(chunk(0)) && f(chunk(0), queries) =>
                  (chunk(0) :: list, chunk)
              }
            }
        }
      }
      .compile
      .toList

  object ShipmentQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("shipments")
  object TrackQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("track")
  object PricingQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("pricing")

  final case class AggregateView(
      shipments: Map[String, Seq[String]],
      track: Map[String, String],
      pricing: Map[String, Float]
  )

  implicit val aggregateToAggregateView: Convert[Aggregate, AggregateView] =
    aggregate =>
      AggregateView(
        shipments = aggregate.shipments
          .foldLeft(Map.empty[String, Seq[String]]) {
            case (m, shipment) =>
              m.+((shipment.orderId, shipment.products.map(_.value.toString)))
          },
        track = aggregate.track
          .foldLeft(Map.empty[String, String]) {
            case (m, track) =>
              m.+((track.orderId, track.status.value.toString))
          },
        pricing = aggregate.pricing
          .foldLeft(Map.empty[String, Float]) {
            case (m, pricing) =>
              m.+((pricing.iso2CountryCode.value.value, pricing.price))
          }
      )

}
