package com.aggregate.service.domain

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.concurrent.Topic
import fs2.{Chunk, Stream}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AsyncFreeSpec
import scala.concurrent.duration.DurationInt

class CollectorSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "collector method with query (1)" - {
    "should return List(1)" in {

      val result = for {
        topic <- Stream.eval(Topic[IO, Int](1))
        list <-
          Collector.collector[IO, Int, Int](topic)(Seq(1))((value, list) =>
            list.contains(value)
          )(_ => true)
      } yield list
      result.take(1).compile.toList.asserting(_ shouldBe List(1))
    }
  }

}
