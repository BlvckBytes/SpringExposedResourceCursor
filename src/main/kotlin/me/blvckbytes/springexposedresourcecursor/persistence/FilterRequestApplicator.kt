package me.blvckbytes.springexposedresourcecursor.persistence

import me.blvckbytes.filterexpressionparser.parser.ComparisonOperator
import me.blvckbytes.filterexpressionparser.parser.LiteralType
import me.blvckbytes.filterexpressionparser.parser.expression.*
import me.blvckbytes.springcommon.exception.DescribedInternalException
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.exception.PropertyDataTypeMismatchException
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

        doubleParam(terminal.value)
      }
      is LongExpression -> {
        // TODO: (Idea) When a long is provided on a string column, it could be automatically compared with the column's length
        if (accessibleColumn.dataType != ExpressionDataType.LONG)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.LONG, accessibleColumn.dataType)

        longParam(terminal.value)
      }
      is StringExpression -> {
        // TODO: If the column is of type UUID, Character, Blob or Binary, a value transformation should occur beforehand
        if (accessibleColumn.dataType != ExpressionDataType.STRING)
          throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.STRING, accessibleColumn.dataType)

        stringParam(terminal.value)
      }
      is LiteralExpression -> when (terminal.value!!) {
        LiteralType.NULL -> null
        LiteralType.TRUE, LiteralType.FALSE -> {
          if (accessibleColumn.dataType != ExpressionDataType.BOOLEAN)
            throw PropertyDataTypeMismatchException(accessibleColumn.key, displayName, ExpressionDataType.BOOLEAN, accessibleColumn.dataType)

          booleanParam(terminal.value == LiteralType.TRUE)
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
    val value = terminalExpressionToExposedExpression(accessibleColumn, terminalValue) ?: return IsNullOp(accessibleColumn.column)

    return when (operator) {
      ComparisonOperator.EQUAL -> EqOp(accessibleColumn.column, value)
      ComparisonOperator.NOT_EQUAL -> NeqOp(accessibleColumn.column, value)
      ComparisonOperator.REGEX_MATCHER -> throw DescribedInternalException("The regex operator is not (yet) supported")
      ComparisonOperator.CONTAINS_EXACT -> {
        // TODO: Implement these missing operations
        throw DescribedInternalException("The contains exact operator is not (yet) supported")
//        LikeEscapeOp(column, stringParam(""), true, '%')
      }
      ComparisonOperator.CONTAINS_FUZZY -> throw DescribedInternalException("The contains fuzzy operator is not (yet) supported")
      ComparisonOperator.GREATER_THAN -> GreaterOp(accessibleColumn.column, value)
      ComparisonOperator.GREATER_THAN_OR_EQUAL -> GreaterEqOp(accessibleColumn.column, value)
      ComparisonOperator.LESS_THAN -> LessOp(accessibleColumn.column, value)
      ComparisonOperator.LESS_THAN_OR_EQUAL -> LessEqOp(accessibleColumn.column, value)
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