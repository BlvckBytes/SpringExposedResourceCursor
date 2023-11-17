package me.blvckbytes.springexposedresourcecursor.domain.exception

import me.blvckbytes.springcommon.exception.DescribedException

open class UnsupportedPropertyException(
  private val property: String,
  private val displayName: String,
) : DescribedException() {

  override fun getDescription(): String {
    return "The property $property does not exist on the entity $displayName"
  }
}