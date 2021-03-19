package com.aggregate.service.infrastructure

import cats.effect.{Concurrent, Sync}
import com.aggregate.model.domain.core.Query
import org.http4s.dsl.io.{BadRequest, GET, Ok, Root}
import org.http4s.{HttpRoutes, Response}

import scala.util.Try
import com.aggregate.model.domain.core
import com.aggregate.model.domain.core.Aggregate
import com.aggregate.model.domain.generic.ISO2CountryCode
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

  def apply[F[_]: Concurrent](
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
      case request @ GET -> Root / "aggregation" =>
        validateRequest(request) match {
          case Left(error) =>
            Sync[F].delay(Response(status = BadRequest).withEntity(error))
          case Right(query) =>
            aggregator
              .collect(query)
              .concurrently(aggregator.publish(query))
              .map(aggregate =>
                Response[F](status = Ok).withEntity(
                  Convert[(Query, Aggregate), AggregateView](
                    (query, aggregate)
                  ).asJson
                )
              )
              .compile
              .lastOrError

        }
    }
  }

  def validateRequest[F[_]: Sync](
      request: Request[F]
  ): Either[String, core.Query] = {

    import cats.data._
    import cats.data.Validated._
    import cats.implicits._

    val shipmentQuery = request.params
      .get("shipments")
      .toSeq
      .flatMap(_.split(","))
      .distinct
      .toArray
    val trackQuery =
      request.params.get("track").toSeq.flatMap(_.split(",")).distinct.toArray
    val pricingQuery =
      request.params.get("pricing").toSeq.flatMap(_.split(",")).distinct.toArray

    val validatedShipment: Validated[String, Array[String]] =
      Either
        .cond(
          !shipmentQuery
            .map(x => Try(x.toInt).toOption)
            .exists(_.isEmpty),
          shipmentQuery,
          "shipment order ids are not valid, "
        )
        .toValidated

    val validatedTrack: Validated[String, Array[String]] =
      Either
        .cond(
          !trackQuery
            .map(x => Try(x.toInt).toOption)
            .exists(_.isEmpty),
          trackQuery,
          "track order ids are not valid, "
        )
        .toValidated

    val validatedPricing: Validated[String, Array[ISO2CountryCode]] =
      Either
        .cond(
          !pricingQuery
            .map(ISO2CountryCode.fromString)
            .exists(_.isEmpty),
          pricingQuery.map(ISO2CountryCode.fromString).map(_.get),
          "iso2 country codes are not valid, "
        )
        .toValidated

    (validatedShipment, validatedTrack, validatedPricing)
      .mapN(core.Query(_, _, _))
      .toEither

  }

  final case class AggregateView(
      shipments: Map[String, Option[Seq[String]]],
      track: Map[String, Option[String]],
      pricing: Map[String, Option[Float]]
  )

  implicit
  val aggregateToAggregateView: Convert[(Query, Aggregate), AggregateView] = {
    case (query, aggregate) =>
      val shipments: Map[String, Option[Seq[String]]] =
        query.shipments.foldLeft(Map.empty[String, Option[Seq[String]]]) {
          case (table, shipmentQuery) =>
            aggregate.shipments.find(_.orderId == shipmentQuery) match {
              case None => table.+(shipmentQuery -> None)
              case Some(shipment) =>
                table.+(
                  shipment.orderId -> Some(
                    shipment.products.map(_.value.toString)
                  )
                )
            }
        }

      val tracks: Map[String, Option[String]] =
        query.track.foldLeft(Map.empty[String, Option[String]]) {
          case (table, trackQuery) =>
            aggregate.track.find(_.orderId == trackQuery) match {
              case None => table.+(trackQuery -> None)
              case Some(track) =>
                table.+(track.orderId -> Some(track.status.value.toString))
            }
        }

      val pricing: Map[String, Option[Float]] =
        query.pricing.foldLeft(Map.empty[String, Option[Float]]) {
          case (table, pricingQuery) =>
            aggregate.pricing.find(
              _.iso2CountryCode.value == pricingQuery.value
            ) match {
              case None => table.+(pricingQuery.value.value -> None)
              case Some(pricing) =>
                table.+(pricingQuery.value.value -> Some(pricing.price))
            }
        }

      AggregateView(
        shipments = shipments,
        track = tracks,
        pricing = pricing
      )
  }

}
