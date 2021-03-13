package com.aggregate.model.core

import com.aggregate.model.generic.{Pricing, Shipment, Track}

final case class Aggregate(
    pricing: Option[Pricing],
    track: Option[Track],
    shipments: Seq[Shipment]
)
