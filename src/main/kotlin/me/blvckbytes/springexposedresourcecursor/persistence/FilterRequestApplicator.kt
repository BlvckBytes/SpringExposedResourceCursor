package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.filterexpressionparser.parser.ComparisonOperator
import me.blvckbytes.filterexpressionparser.parser.LiteralType
import me.blvckbytes.filterexpressionparser.parser.expression.*
import me.blvckbytes.springcommon.exception.DescribedInternalException
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.exception.UnsupportedPropertyException
import org.jetbrains.exposed.sql.*

class FilterRequestApplicator(
  table: Table,
  private val displayName: String,
  vararg hiddenColumns: Column<*>
) {

  private val columnByName: Map<String, Column<*>>

  init {
    val columns = mutableMapOf<String, Column<*>>()

    for (column in table.columns) {
      if (hiddenColumns.contains(column))
        continue

      columns[column.name] = column
    }

    columnByName = columns
  }

  fun apply(resourceCursor: RequestResourceCursor, query: Query) {
    val expression = resourceCursor.filtering
    if (expression != null)
      query.andWhere { filterExpressionToOperator(expression) }
  }

  private fun terminalExpressionToExposedExpression(column: Column<*>, terminal: TerminalExpression<*>): Expression<*>? {
    // TODO: Use column.columnType to either transform terminal values to their correct type or throw an error
    return when (terminal) {
      is DoubleExpression -> doubleParam(terminal.value)
      is LongExpression -> longParam(terminal.value)
      is StringExpression -> stringParam(terminal.value)
      is LiteralExpression -> when (terminal.value!!) {
        LiteralType.NULL -> null
        LiteralType.TRUE -> booleanParam(true)
        LiteralType.FALSE -> booleanParam(false)
      }
      is IdentifierExpression -> resolveColumn(terminal.value)
      else -> throw DescribedInternalException("Encountered unimplemented filter terminal expression type")
    }
  }

  private fun resolveColumn(name: String): Column<*> {
    return columnByName[name] ?: throw UnsupportedPropertyException(name, displayName)
  }

  private fun instantiateOperator(column: Column<*>, operator: ComparisonOperator, terminalValue: TerminalExpression<*>): Op<Boolean> {
    val value = terminalExpressionToExposedExpression(column, terminalValue) ?: return IsNullOp(column)

    return when (operator) {
      ComparisonOperator.EQUAL -> EqOp(column, value)
      ComparisonOperator.NOT_EQUAL -> NeqOp(column, value)
      ComparisonOperator.REGEX_MATCHER -> throw DescribedInternalException("The regex operator is not (yet) supported")
      ComparisonOperator.CONTAINS_EXACT -> {
        throw DescribedInternalException("The contains exact operator is not (yet) supported")
//        LikeEscapeOp(column, stringParam(""), true, '%')
      }
      ComparisonOperator.CONTAINS_FUZZY -> throw DescribedInternalException("The contains fuzzy operator is not (yet) supported")
      ComparisonOperator.GREATER_THAN -> GreaterOp(column, value)
      ComparisonOperator.GREATER_THAN_OR_EQUAL -> GreaterEqOp(column, value)
      ComparisonOperator.LESS_THAN -> LessOp(column, value)
      ComparisonOperator.LESS_THAN_OR_EQUAL -> LessEqOp(column, value)
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