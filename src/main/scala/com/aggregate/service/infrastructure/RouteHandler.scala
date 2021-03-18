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
import com.aggregate.service.domain.Aggregator
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl.io._
import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._

object RouteHandler {

  def apply[F[_]: Sync](
      aggregator: Aggregator[F]
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
              _ <- aggregator.publish(query)
              aggregate <- aggregator.collect(query)
            } yield Response[F](status = Ok).withEntity(
              Convert[(Query, Aggregate), AggregateView](
                (query, aggregate)
              ).asJson
            )
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

  object ShipmentQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("shipments")
  object TrackQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("track")
  object PricingQueryParamMatcher
      extends QueryParamDecoderMatcher[String]("pricing")

  final case class AggregateView(
      shipments: Map[String, Option[Seq[String]]],
      track: Map[String, Option[String]],
      pricing: Map[String, Option[Float]]
  )

  implicit
  val aggregateToAggregateView: Convert[(Query, Aggregate), AggregateView] = {
    case (query, aggregate) =>
      val collectedShipments: Map[String, Option[Seq[String]]] =
        aggregate.shipments
          .foldLeft(Map.empty[String, Option[Seq[String]]]) {
            case (m, shipment) =>
              m.+(
                (
                  shipment.orderId,
                  Some(shipment.products.map(_.value.toString))
                )
              )
          }
      val nonCollectedShipments: Map[String, Option[Seq[String]]] =
        query.shipments
          .flatMap(shipment =>
            aggregate.shipments.filter(_.orderId != shipment)
          )
          .map(shipment => (shipment.orderId, None))
          .toMap

      val collectedTracks = aggregate.track
        .foldLeft(Map.empty[String, Option[String]]) {
          case (m, track) =>
            m.+((track.orderId, Some(track.status.value.toString)))
        }

      val nonCollectedTracks: Map[String, Option[String]] =
        query.track
          .flatMap(track => aggregate.track.filter(_.orderId != track))
          .map(track => (track.orderId, None))
          .toMap

      val collectedPricing = aggregate.pricing
        .foldLeft(Map.empty[String, Option[Float]]) {
          case (m, pricing) =>
            m.+((pricing.iso2CountryCode.value.value, Some(pricing.price)))
        }

      val nonCollectedPricing: Map[String, Option[Float]] =
        query.pricing
          .flatMap(pricing =>
            aggregate.pricing
              .filter(_.iso2CountryCode.value.value != pricing.value.value)
          )
          .map(pricing => (pricing.iso2CountryCode.value.value, None))
          .toMap

      AggregateView(
        shipments = collectedShipments ++ nonCollectedShipments,
        track = collectedTracks ++ nonCollectedTracks,
        pricing = collectedPricing ++ nonCollectedPricing
      )
  }

}
