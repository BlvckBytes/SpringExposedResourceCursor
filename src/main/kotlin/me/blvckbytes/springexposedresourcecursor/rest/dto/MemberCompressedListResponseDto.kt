package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse
import java.util.UUID

class MemberCompressedListResponseDto(
  val items: List<*>,
  val referencedMembers: Collection<*>,
  val cursor: ResponseRequestCursorDto
) {
  companion object {
    fun <DomainType: Any, ItemDtoType : Any, MemberDtoType : Any> fromListResponse(
      listResponse: ListResponse<DomainType>,
      itemDtoMapper: (item: DomainType) -> ItemDtoType,
      memberPutter: (item: DomainType, memberMap: MutableMap<UUID, MemberDtoType>) -> Unit,
    ) : MemberCompressedListResponseDto {
      val items = mutableListOf<ItemDtoType>()
      val members = mutableMapOf<UUID, MemberDtoType>()

      for (domainItem in listResponse.items) {
        items.add(itemDtoMapper(domainItem))
        memberPutter(domainItem, members)
      }

      return MemberCompressedListResponseDto(
        items, members.values,
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }
  }
}