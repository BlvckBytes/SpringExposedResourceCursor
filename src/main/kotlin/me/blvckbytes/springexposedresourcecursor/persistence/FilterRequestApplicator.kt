package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.filterexpressionparser.parser.ComparisonOperator
import me.blvckbytes.filterexpressionparser.parser.LiteralType
import me.blvckbytes.filterexpressionparser.parser.expression.*
import me.blvckbytes.springcommon.exception.DescribedInternalException
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.exception.PropertyDataTypeMismatchException
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedOperatorException
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedPropertyException
import org.jetbrains.exposed.sql.*

class FilterRequestApplicator(
  private val displayName: String,
  accessibleColumns: List<UserAccessibleColumn>
) {

  private val accessibleColumnByName: Map<String, UserAccessibleColumn>

  init {
    val columns = mutableMapOf<String, UserAccessibleColumn>()

    for (accessibleColumn in accessibleColumns)
      columns[accessibleColumn.key] = accessibleColumn

    accessibleColumnByName = columns
  }

  fun apply(resourceCursor: RequestResourceCursor, query: Query): Query {
    val expression = resourceCursor.filtering

    if (expression != null)
      query.andWhere { filterExpressionToOperator(expression) }

    return query
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
        // TODO: (Idea) would it be possible to somehow encode invoking LOWERCASE, UPPERCASE and TRIM?
        if (accessibleColumn.dataType != ExpressionDataType.STRING)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.STRING, accessibleColumn.dataType)

        accessibleColumn.valueToExpression(terminal.value)
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

  private fun instantiateOperator(accessibleColumn: UserAccessibleColumn, operator: ComparisonOperator, terminalValue: TerminalExpression<*>): Op<Boolean> {
    var operand: Expression<*> = accessibleColumn.column
    val value: Expression<*>?

    // NOTE: Longs should be able to operate on string columns by accessing their length
    if (
      terminalValue is LongExpression &&
      accessibleColumn.column.columnType is StringColumnType
    ) {
      @Suppress("UNCHECKED_CAST")
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

    // TODO: Check operators against terminal expression types to catch unlogical comparisons

    return when (operator) {
      ComparisonOperator.EQUAL -> EqOp(operand, value)
      ComparisonOperator.NOT_EQUAL -> NeqOp(operand, value)
      ComparisonOperator.REGEX_MATCHER -> throw DescribedInternalException("The regex operator is not (yet) supported")
      ComparisonOperator.CONTAINS_EXACT -> {
        // TODO: Implement these missing operations
        throw DescribedInternalException("The contains exact operator is not (yet) supported")
//        LikeEscapeOp(column, stringParam(""), true, '%')
      }
      ComparisonOperator.CONTAINS_FUZZY -> throw DescribedInternalException("The contains fuzzy operator is not (yet) supported")
      ComparisonOperator.GREATER_THAN -> GreaterOp(operand, value)
      ComparisonOperator.GREATER_THAN_OR_EQUAL -> GreaterEqOp(operand, value)
      ComparisonOperator.LESS_THAN -> LessOp(operand, value)
      ComparisonOperator.LESS_THAN_OR_EQUAL -> LessEqOp(operand, value)
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