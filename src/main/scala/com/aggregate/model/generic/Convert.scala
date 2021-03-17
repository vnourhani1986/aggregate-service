package com.aggregate.model.generic

object Convert {

  type Convert[A, B] = A => B

  def apply[A, B](a: A)(implicit
      convert: Convert[A, B]
  ): B = implicitly[Convert[A, B]](convert)(a)

}
