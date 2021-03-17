package com.aggregate.model.domain.core

import com.aggregate.model.domain.generic.ISO2CountryCode
import org.http4s.EntityEncoder

final case class Query(
    shipments: Seq[String],
    track: Seq[String],
    pricing: Seq[ISO2CountryCode]
)

object Query {
  def empty: Query = Query(Nil, Nil, Nil)
}
