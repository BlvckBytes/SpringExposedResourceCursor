package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException
import me.blvckbytes.springexposedresourcecursor.persistence.UserAccessibleColumn

open class UnsupportedPropertyException(
  private val property: String,
  private val availableProperties: Collection<UserAccessibleColumn>,
  private val displayName: String,
) : DescribedException() {

  override fun getDescription(): String {
    val availablePropertiesString = availableProperties.joinToString() {
      "${it.key} (${it.dataType.name.lowercase()})"
    }

    return "The property $property does not exist on the entity $displayName." +
      " Please select from the following list only: $availablePropertiesString"
  }
}