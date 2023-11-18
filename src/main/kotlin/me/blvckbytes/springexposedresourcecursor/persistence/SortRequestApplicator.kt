package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.SortingOrder
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedPropertyException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table

class SortRequestApplicator(
  table: Table,
  private val displayName: String,
  vararg hiddenColumns: Column<*>
) {

  private val columnByName: Map<String, Column<*>>

  init {
    val columns = mutableMapOf<String, Column<*>>()

    for (column in table.columns) {
      if (hiddenColumns.contains(column))
        continue

      columns[column.name] = column
    }

    columnByName = columns
  }

  fun apply(resourceCursor: RequestResourceCursor, query: Query) {
    for (sortingRequest in (resourceCursor.sorting ?: return)) {
      query.orderBy(
        columnByName[sortingRequest.key]
          ?: throw UnsupportedPropertyException(sortingRequest.key, listOf(),  displayName),
        when (sortingRequest.value) {
          SortingOrder.ASCENDING -> SortOrder.ASC
          SortingOrder.DESCENDING -> SortOrder.DESC
        }
      )
    }
  }
}