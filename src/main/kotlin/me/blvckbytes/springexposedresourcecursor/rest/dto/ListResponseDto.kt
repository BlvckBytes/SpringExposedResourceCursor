package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse

class ListResponseDto(
  val items: List<*>,
  val cursor: ResponseRequestCursorDto
) {
  companion object {
    fun <DomainType : Any, DtoType : Any> fromListResponse(listResponse: ListResponse<DomainType>, dtoMapper: (item: DomainType) -> DtoType): ListResponseDto {
      return ListResponseDto(
        listResponse.items.map(dtoMapper),
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }
  }
}