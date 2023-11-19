package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.filterexpressionparser.parser.ComparisonOperator
import me.blvckbytes.springcommon.exception.DescribedException

open class UnsupportedOperatorException(
  private val value: Any?,
  private val rejectedOperator: ComparisonOperator,
  private val availableOperators: Collection<ComparisonOperator>,
) : DescribedException() {

  override fun getDescription(): String {
    return "The operator $rejectedOperator is not available on the value $value, please " +
      "select one of ${availableOperators.joinToString()}"
  }
}