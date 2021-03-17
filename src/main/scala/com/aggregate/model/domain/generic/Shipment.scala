package com.aggregate.model.domain.generic

final case class Shipment(
    orderId: String,
    products: Seq[Product]
)

object Shipment {
  def empty: Shipment = Shipment("", Nil)
}
