package me.blvckbytes.springexposedresourcecursor

import me.blvckbytes.springexposedresourcecursor.persistence.UserAccessibleColumn
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserAccessibleColumnTest {

  private val fakeTable = Table("fake_table")

  @Test
  fun `should camel case snake case names`() {
    createSpecificCase("", "")
    createSpecificCase(" ", " ")
    createSpecificCase("helloWorld", "hello_world")
    createSpecificCase("helloWorld", "hello__world")
    createSpecificCase("helloWorld", "_hello__world_")
    createSpecificCase("helloWorld", "_hello_world_")
    createSpecificCase("helloWorld", "_hellO_woRLd_")
  }

  private fun createSpecificCase(expectedName: String, columnName: String) {
    val result = UserAccessibleColumn(createFakeColumn(columnName), null, true, null).makeKey()
    assertEquals(expectedName, result)
  }

  private fun createFakeColumn(name: String): Column<*> {
    return Column<Any>(fakeTable, name, DecimalColumnType(0, 0))
  }
}