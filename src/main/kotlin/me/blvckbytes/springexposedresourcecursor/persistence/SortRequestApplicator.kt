package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.SortingOrder
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedPropertyException
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder

class SortRequestApplicator(
  private val displayName: String,
  private val accessibleColumnByName: Map<String, UserAccessibleColumn>
) {

  fun apply(resourceCursor: RequestResourceCursor, query: Query) {
    for (sortingRequest in (resourceCursor.sorting ?: return)) {
      query.orderBy(
        accessibleColumnByName[sortingRequest.key]?.column
          ?: throw UnsupportedPropertyException(sortingRequest.key, accessibleColumnByName.values,  displayName),
        when (sortingRequest.value) {
          SortingOrder.ASCENDING -> SortOrder.ASC
          SortingOrder.DESCENDING -> SortOrder.DESC
        }
      )
    }
  }
}