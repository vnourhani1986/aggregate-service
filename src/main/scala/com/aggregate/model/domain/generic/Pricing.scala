package com.aggregate.model.domain.generic

import com.vitorsvieira.iso.ISOCountry

final case class Pricing(
    iso2CountryCode: ISO2CountryCode,
    price: Float
)

object Pricing {
  def empty: Pricing =
    Pricing(
      ISO2CountryCode.fromString(ISOCountry.UNITED_STATES.value).get,
      0.0f
    )
}
