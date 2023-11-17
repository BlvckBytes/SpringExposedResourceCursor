package me.blvckbytes.springexposedresourcecursor.domain

import me.blvckbytes.filterexpressionparser.parser.expression.ABinaryFilterExpression
import java.util.TreeMap

open class RequestResourceCursor(
  val selectedPage: Int,
  val pageSize: Int,
  val sorting: TreeMap<String, SortingOrder>?,
  val filtering: ABinaryFilterExpression<*, *>?,
)