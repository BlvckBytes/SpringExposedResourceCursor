package me.blvckbytes.springexposedresourcecursor.domain

import me.blvckbytes.filterexpressionparser.parser.expression.ABinaryFilterExpression

open class RequestResourceCursor(
  val selectedPage: Int,
  val pageSize: Int,
  val sorting: LinkedHashMap<String, SortingOrder>?,
  val filtering: ABinaryFilterExpression<*, *>?,
)