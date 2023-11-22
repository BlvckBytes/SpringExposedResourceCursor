package me.blvckbytes.springexposedresourcecursor.domain

class ListResponse<T : Any>(
  val items: List<T>,
  val cursor: ResponseResourceCursor
)