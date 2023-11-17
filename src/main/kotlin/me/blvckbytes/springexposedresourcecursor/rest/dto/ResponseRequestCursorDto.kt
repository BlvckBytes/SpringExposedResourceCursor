package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.SortingOrder

class ResponseRequestCursorDto(
  val selectedPage: Int,
  val pageSize: Int,
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

    fun fromRequestCursor(resourceCursor: RequestResourceCursor): ResponseRequestCursorDto {
      return ResponseRequestCursorDto(
        resourceCursor.selectedPage,
        resourceCursor.pageSize,
        stringifySorting(resourceCursor),
        resourceCursor.filtering?.expressionify()
      )
    }
  }
}