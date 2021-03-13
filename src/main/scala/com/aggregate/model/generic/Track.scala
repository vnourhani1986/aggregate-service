package com.aggregate.model.generic

final case class Track(
    orderId: String,
    status: Track.Status
)

object Track {

  sealed abstract case class Status private (
      value: Status.Statuses.Status
  )

  object Status {

    def fromString(v: String): Option[Status] =
      Statuses.find(v.trim.toLowerCase()).map(new Status(_) {})

    object Statuses extends Enumeration {
      type Status = Value
      val New, Collecting = Value

      def find(v: String): Option[Status] = values.find(_.toString == v)
    }

  }

}
