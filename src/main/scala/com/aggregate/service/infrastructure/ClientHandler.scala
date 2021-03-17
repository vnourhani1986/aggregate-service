package com.aggregate.service.infrastructure

import cats.effect.ConcurrentEffect
import fs2.{Chunk, Pipe, Stream}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import cats.syntax.functor._
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Product,
  Shipment,
  Track
}
import com.aggregate.model.generic.Convert
import com.aggregate.model.generic.Convert.Convert
import com.aggregate.service.ServiceConfig.Client.Api.Urls

import scala.concurrent.ExecutionContext

trait ClientHandler[F[_]] {
  def getShipments: Pipe[F, Chunk[String], Chunk[Shipment]]
  def getTracks: Pipe[F, Chunk[String], Chunk[Track]]
  def getPricing: Pipe[F, Chunk[ISO2CountryCode], Chunk[Pricing]]
}

class ClientHandlerImpl[F[_]: ConcurrentEffect](
    client: Client[F]
)(
    shipmentUrl: String,
    trackUrl: String,
    pricingUrl: String
) extends ClientHandler[F] {

  import ClientHandler._

  override def getShipments: Pipe[F, Chunk[String], Chunk[Shipment]] =
    in => {
      implicit val mapDecoderEntity
          : EntityDecoder[F, Map[String, Seq[String]]] =
        jsonOf[F, Map[String, Seq[String]]]

      for {
        queries <- in
        responses <- Stream.eval(
          client.get[Chunk[Shipment]](
            s"$shipmentUrl?q=${queries.toList.mkString(",")}"
          )(response =>
            response
              .as[Map[String, Seq[String]]]
              .map(m =>
                Chunk.array(m.map {
                  case (orderId, products) =>
                    Convert[ShipmentView, Shipment](
                      ShipmentView(orderId, products)
                    )
                }.toArray)
              )
          )
        )
      } yield responses
    }

  override def getTracks: Pipe[F, Chunk[String], Chunk[Track]] =
    in => {
      implicit val mapDecoderEntity: EntityDecoder[F, Map[String, String]] =
        jsonOf[F, Map[String, String]]

      for {
        queries <- in
        responses <- Stream.eval(
          client.get[Chunk[Track]](
            s"$trackUrl?q=${queries.toList.mkString(",")}"
          )(response =>
            response
              .as[Map[String, String]]
              .map(m =>
                Chunk.array(m.map {
                  case (orderId, status) =>
                    Convert[TrackView, Track](
                      TrackView(orderId, status)
                    )
                }.toArray)
              )
          )
        )
      } yield responses
    }

  override def getPricing: Pipe[F, Chunk[ISO2CountryCode], Chunk[Pricing]] =
    in => {
      implicit val mapDecoderEntity: EntityDecoder[F, Map[String, Float]] =
        jsonOf[F, Map[String, Float]]

      for {
        queries <- in
        responses <- Stream.eval(
          client.get[Chunk[Pricing]](
            s"$pricingUrl?q=${queries.toList.map(_.value.value).mkString(",")}"
          )(response =>
            response
              .as[Map[String, Float]]
              .map(m =>
                Chunk.array(m.map {
                  case (orderId, price) =>
                    Convert[PricingView, Pricing](
                      PricingView(orderId, price)
                    )
                }.toArray)
              )
          )
        )
      } yield responses
    }
}

object ClientHandler {
  def apply[F[_]: ConcurrentEffect](
      shipmentUrl: String,
      trackUrl: String,
      pricingUrl: String
  )(
      executionContext: ExecutionContext
  ): Stream[F, ClientHandler[F]] =
    for {
      client <- Stream.resource(
        BlazeClientBuilder[F](executionContext).resource
      )
    } yield new ClientHandlerImpl[F](client)(shipmentUrl, trackUrl, pricingUrl)

  final case class ShipmentView(
      orderId: String,
      products: Seq[String]
  )

  final case class TrackView(
      orderId: String,
      status: String
  )

  final case class PricingView(
      iso2CountryCode: String,
      price: Float
  )

  implicit val shipmentViewToShipment: Convert[ShipmentView, Shipment] =
    shipmentView =>
      Shipment(
        orderId = shipmentView.orderId,
        products = shipmentView.products
          .map(Product.fromString)
          .filter(_.isDefined)
          .map(_.get)
      )

  implicit val trackViewToTrack: Convert[TrackView, Track] = trackView =>
    Track(
      orderId = trackView.orderId,
      status = Track.Status.fromString(trackView.status).get
    )

  implicit val pricingViewToPricing: Convert[PricingView, Pricing] =
    pricingView =>
      Pricing(
        iso2CountryCode =
          ISO2CountryCode.fromString(pricingView.iso2CountryCode).get,
        price = pricingView.price
      )

}
