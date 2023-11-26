package me.blvckbytes.springexposedresourcecursor.rest.dto

import me.blvckbytes.springexposedresourcecursor.domain.ListResponse
import java.util.*

class TwoMemberListResponseDto<ItemDtoType : Any, FirstMemberDtoType : Any, SecondMemberDtoType : Any>(
  val items: List<ItemDtoType>,
  val membersA: Collection<FirstMemberDtoType>,
  val membersB: Collection<SecondMemberDtoType>,
  val cursor: ResponseRequestCursorDto,
) {
  companion object {

    fun <DomainType : Any, ItemDtoType : Any, FirstMemberDtoType: Any, SecondMemberDtoType : Any> fromListResponse(
      listResponse: ListResponse<DomainType>,
      dtoMapper: (item: DomainType) -> ItemDtoType,
      memberPutter: (
        item: DomainType,
        memberAMap: MutableMap<UUID, FirstMemberDtoType>,
        memberBMap: MutableMap<UUID, SecondMemberDtoType>,
      ) -> Unit,
    ): TwoMemberListResponseDto<ItemDtoType, FirstMemberDtoType, SecondMemberDtoType> {
      val membersA = mutableMapOf<UUID, FirstMemberDtoType>()
      val membersB = mutableMapOf<UUID, SecondMemberDtoType>()

      for (domainItem in listResponse.items)
        memberPutter(domainItem, membersA, membersB)

      return TwoMemberListResponseDto(
        listResponse.items.map(dtoMapper),
        membersA.values, membersB.values,
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }
  }
}