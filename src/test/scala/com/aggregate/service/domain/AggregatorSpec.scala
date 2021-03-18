package com.aggregate.service.domain

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import com.aggregate.model.domain.core.{Aggregate, Query}
import com.aggregate.model.domain.generic.Product.Products
import com.aggregate.model.domain.generic.Track.Status
import com.aggregate.model.domain.generic.{
  ISO2CountryCode,
  Pricing,
  Product,
  Shipment,
  Track
}
import com.aggregate.service.infrastructure.ClientHandler
import fs2.{Chunk, Pipe, Stream}
import fs2.concurrent.Topic
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class AggregatorSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "aggregator method with query (shipments = (1), track = (1), pricing = (US))" - {
    "should return (shipments = (1, (box, envelope), track = (1, NEW), pricing = (US, 12.2f)" in {

      val fakeClientHandler: ClientHandler[IO] = new ClientHandler[IO] {
        override def getShipments: Pipe[IO, Chunk[String], Chunk[Shipment]] =
          _ =>
            Stream.emit(
              Chunk.seq(
                List(
                  Shipment(
                    "1",
                    Seq(
                      Product.fromString("box").get,
                      Product.fromString("envelope").get
                    )
                  )
                )
              )
            )

        override def getTracks: Pipe[IO, Chunk[String], Chunk[Track]] =
          _ =>
            Stream.emit(
              Chunk.seq(List(Track("1", Status.fromString("NEW").get)))
            )

        override def getPricing
            : Pipe[IO, Chunk[ISO2CountryCode], Chunk[Pricing]] =
          _ =>
            Stream.emit(
              Chunk
                .seq(List(Pricing(ISO2CountryCode.fromString("US").get, 12.2f)))
            )
      }

      val result = for {
        clientHandler <- Stream.eval(IO(fakeClientHandler))
        aggregator <- Aggregator[IO](clientHandler)(5, 5)(5.seconds)
        query = Query(
          Seq("1"),
          Seq("1"),
          Seq(ISO2CountryCode.fromString("US").get)
        )
        aggregate <- Stream.eval(aggregator.collect(query))
        _ <- Stream.eval(aggregator.publish(query))
      } yield aggregate

      result
        .take(1)
        .compile
        .toList
        .asserting(
          _ shouldBe List(
            Aggregate(
              List(Pricing(ISO2CountryCode.fromString("US").get, 12.2f)),
              List(Track("1", Status.fromString("NEW").get)),
              List(
                Shipment(
                  "1",
                  List(
                    Product.fromString("box").get,
                    Product.fromString("envelope").get
                  )
                )
              )
            )
          )
        )
    }
  }

}
