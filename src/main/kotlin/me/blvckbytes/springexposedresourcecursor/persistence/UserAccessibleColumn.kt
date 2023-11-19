package me.blvckbytes.springexposedresourcecursor.persistence

import org.jetbrains.exposed.sql.Column

class UserAccessibleColumn(
  val column: Column<*>,
  private val customName: String?,
  private val camelCaseTransformName: Boolean,
  private val parent: UserAccessibleColumn?
) {

  fun makeKey(): String {
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
}