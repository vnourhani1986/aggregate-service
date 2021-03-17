package com.aggregate.model.domain.core

import com.aggregate.model.domain.generic.{Pricing, Shipment, Track}

final case class Aggregate(
    pricing: Seq[Pricing],
    track: Seq[Track],
    shipments: Seq[Shipment]
)
