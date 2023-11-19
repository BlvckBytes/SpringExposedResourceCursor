package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import org.jetbrains.exposed.sql.Query

class ResourceCursorApplicator(
  private val displayName: String,
  accessibleColumns: List<UserAccessibleColumn>
) {

  private val filterApplicator: FilterRequestApplicator
  private val sortApplicator: SortRequestApplicator

  init {
    val columns = mutableMapOf<String, UserAccessibleColumn>()

    for (accessibleColumn in accessibleColumns)
      columns[accessibleColumn.key] = accessibleColumn

    filterApplicator = FilterRequestApplicator(displayName, columns)
    sortApplicator = SortRequestApplicator(displayName, columns)
  }

  fun apply(resourceCursor: RequestResourceCursor, query: Query): Query {
    filterApplicator.apply(resourceCursor, query)
    sortApplicator.apply(resourceCursor, query)

    if (resourceCursor.selectedPage < 1)
      throw IllegalStateException("The selected page variable mustn't be less than and starts at one")

    query.limit(
      resourceCursor.pageSize,
      (resourceCursor.selectedPage - 1) * resourceCursor.pageSize.toLong()
    )

    return query
  }
}