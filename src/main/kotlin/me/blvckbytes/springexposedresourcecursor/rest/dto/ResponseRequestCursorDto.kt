package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.ResponseResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.SortingOrder

class ResponseRequestCursorDto(
  val selectedPage: Int,
  val pageSize: Int,
  val totalItems: Long,
  val sorting: String?,
  val filtering: String?,
) {
  companion object {
    private fun stringifySorting(resourceCursor: RequestResourceCursor): String? {
      return resourceCursor.sorting?.entries?.joinToString(separator = ",") {
        when(it.value) {
          SortingOrder.ASCENDING -> "+${it.key}"
          SortingOrder.DESCENDING -> "-${it.key}"
        }
      }
    }

    fun fromRequestCursor(resourceCursor: ResponseResourceCursor): ResponseRequestCursorDto {
      return ResponseRequestCursorDto(
        resourceCursor.selectedPage,
        resourceCursor.pageSize,
        resourceCursor.totalItems,
        stringifySorting(resourceCursor),
        resourceCursor.filtering?.expressionify()
      )
    }
  }
}