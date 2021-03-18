package com.aggregate.service.domain

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, ConcurrentEffect, Timer}
import fs2.{Chunk, Pipe, Stream}

import scala.concurrent.duration.DurationInt

object Scheduler {

  def apply[F[_]: Timer: ConcurrentEffect, A](
      chunkMaxSize: Int,
      timeMaxPeriod: Long
  ): Pipe[F, A, Chunk[A]] =
    in => {

      def timeTagger(): Pipe[F, Option[A], (Option[A], Long)] =
        in =>
          for {
            value <- in
            clock <- Stream.eval(Clock[F].realTime(TimeUnit.SECONDS))
          } yield (value, clock)

      val trigger: Stream[F, Option[A]] = Stream
        .awakeEvery[F](1.seconds)
        .map(_ => None)

      in.map(Option(_))
        .merge(trigger)
        .through(timeTagger())
        .scanChunksOpt((0, 0L)) {
          case (n, time) =>
            Some { chunk: Chunk[(Option[A], Long)] =>
              chunk.toList match {
                case (value, timeTag) :: Nil
                    if value.isEmpty && timeTag - time < timeMaxPeriod - 1 =>
                  ((n, time), chunk.map(_._1).drop(1))
                case (value, timeTag) :: Nil
                    if value.isEmpty && timeTag - time >= timeMaxPeriod - 1 =>
                  ((0, timeTag), chunk.map(_ => None))
                case (value, timeTag) :: tail if n < chunkMaxSize - 1 =>
                  ((n + 1, timeTag), chunk.map(_._1))
                case (value, timeTag) :: tail if n >= chunkMaxSize - 1 =>
                  (
                    (0, timeTag),
                    Chunk.seq(chunk.toList.map(_._1) ++ List(None))
                  )
              }
            }
        }
        .split(_.isEmpty)
        .map(_.map(_.get))
        .map { x => println(x, "end"); x }
        .filter(_.nonEmpty)
    }

}
