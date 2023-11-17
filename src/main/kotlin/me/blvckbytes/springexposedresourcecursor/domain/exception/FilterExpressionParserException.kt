package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.filterexpressionparser.error.AParserError
import me.blvckbytes.springcommon.exception.DescribedException

class FilterExpressionParserException(
  private val error: AParserError
) : DescribedException() {
  override fun getDescription(): String {
    return "Could not parse the filter expression:${error.message!!}"
  }
}