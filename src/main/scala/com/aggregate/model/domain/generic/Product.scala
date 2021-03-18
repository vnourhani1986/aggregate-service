package com.aggregate.model.domain.generic

sealed abstract case class Product private (value: Product.Products.Product)

object Product {

  def fromString(v: String): Option[Product] =
    Products.find(v.trim.toLowerCase()).map(new Product(_) {})

  object Products extends Enumeration {
    type Product = Value
    val Envelope: Product = Value("envelope")
    val Box: Product = Value("box")
    val pallet: Product = Value("pallet")

    def find(v: String): Option[Product] = values.find(_.toString == v)
  }

}
