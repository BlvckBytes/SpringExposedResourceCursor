package me.blvckbytes.springexposedresourcecursor.rest.service

import me.blvckbytes.filterexpressionparser.error.AParserError
import me.blvckbytes.filterexpressionparser.parser.FilterExpressionParser
import me.blvckbytes.filterexpressionparser.parser.expression.ABinaryFilterExpression
import me.blvckbytes.filterexpressionparser.tokenizer.FilterExpressionTokenizer
import me.blvckbytes.propertyvalidation.ValidationBuilder
import me.blvckbytes.propertyvalidation.validatior.*
import me.blvckbytes.propertyvalidation.validatior.Comparison
import me.blvckbytes.springexposedresourcecursor.domain.RequestResourceCursor
import me.blvckbytes.springexposedresourcecursor.domain.SortingOrder
import me.blvckbytes.springexposedresourcecursor.domain.exception.FilterExpressionParserException
import me.blvckbytes.springexposedresourcecursor.domain.exception.SortingExpressionParserException
import me.blvckbytes.springexposedresourcecursor.rest.dto.RequestResourceCursorDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.util.logging.Logger
import kotlin.collections.LinkedHashMap

@Component
class RequestResourceCursorService(
  @Value("\${resource-cursor.default-page-size}")
  private val pageSizeDefault: Int,
  @Value("\${resource-cursor.min-page-size}")
  private val minPageSize: Int,
  @Value("\${resource-cursor.max-page-size}")
  private val maxPageSize: Int,
  @Value("\${resource-cursor.default-sorting:#{null}}")
  private val defaultSorting: String?,
) {

  // TODO: This seems to not be the right way of obtaining a logger...
  private val logger = Logger.getGlobal()

  private val parser = FilterExpressionParser(logger)
  private val preParsedDefaultSorting: LinkedHashMap<String, SortingOrder>?

  init {
    preParsedDefaultSorting = if (defaultSorting == null) null else parseSorting(defaultSorting)
  }

  fun parseCursorFromDto(cursorDto: RequestResourceCursorDto): RequestResourceCursor {
    ValidationBuilder()
      .addValidator(CompareToConstant(RequestResourceCursorDto::selectedPage, cursorDto.selectedPage, 1, Comparison.GREATER_THAN_OR_EQUALS))
      .addValidator(CompareToMinMax(RequestResourceCursorDto::pageSize, cursorDto.pageSize, minPageSize, maxPageSize))
      .addValidator(NullOrNotBlank(RequestResourceCursorDto::sorting, cursorDto.sorting))
      .addValidator(NullOrNotBlank(RequestResourceCursorDto::filtering, cursorDto.filtering))
      .throwIfApplicable()

    return RequestResourceCursor(
      cursorDto.selectedPage ?: 1,
      cursorDto.pageSize ?: pageSizeDefault,
      if (cursorDto.sorting == null) preParsedDefaultSorting else parseSorting(cursorDto.sorting),
      if (cursorDto.filtering == null) null else parseFiltering(cursorDto.filtering)
    )
  }

  private fun parseFiltering(filteringString: String): ABinaryFilterExpression<*, *>? {
    try {
      return parser.parse(FilterExpressionTokenizer(logger, filteringString))
    } catch (exception: AParserError) {
      throw FilterExpressionParserException(exception)
    }
  }

  private fun parseSorting(sortingString: String): LinkedHashMap<String, SortingOrder>? {
    val sorting = LinkedHashMap<String, SortingOrder>()
    val sortingItems = sortingString.split(",")

    for (sortingItem in sortingItems) {
      val sortingOrder = when (sortingItem[0]) {
        '+' -> SortingOrder.ASCENDING
        '-' -> SortingOrder.DESCENDING
        else -> throw SortingExpressionParserException("Sorting fields need to start with either '+' (Ascending) or '-' (Descending)")
      }

      val sortingField = sortingItem.substring(1)

      if (sortingField.isEmpty())
        throw SortingExpressionParserException("Sorting field names cannot be empty")

      if (sorting.containsKey(sortingField))
        throw SortingExpressionParserException("Sorting field names cannot occur multiple times")

      sorting[sortingField] = sortingOrder
    }

    return sorting
  }
}