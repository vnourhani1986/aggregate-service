package com.aggregate.service.domain

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.{Chunk, Stream}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class SchedulerSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "scheduler" - {
    "return" in {
      val source =
        Stream.repeatEval(IO(1)).metered(1.seconds).interruptAfter(8.seconds) ++
          Stream
            .repeatEval(IO(1))
            .delayBy(5.seconds)
            .metered(1.seconds)
            .interruptAfter(8.seconds)
      val scheduler = Scheduler[IO, Int](5, 5)
      source
        .through(scheduler)
        .take(3)
        .compile
        .toList
        .asserting(_ shouldBe List())
    }
  }

  "My Code " - {
    "works" in {
      Stream[IO, Int](1, 2)
        .take(2)
        .compile
        .toList
        .asserting(_ shouldBe List(1))
    }
  }
}
