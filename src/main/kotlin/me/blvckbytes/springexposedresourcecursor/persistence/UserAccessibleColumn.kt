package me.blvckbytes.springexposedresourcecursor.persistence

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*

class UserAccessibleColumn(
  val column: Column<*>,
  private val customName: String?,
  private val camelCaseTransformName: Boolean,
  private val parent: UserAccessibleColumn?
) {

  val dataType: ExpressionDataType = decideDataType(column)

  val key = makeKey()

  private fun makeKey(): String {
    val columnName = getColumnName()

    if (parent == null)
      return columnName

    return parent.makeKey() + "." + columnName
  }

  private fun getColumnName(): String {
    if (customName != null)
      return customName

    if (!camelCaseTransformName)
      return column.name

    return asciiCamelCaseTransform(column.name)
  }

  private fun asciiCamelCaseTransform(input: String): String {
    val inputLength = input.length
    val result = CharArray(inputLength)
    var resultSize = 0

    var upperCaseFlag = false

    for (i in 0 until inputLength) {
      val currentChar = input[i]

      if (currentChar == '_') {
        upperCaseFlag = resultSize > 0
        continue
      }

      // A 65 - Z 90
      val isUpperCaseAlphabet = currentChar.code in 65..90

      if (upperCaseFlag) {
        upperCaseFlag = false

        if (isUpperCaseAlphabet) {
          result[resultSize++] = currentChar
          continue
        }

        // a 97 - z 112
        val isLowerCaseAlphabet = currentChar.code in 97..122

        if (isLowerCaseAlphabet)
          result[resultSize++] = currentChar.minus(32)

        continue
      }

      if (isUpperCaseAlphabet) {
        result[resultSize++] = currentChar.plus(32)
        continue
      }

      result[resultSize++] = currentChar
    }

    return String(result.sliceArray(0 until resultSize))
  }

  private fun decideDataType(column: Column<*>): ExpressionDataType {
    // NOTE: It seems like Exposed has to - for whatever internal reason - access the database
    // when trying to read the Column#columnType property on columns that store the main ID
    // of an entity. A wild guess would be that the actual SQL type (which is stored in many
    // if not all *ColumnType-s) may vary depending on the underlying database, like either
    // being a binary(16) or a varchar for UUIDs, which has to be checked beforehand. This would
    // require that there is already an active database connection as well as a transaction context
    // when all that's required to know at this point is the "higher level data type". When using the
    // column list builder in the constructor of a Spring Component, this requirement becomes a pain.
    // To circumvent this, as all tables automatically name their primary ID "id", the data type can
    // be distinguished by the specific IdTable implementation class.

    if (column.name == "id") {
      when (column.table) {
        is IntIdTable,
        is LongIdTable -> return ExpressionDataType.DOUBLE
        is UUIDTable -> return ExpressionDataType.STRING
      }
    }

    return when (column.columnType) {
      is DoubleColumnType,
      is DecimalColumnType,
      is FloatColumnType -> ExpressionDataType.DOUBLE
      is LongColumnType,
      is IntegerColumnType,
      is ByteColumnType,
      is ShortColumnType,
      is ULongColumnType,
      is UIntegerColumnType,
      is UByteColumnType,
      is UShortColumnType -> ExpressionDataType.LONG
      is StringColumnType,
      is UUIDColumnType,
      is CharacterColumnType,
      is BlobColumnType,
      is BinaryColumnType -> ExpressionDataType.STRING
      is EntityIDColumnType<*> -> decideDataType((column.columnType as EntityIDColumnType<*>).idColumn)
      is BooleanColumnType -> ExpressionDataType.BOOLEAN
      else -> throw IllegalStateException("Could not map column type to expression data type: ${column.columnType.javaClass.simpleName}")
    }
  }
}