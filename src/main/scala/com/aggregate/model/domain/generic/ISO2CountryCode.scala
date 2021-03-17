package com.aggregate.model.domain.generic

import com.vitorsvieira.iso.ISOCountry
import com.vitorsvieira.iso.ISOCountry.ISOCountry

sealed abstract case class ISO2CountryCode private (value: ISOCountry)

object ISO2CountryCode {

  def fromString(v: String): Option[ISO2CountryCode] =
    ISOCountry.from(v).map(new ISO2CountryCode(_) {})

}
