package me.blvckbytes.springexposedresourcecursor.domain.exception

open class InvalidUUIDException(
  val value: String,
  val columnName: String
) : RuntimeException()