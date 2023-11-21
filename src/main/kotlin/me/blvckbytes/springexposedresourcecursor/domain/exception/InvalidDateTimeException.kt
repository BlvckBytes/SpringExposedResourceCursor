package me.blvckbytes.springexposedresourcecursor.domain.exception

open class InvalidDateTimeException(
  val value: String,
  val columnName: String
) : RuntimeException()