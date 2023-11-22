package me.blvckbytes.springexposedresourcecursor.domain

import me.blvckbytes.filterexpressionparser.parser.expression.ABinaryFilterExpression
import java.util.TreeMap

open class ResponseResourceCursor(
  selectedPage: Int,
  pageSize: Int,
  sorting: TreeMap<String, SortingOrder>?,
  filtering: ABinaryFilterExpression<*, *>?,
  val totalItems: Long,
) : RequestResourceCursor(selectedPage, pageSize, sorting, filtering) {
  companion object {
    fun fromRequestCursor(requestCursor: RequestResourceCursor, totalItems: Long): ResponseResourceCursor {
      return ResponseResourceCursor(
        requestCursor.selectedPage,
        requestCursor.pageSize,
        requestCursor.sorting,
        requestCursor.filtering,
        totalItems
      )
    }
  }
}