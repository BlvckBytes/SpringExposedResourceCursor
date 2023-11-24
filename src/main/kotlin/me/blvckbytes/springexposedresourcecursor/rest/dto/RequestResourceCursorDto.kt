package me.blvckbytes.springexposedresourcecursor.rest.dto

class RequestResourceCursorDto(
  val selectedPage: Int?,
  val pageSize: Int?,
  val sorting: String?,
  val filtering: String?,
)