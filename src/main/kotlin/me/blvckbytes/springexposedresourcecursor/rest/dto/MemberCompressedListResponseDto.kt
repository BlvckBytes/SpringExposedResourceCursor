package me.blvckbytes.springexposedresourcecursor.rest.dto

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import me.blvckbytes.springexposedresourcecursor.domain.ListResponse
import java.util.*
import kotlin.collections.LinkedHashMap

class MemberCompressedListResponseDto(
  @field:JsonIgnore
  private val _items: List<*>,
  @field:JsonIgnore
  private val _referencedMembersMap: Map<String, Collection<*>>,
  @field:JsonIgnore
  private val _cursor: ResponseRequestCursorDto
) {

  @get:JsonAnyGetter
  val dtoProperties: LinkedHashMap<String, *>
    get() {
      // Since this serialization output order is desired and jackson will just accept either
      // alphabetical order or compile-time known name order, the following is the only way of
      // enforcing said order for dynamic named properties.
      val map = LinkedHashMap<String, Any>()

      map["items"] = _items
      map.putAll(_referencedMembersMap)
      map["cursor"] = _cursor

      return map
    }

  companion object {

    private val ILLEGAL_MEMBER_NAMES = listOf("items", "cursor")

    fun <DomainType: Any, ItemDtoType : Any, MemberDtoType : Any> fromListResponseWithOneReferenced(
      listResponse: ListResponse<DomainType>,
      memberName: String,
      itemDtoMapper: (item: DomainType) -> ItemDtoType,
      memberPutter: (
        item: DomainType,
        memberMap: MutableMap<UUID, MemberDtoType>
      ) -> Unit,
    ) : MemberCompressedListResponseDto {
      validateMemberNames(memberName)

      val items = mutableListOf<ItemDtoType>()
      val members = mutableMapOf<UUID, MemberDtoType>()

      for (domainItem in listResponse.items) {
        items.add(itemDtoMapper(domainItem))
        memberPutter(domainItem, members)
      }

      return MemberCompressedListResponseDto(
        items, mapOf(Pair(memberName, members.values)),
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }

    fun <DomainType: Any, ItemDtoType : Any, FirstMemberDtoType : Any, SecondMemberDtoType : Any> fromListResponseWithTwoReferenced(
      listResponse: ListResponse<DomainType>,
      firstMemberName: String,
      secondMemberName: String,
      itemDtoMapper: (item: DomainType) -> ItemDtoType,
      memberPutter: (
        item: DomainType,
        firstMemberMap: MutableMap<UUID, FirstMemberDtoType>,
        secondMemberMap: MutableMap<UUID, SecondMemberDtoType>,
      ) -> Unit,
    ) : MemberCompressedListResponseDto {
      validateMemberNames(firstMemberName, secondMemberName)

      val items = mutableListOf<ItemDtoType>()

      val firstMembers = mutableMapOf<UUID, FirstMemberDtoType>()
      val secondMembers = mutableMapOf<UUID, SecondMemberDtoType>()

      for (domainItem in listResponse.items) {
        items.add(itemDtoMapper(domainItem))
        memberPutter(domainItem, firstMembers, secondMembers)
      }

      return MemberCompressedListResponseDto(
        items,
        mapOf(
          Pair(firstMemberName, firstMembers.values),
          Pair(secondMemberName, secondMembers.values),
        ),
        ResponseRequestCursorDto.fromRequestCursor(listResponse.cursor)
      )
    }

    private fun validateMemberNames(vararg names: String) {
      for (name in names) {
        if (ILLEGAL_MEMBER_NAMES.contains(name.lowercase()))
          throw IllegalStateException("Cannot use reserved name $name as a member-name")
      }
    }
  }
}