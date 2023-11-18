package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException

open class UnsupportedPropertyException(
  private val property: String,
  private val availableProperties: Collection<String>,
  private val displayName: String,
) : DescribedException() {

  override fun getDescription(): String {
    // TODO: It would be super helpful to tell the user about each property's type as well
    return "The property $property does not exist on the entity $displayName." +
      " Please select from the following list only: ${availableProperties.joinToString()}"
  }
}