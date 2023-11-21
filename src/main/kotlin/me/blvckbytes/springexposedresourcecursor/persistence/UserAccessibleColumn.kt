package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.springexposedresourcecursor.domain.exception.InvalidDateTimeException
import me.blvckbytes.springexposedresourcecursor.domain.exception.InvalidUUIDException
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.reflect.KClass

class UserAccessibleColumn(
  val column: Column<*>,
  private val customName: String?,
  private val camelCaseTransformName: Boolean,
  private val parent: UserAccessibleColumn?
) {

  val dataType: ExpressionDataType
  val key = makeKey()

  val valueToExpression: (value: Any) -> Expression<*>

  init {
    val result = decideDataTypeAndMapper(column)
    this.dataType = result.first
    this.valueToExpression = result.second
  }

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

  private fun decideDataTypeAndMapper(column: Column<*>): Pair<ExpressionDataType, ((value: Any) -> Expression<*>)> {
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

    var identifierColumnType: ColumnType? = null

    if (column.name == "id") {
      identifierColumnType = when (column.table) {
        is IntIdTable,
        is LongIdTable -> LongColumnType()
        is UUIDTable -> UUIDColumnType()
        else -> null
      }
    }

    return when (val columnType = identifierColumnType ?: column.columnType) {
      is DoubleColumnType,
      is DecimalColumnType,
      is FloatColumnType -> Pair(ExpressionDataType.DOUBLE) {
        ensureCorrectValueType(ExpressionDataType.DOUBLE, it)
        QueryParameter(it, columnType)
      }
      is LongColumnType,
      is IntegerColumnType,
      is ByteColumnType,
      is ShortColumnType,
      is ULongColumnType,
      is UIntegerColumnType,
      is UByteColumnType,
      is UShortColumnType -> Pair(ExpressionDataType.LONG) {
        ensureCorrectValueType(ExpressionDataType.LONG, it)
        QueryParameter(it, columnType)
      }
      is UUIDColumnType -> Pair(ExpressionDataType.STRING) {
        ensureCorrectValueType(ExpressionDataType.STRING, it)
        QueryParameter(parseUUID(key, it as String), columnType)
      }
      is JavaLocalDateTimeColumnType -> Pair(ExpressionDataType.STRING) {
        ensureCorrectValueType(ExpressionDataType.STRING, it)
        QueryParameter(parseLocalDateTime(key, it as String), columnType)
      }
      is StringColumnType,
      is CharacterColumnType -> Pair(ExpressionDataType.STRING) {
        ensureCorrectValueType(ExpressionDataType.STRING, it)
        QueryParameter(it, columnType)
      }
      is BlobColumnType,
      is BinaryColumnType -> Pair(ExpressionDataType.STRING) {
        ensureCorrectValueType(ExpressionDataType.STRING, it)
        QueryParameter((it as String).toByteArray(), columnType)
      }
      is BooleanColumnType -> Pair(ExpressionDataType.BOOLEAN) {
        ensureCorrectValueType(ExpressionDataType.BOOLEAN, it)
        QueryParameter(it, columnType)
      }
      is EntityIDColumnType<*> -> decideDataTypeAndMapper((column.columnType as EntityIDColumnType<*>).idColumn)
      else -> throw IllegalStateException("Could not map column type to expression data type: ${column.columnType.javaClass.simpleName}")
    }
  }

  private fun ensureCorrectValueType(dataType: ExpressionDataType, value: Any) {
    val type: KClass<*> = when (dataType) {
      ExpressionDataType.STRING -> String::class
      ExpressionDataType.LONG -> Long::class
      ExpressionDataType.DOUBLE -> Double::class
      ExpressionDataType.BOOLEAN -> Boolean::class
    }

    // NOTE: This exception doesn't need to be user-readable, as it should never occur since the
    // corresponding applicator has to check beforehand and throw a proper type mismatch exception
    if (!type.isInstance(value))
      throw IllegalStateException("Required type $dataType but found ${value.javaClass.simpleName}")
  }

  private fun parseLocalDateTime(columnName: String, input: String): LocalDateTime {
    try {
      return LocalDateTime.parse(input)
    } catch (exception: DateTimeParseException) {
      throw InvalidDateTimeException(input, columnName)
    }
  }

  private fun parseUUID(columnName: String, input: String): UUID {
    try {
      return UUID.fromString(input)
    } catch (exception: IllegalArgumentException) {
      throw InvalidUUIDException(input, columnName)
    }
  }
}