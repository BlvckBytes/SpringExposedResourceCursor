package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException
import me.blvckbytes.springexposedresourcecursor.persistence.UserAccessibleColumn

open class MalformedRegexException(
  private val accessibleColumn: UserAccessibleColumn,
  private val value: String,
) : DescribedException() {

  override fun getDescription(): String {
    return "The regular expression \"$value\", applied on the property ${accessibleColumn.key} is malformed"
  }
}