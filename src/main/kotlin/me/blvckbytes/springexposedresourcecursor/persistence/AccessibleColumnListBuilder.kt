package me.blvckbytes.springexposedresourcecursor.persistence

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

class AccessibleColumnListBuilder {

  private var parent: UserAccessibleColumn? = null
  private var exceptions: Array<out Column<*>>? = null
  private var subResults = mutableListOf<UserAccessibleColumn>()
  private var columns = mutableMapOf<Column<*>, String?>()
  private var camelCaseTransformNames = false

  fun allOf(table: Table): AccessibleColumnListBuilder {
    for (column in table.columns) {
      if (columns.containsKey(column))
        throw IllegalStateException("Encountered duplicate column while extending using allOf(): ${column.name}")

      this.columns[column] = null
    }

    return this
  }

  fun with(column: Column<*>): AccessibleColumnListBuilder {
    if (columns.containsKey(column))
      throw IllegalStateException("Encountered duplicate column: ${column.name}")

    this.columns[column] = null
    return this
  }

  fun extendFrom(columns: List<UserAccessibleColumn>): AccessibleColumnListBuilder {
    for (column in columns) {

      if (this.columns.containsKey(column.column))
        throw IllegalStateException("Encountered duplicate column while extending using extendFrom(): ${column.column.name}")

      this.columns[column.column] = column.key
    }

    return this
  }

  fun withCustomNamed(column: Column<*>, customName: String): AccessibleColumnListBuilder {
    val existingName = columns[column]

    if (existingName != null)
      throw IllegalStateException("Cannot assign a name to a column twice: ${column.name} -> $customName ($existingName)")

    columns[column] = customName
    return this
  }

  fun except(vararg exceptions: Column<*>): AccessibleColumnListBuilder {
    this.exceptions = exceptions
    return this
  }

  fun doCamelCaseTransformNames(value: Boolean = true): AccessibleColumnListBuilder {
    this.camelCaseTransformNames = value
    return this
  }

  fun withParent(
    parent: Column<*>,
    customName: String?,
    builderHandler: (builder: AccessibleColumnListBuilder) -> List<UserAccessibleColumn>
  ): AccessibleColumnListBuilder {
    val subBuilder = AccessibleColumnListBuilder()

    subBuilder.parent = UserAccessibleColumn(parent, customName, camelCaseTransformNames, this.parent)
    subBuilder.camelCaseTransformNames = camelCaseTransformNames

    subResults.addAll(builderHandler(subBuilder))
    return this
  }

  fun build(): List<UserAccessibleColumn> {
    val result = subResults.toMutableList()

    for (columnEntry in columns.entries) {
      if (exceptions?.contains(columnEntry.key) == true)
        continue

      result.add(UserAccessibleColumn(
        columnEntry.key,
        columnEntry.value,
        camelCaseTransformNames,
        parent
      ))
    }

    return result
  }
}