package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException

class SortingExpressionParserException(
  private val error: String
) : DescribedException() {
  override fun getDescription(): String {
    return "Could not parse the sorting expression: $error"
  }
}