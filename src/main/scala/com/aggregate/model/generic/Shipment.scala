package com.aggregate.model.generic

final case class Shipment(
    orderId: String,
    products: Seq[Product]
)
