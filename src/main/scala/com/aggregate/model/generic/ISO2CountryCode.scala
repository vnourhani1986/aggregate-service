package com.aggregate.model.generic

import com.vitorsvieira.iso.ISOCountry
import com.vitorsvieira.iso.ISOCountry.ISOCountry

sealed abstract case class ISO2CountryCode private (value: ISOCountry)

object ISO2CountryCode {

  def fromString(v: String): Option[ISOCountry] =
    ISOCountry.from(v)

}
