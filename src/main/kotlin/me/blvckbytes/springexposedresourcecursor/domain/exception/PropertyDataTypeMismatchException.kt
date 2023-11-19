package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException
import me.blvckbytes.springexposedresourcecursor.persistence.ExpressionDataType

class PropertyDataTypeMismatchException(
  private val property: String,
  private val displayName: String,
  private val rejectedDataType: ExpressionDataType,
  private val requiredDataType: ExpressionDataType,
) : DescribedException() {

  override fun getDescription(): String {
    return "The property $property of the entity $displayName cannot accept filter values of type " +
      "${rejectedDataType.name.lowercase()}, as it is of type ${requiredDataType.name.lowercase()}"
  }
}