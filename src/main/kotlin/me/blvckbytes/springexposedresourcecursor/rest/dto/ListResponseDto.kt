package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse

class ListResponseDto<ItemDtoType>(
  val items: List<ItemDtoType>,
  val cursor: ResponseRequestCursorDto
) {
  companion object {
    fun <DomainType : Any, DtoType : Any> fromListResponse(
      listResponse: ListResponse<DomainType>,
      dtoMapper: (item: DomainType) -> DtoType
    ): ListResponseDto<DtoType> {
      return ListResponseDto(
        listResponse.items.map(dtoMapper),
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }
  }
}