package com.aggregate.service

import cats.effect.{Blocker, ContextShift, Sync}
import com.aggregate.service.ServiceConfig.Client.Api
import com.aggregate.service.ServiceConfig.Client.Api.Urls
import com.aggregate.service.ServiceConfig.{Client, Host, Scheduler}
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax._
import pureconfig.generic.auto._

case class ServiceConfig(
    host: Host,
    client: Client,
    scheduler: Scheduler
)

object ServiceConfig {

  def load[F[_]: Sync: ContextShift](blocker: Blocker): F[ServiceConfig] =
    ConfigSource
      .file("src/main/resources/application.conf")
      .loadF[F, ServiceConfig](blocker)

  final case class Host(
      address: String,
      port: Int
  )

  final case class Client(
      api: Api
  )

  object Client {
    final case class Api(
        urls: Urls
    )

    object Api {
      final case class Urls(
          shipments: String,
          track: String,
          pricing: String
      )
    }
  }

  final case class Scheduler(
      maxBufferSize: Int,
      maxTimePeriod: Int
  )
}
