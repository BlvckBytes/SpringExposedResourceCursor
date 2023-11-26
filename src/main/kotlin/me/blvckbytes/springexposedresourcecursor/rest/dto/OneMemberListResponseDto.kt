package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse
import java.util.*

class OneMemberListResponseDto<ItemDtoType, MemberDtoType>(
  val items: List<ItemDtoType>,
  val members: Collection<MemberDtoType>,
  val cursor: ResponseRequestCursorDto,
) {
  companion object {

    fun <DomainType : Any, ItemDtoType : Any, MemberDtoType: Any> fromListResponse(
      listResponse: ListResponse<DomainType>,
      dtoMapper: (item: DomainType) -> ItemDtoType,
      memberPutter: (
        item: DomainType,
        memberMap: MutableMap<UUID, MemberDtoType>
      ) -> Unit
    ): OneMemberListResponseDto<ItemDtoType, MemberDtoType> {
      val members = mutableMapOf<UUID, MemberDtoType>()

      for (domainItem in listResponse.items)
        memberPutter(domainItem, members)

      return OneMemberListResponseDto(
        listResponse.items.map(dtoMapper),
        members.values,
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }
  }
}