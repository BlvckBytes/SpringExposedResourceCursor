package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.filterexpressionparser.parser.ComparisonOperator
import me.blvckbytes.filterexpressionparser.parser.LiteralType
import me.blvckbytes.filterexpressionparser.parser.expression.*
import me.blvckbytes.springcommon.exception.DescribedException
import me.blvckbytes.springcommon.exception.DescribedInternalException
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.exception.PropertyDataTypeMismatchException
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedOperatorException
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedPropertyException
import org.jetbrains.exposed.sql.*

class FilterRequestApplicator(
  private val displayName: String,
  private val accessibleColumnByName: Map<String, UserAccessibleColumn>
) {

  fun apply(resourceCursor: RequestResourceCursor, query: Query) {
    val expression = resourceCursor.filtering

    if (expression != null)
      query.andWhere { filterExpressionToOperator(expression) }
  }

  private fun terminalExpressionToExposedExpression(accessibleColumn: UserAccessibleColumn, terminal: TerminalExpression<*>): Expression<*>? {
    return when (terminal) {
      is DoubleExpression -> {
        if (accessibleColumn.dataType != ExpressionDataType.DOUBLE)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.DOUBLE, accessibleColumn.dataType)

        accessibleColumn.valueToExpression(terminal.value)
      }
      is LongExpression -> {
        if (accessibleColumn.dataType != ExpressionDataType.LONG)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.LONG, accessibleColumn.dataType)

        accessibleColumn.valueToExpression(terminal.value)
      }
      is StringExpression -> {
        if (accessibleColumn.dataType != ExpressionDataType.STRING)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.STRING, accessibleColumn.dataType)

        var value = terminal.value

        if (!terminal.isCaseSensitive)
          value = value.lowercase()

        accessibleColumn.valueToExpression(value)
      }
      is LiteralExpression -> when (terminal.value!!) {
        LiteralType.NULL -> null
        LiteralType.TRUE, LiteralType.FALSE -> {
          if (accessibleColumn.dataType != ExpressionDataType.BOOLEAN)
            throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.BOOLEAN, accessibleColumn.dataType)

          accessibleColumn.valueToExpression(terminal.value == LiteralType.TRUE)
        }
      }
      is IdentifierExpression -> resolveColumn(terminal.value).column
      else -> throw DescribedInternalException("Encountered unimplemented filter terminal expression type")
    }
  }

  private fun resolveColumn(name: String): UserAccessibleColumn {
    return accessibleColumnByName[name] ?: throw UnsupportedPropertyException(name, accessibleColumnByName.values, displayName)
  }

  private fun escapeLikeValue(value: String): String {
    return value
      // SQL uses C-Style escapes, so single backslashes need to be escaped as well
      .replace("\\", "\\\\")
      // The percentage wildcard is used to specify a pattern of zero (0) or more characters
      .replace("%", "\\%")
      // The underscore wildcard is used to match exactly one character
      .replace("_", "\\_")
  }

  @Suppress("UNCHECKED_CAST")
  private fun instantiateOperator(accessibleColumn: UserAccessibleColumn, operator: ComparisonOperator, terminalValue: TerminalExpression<*>): Op<Boolean> {
    var operand: Expression<*> = accessibleColumn.column

    if (
      terminalValue is StringExpression &&
      accessibleColumn.column.columnType is StringColumnType
    ) {
      if (!terminalValue.isCaseSensitive)
        operand = LowerCase(operand as Expression<String>)

      if (terminalValue.shouldTrimTarget())
        operand = Trim(operand as Expression<String>)
    }

    // These operators all operate only on string terminal values and string columns
    if (
      operator == ComparisonOperator.CONTAINS ||
      operator == ComparisonOperator.CONTAINS_FUZZY ||
      operator == ComparisonOperator.STARTS_WITH ||
      operator == ComparisonOperator.ENDS_WITH ||
      operator == ComparisonOperator.REGEX_MATCHER
    ) {
      if (terminalValue !is StringExpression)
        throw DescribedException.fromDescription("The filter operator $operator only works with string values")

      if (accessibleColumn.column.columnType !is StringColumnType)
        throw DescribedException.fromDescription("The filter operator $operator only works on string columns")

      var terminalStringValue = terminalValue.value

      if (!terminalValue.isCaseSensitive)
        terminalStringValue = terminalStringValue.lowercase()

      return when (operator) {
        ComparisonOperator.CONTAINS_FUZZY -> AndOp(
          terminalStringValue.split(" ").map {
            LikeEscapeOp(
              operand,
              QueryParameter("%${escapeLikeValue(it)}%", accessibleColumn.column.columnType),
              true, null
            )
          }
        )
        ComparisonOperator.REGEX_MATCHER -> (
          @Suppress("UNCHECKED_CAST")
          RegexpOp(
            operand as Expression<String>,
            QueryParameter(terminalStringValue, accessibleColumn.column.columnType),
            true
          )
        )
        else -> {
          val escapedValue = escapeLikeValue(terminalStringValue)
          LikeEscapeOp(
            operand,
            QueryParameter(
              when (operator) {
                ComparisonOperator.STARTS_WITH -> "$escapedValue%"
                ComparisonOperator.ENDS_WITH -> "%$escapedValue"
                ComparisonOperator.CONTAINS -> "%$escapedValue%"
                else -> throw IllegalStateException()
              },
              accessibleColumn.column.columnType
            ),
            true, null
          )
        }
      }
    }

    var value: Expression<*>?

    // Longs should be able to operate on string columns by accessing their length
    if (
      terminalValue is LongExpression &&
      accessibleColumn.column.columnType is StringColumnType
    ) {
      operand = CharLength(accessibleColumn.column as Column<String>)
      value = QueryParameter(terminalValue.value, LongColumnType())
    }

    else
      value = terminalExpressionToExposedExpression(accessibleColumn, terminalValue)

    if (value == null) {
      if (operator == ComparisonOperator.EQUAL)
        return IsNullOp(operand)

      if (operator == ComparisonOperator.NOT_EQUAL)
        return IsNotNullOp(operand)

      throw UnsupportedOperatorException(null, operator, listOf(ComparisonOperator.EQUAL, ComparisonOperator.NOT_EQUAL))
    }

    if (
      accessibleColumn.column.columnType is StringColumnType &&
      terminalValue is StringExpression &&
      (operator == ComparisonOperator.EQUAL || operator == ComparisonOperator.NOT_EQUAL)
    ) {
      operand = Trim(LowerCase(operand as Expression<String>))
      value = Trim(LowerCase(value as Expression<String>))
    }

    return when (operator) {
      ComparisonOperator.EQUAL -> EqOp(operand, value)
      ComparisonOperator.NOT_EQUAL -> NeqOp(operand, value)
      ComparisonOperator.GREATER_THAN -> GreaterOp(operand, value)
      ComparisonOperator.GREATER_THAN_OR_EQUAL -> GreaterEqOp(operand, value)
      ComparisonOperator.LESS_THAN -> LessOp(operand, value)
      ComparisonOperator.LESS_THAN_OR_EQUAL -> LessEqOp(operand, value)
      else -> throw DescribedException.fromDescription("The operator $operator is not (yet) supported")
    }
  }

  private fun filterExpressionToOperator(expression: AExpression): Op<Boolean> {
    if (expression is ComparisonExpression) {
      val column = resolveColumn(expression.lhs.value)
      return instantiateOperator(column, expression.operator, expression.rhs)
    }

    if (expression is ConjunctionExpression) {
      return AndOp(listOf(
        filterExpressionToOperator(expression.lhs),
        filterExpressionToOperator(expression.rhs)
      ))
    }

    if (expression is DisjunctionExpression) {
      return OrOp(listOf(
        filterExpressionToOperator(expression.lhs),
        filterExpressionToOperator(expression.rhs)
      ))
    }

    throw IllegalStateException("Unexpected expression type: $expression")
  }
}