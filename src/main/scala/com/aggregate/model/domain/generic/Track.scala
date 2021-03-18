package com.aggregate.model.domain.generic

final case class Track(
    orderId: String,
    status: Track.Status
)

object Track {

  def empty: Track =
    Track("", Status.fromString(Status.Statuses.New.toString.toLowerCase()).get)

  sealed abstract case class Status private (
      value: Status.Statuses.Status
  )

  object Status {

    def fromString(v: String): Option[Status] =
      Statuses.find(v.trim.toLowerCase()).map(new Status(_) {})

    object Statuses extends Enumeration {
      type Status = Value
      val New: Status = Value("NEW")
      val InTransit: Status = Value("IN TRANSIT")
      val Collecting: Status = Value("COLLECTING")
      val Collected: Status = Value("COLLECTEd")
      val Delivering: Status = Value("DELIVERING")
      val Delivered: Status = Value("DELIVERED")

      def find(v: String): Option[Status] =
        values.find(_.toString.toLowerCase() == v)
    }

  }

}
