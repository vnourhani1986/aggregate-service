package com.aggregate.service.domain

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.{Chunk, Stream}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class SchedulerSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "scheduler with inputs by distances lower than max period(5)" - {
    "should return chunk of max size(5)" in {
      val source = Stream.repeatEval(IO(1)).metered(4.seconds)
      val scheduler = Scheduler[IO, Int](5, 5)
      source
        .through(scheduler)
        .take(1)
        .compile
        .toList
        .asserting(_ shouldBe List(Chunk.array(Array(1, 1, 1, 1, 1))))
    }
  }

  "scheduler with 2 inputs by distances lower than max period(5) at first and no more input" - {
    "should return chunk of max size(2)" in {
      val source =
        Stream.repeatEval(IO(1)).metered(2.seconds).interruptAfter(5.seconds)
      val scheduler = Scheduler[IO, Int](5, 5)
      source
        .through(scheduler)
        .take(1)
        .compile
        .toList
        .asserting(_ shouldBe List(Chunk.array(Array(1, 1))))
    }
  }

}
