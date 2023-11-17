package me.blvckbytes.springexposedresourcecursor.rest.dto

import jakarta.validation.constraints.Min
import me.blvckbytes.springcommon.validators.MinMaxInt
import me.blvckbytes.springcommon.validators.NullOrNotBlank
import java.util.*

class RequestResourceCursorDto(
  @field:Min(1)
  val selectedPage: Int?,
  @field:MinMaxInt(5, 100)
  val pageSize: Int?,
  @field:NullOrNotBlank
  val sorting: String?,
  @field:NullOrNotBlank
  val filtering: String?,
)