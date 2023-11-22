package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.ResponseResourceCursor
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow

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

  fun <DomainModel> applyAndMakeResponse(
    resourceCursor: RequestResourceCursor,
    query: Query,
    mapper: (resultRow: ResultRow) -> DomainModel
  ): ListResponse<DomainModel> where DomainModel : Any {
    filterApplicator.apply(resourceCursor, query)
    sortApplicator.apply(resourceCursor, query)

    if (resourceCursor.selectedPage < 1)
      throw IllegalStateException("The selected page variable mustn't be less than and starts at one")

    val totalItems = query.count()

    val items = query
      .limit(resourceCursor.pageSize, (resourceCursor.selectedPage - 1) * resourceCursor.pageSize.toLong())
      .map(mapper)

    return ListResponse(items, ResponseResourceCursor.fromRequestCursor(resourceCursor, totalItems))
  }
}