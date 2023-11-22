# SpringExposedResourceCursor

This project's aim is to completely take care of standardized resource cursors. It makes use of the [FilterExpressionParser](https://github.com/BlvckBytes/FilterExpressionParser) and maps the `AST` to a tree of [Jetbrains Exposed](https://github.com/JetBrains/Exposed) operations, to be applied to entity queries later on. It also handles parsing very simple sorting expressions, all while trying to be as user-friendly on the API-side as possible.

<!-- #toc -->

## Filtering

The tokens of these operators and the overall syntax of filter expressions can be found at the `FilterExpressionParser` project. This section will only roughly outline which SQL commands these operators map to.

In general, terminal values and columns need to have the same type to be comparable, with these exceptions:
- String terminals are parsed into a `UUID` for comparison with a `UUID` column
- String terminals are interpreted as binary data for `Binary` and `Blob` columns
- Long terminals can compare with all whole number column types
- Double terminals can compare with all decimal number column types
- A long terminal used on a column of any string type will compare against it's **length**

The difference between sensitive and non-sensitive strings is automatic trimming and case invariance when comparing, which of course only works if the collation of the column operated on is case- and optionally accent-sensitive in the first place. Personally, I use `utf8mb4_0900_as_cs` for this exact reason.

### String Literal Flags

If a string literal is flagged to be compared case-insensitively, the `LOWER()`-function is applied to both sides of the SQL-expression before the comparison. Likewise, when a string literal is flagged to trim it's target-value, the `TRIM()`-function is being applied to the target column before comparing.

### EQUAL

```
<column> == <value>
```

```sql
WHERE column = value
```

When `<column>` is a string column and `<value>` is a long, the following expression will be generated (this also applies for other numeric operators, but is only shown once):

```sql
WHERE CHAR_LENGTH(column) = value
```

### NOT_EQUAL

```
<column> != <value>
```

```sql
WHERE column != value
```

### REGEX_MATCHER

```
<column> ? <value>
```

```sql
WHERE REGEXP_LIKE(<column>, <value>, 'c')
```

### STARTS_WITH

```
<column> >% <value>
```

```sql
WHERE <column> LIKE '<value>%'
```

### ENDS_WITH

```
<column> <% <value>
```

```sql
WHERE <column> LIKE '%<value>'
```

### CONTAINS

```
<column> % <value>
```

```sql
WHERE <column> LIKE '%<value>%'
```

### CONTAINS_FUZZY

```
<column> %% <value>
```

This operator adds a `LIKE` clause analogous to [CONTAINS](#contains) for each word of the input string, split by space, joined by `AND`. All words have to occur, but in no certain order, and duplicate words only have to be present once.

### GREATER_THAN

```
<column> > <value>
```

```sql
WHERE column > value
```

### GREATER_THAN_OR_EQUAL

```
<column> >= <value>
```

```sql
WHERE column >= value
```

### LESS_THAN

```
<column> < <value>
```

```sql
WHERE column < value
```

### LESS_THAN_OR_EQUAL

```
<column> <= <value>
```

```sql
WHERE column <= value
```

## Sorting

For sorting, the exact same keys are available and to be used as for [Filtering](#filtering). To sort in an ascending manner, prepend a `+`, to sort in descending order, prepend a `-`. To further sort on equality of the previous key, multiple columns can be specified, separated by a comma `,`.

```
+id,-name,-description
```

```sql
ORDER BY
`description` DESC,
`id` ASC,
`name` DESC
```

## Pagination

The numeric `selectedPage` and `pageSize` values dictate the current pagination frame, based on the order that the user selected. The default page size has to be provided by `resource-cursor.default-page-size` in your `application.properties`, as it's not a mandatory request parameter. The following SQL will be generated based on these inputs:

```sql
LIMIT <pageSize> OFFSET <(selectedPage - 1) * pageSize>
```

## Example Use

Let's define two tables with a `1:n` relationship between them, so that the example gets a bit more interesting.

```kotlin
object TagGroupTable : UUIDTable() {
  val name = varchar("name", length = 255)
  val description = text("description").nullable()
}
```

```kotlin
object BaseTagTable : UUIDTable() {
  val name = varchar("name", length = 255)
  val description = text("description").nullable()

  val tagGroupId = reference("tag_group_id", TagGroupTable).nullable()
}
```

Since `GET /tag-group` would be trivial, it's boilerplate is omitted for brevity. To list all entries of `BaseTagTable` with their respective `TagGroupTable` entry, a left join is the right operation:

```kotlin
BaseTagTable
  .leftJoin(TagGroupTable, { tagGroupId }, { id })
  .selectAll()
```

The result needs to be mapped to their respective domain model instances and later on to appropriate `DTO`s, which is again too trivial to address here. One note I do want to make is that on lists like these, were the same `TagGroupTable` may be referenced multiple times by the `BaseTagTable`, it is of value to "compress" the response by not inlining these relationships into the `DTO` for the `BaseTag`, but rather to only have a `baseTagId` on it and then respond with a separate, `id`-unique list of `TagGroup` entries, as the client can easily join them on their side. The [MemberCompressedListResponseDto](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/rest/dto/MemberCompressedListResponseDto.kt) has been introduced for this exact purpose.

At this point, having fully capable filtering, sorting as well as pagination is only a few steps away. First, the library needs to know which fields are available to operate on. To keep this definition concise, the [AccessibleColumnListBuilder](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/persistence/AccessibleColumnListBuilder.kt) is a great utility to make use of. The [ResourceCursorApplicator](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/persistence/ResourceCursorApplicator.kt) makes it easy to apply a [RequestResourceCursor](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/domain/RequestResourceCursor.kt) to a `Query`, as returned by `Exposed`.

```kotlin
val cursorApplicator = ResourceCursorApplicator(
  BaseTag.DISPLAY_NAME,
  AccessibleColumnListBuilder()
    .doCamelCaseTransformNames()
    .allOf(BaseTagTable)
    .withParent(BaseTagTable.tagGroupId, "tagGroup") {
      it
        .allOf(TagGroupTable)
        .build()
    }
    .build()
)
```

This applicator can now be used on the query. Also, this time, a proper [ListResponse](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/domain/ListResponse.kt) is being returned, so that the cursor can be echoed back to the user later on.

```kotlin
fun listBaseTags(resourceCursor: RequestResourceCursor): ListResponse<BaseTag> {
  return transaction {
    cursorApplicator.applyAndMakeResponse(
      resourceCursor,
      BaseTagTable
        .leftJoin(TagGroupTable, { tagGroupId }, { id })
        .selectAll(),
      this@BaseTagPersistenceAdapter::mapBaseTag
    )
  }
}
```

The controller needs to parse the cursor from the user's request, which is then passed to the persistence adapter from above, as follows:

```kotlin
// Dependency injected singleton
private val resourceCursorService: RequestResourceCursorService

@GetMapping
fun getBaseTags(
  @Valid requestResourceCursorData: RequestResourceCursorDto
): MemberCompressedListResponseDto {
  return MemberCompressedListResponseDto.fromListResponseWithOneReferenced(
    baseTagPersistence.listBaseTags(
      resourceCursorService.parseCursorFromDto(requestResourceCursorData)
    ),
    "tagGroups",
    BaseTagRDto::fromModel,
  ) { item, map ->
    if (item.tagGroup != null)
      map[item.tagGroup!!.id] = TagGroupDto.fromModel(item.tagGroup!!)
  }
}
```

The cursor is automatically built based on the request's query parameters by spring and stored into the corresponding `DTO` instance, while the [RequestResourceCursorService](src/main/kotlin/me/blvckbytes/springexposedresourcecursor/rest/service/RequestResourceCursorService.kt) will take care of parsing the `filtering` and `sorting` strings.

That's already it, there is nothing else to take care of. The API now offers full pagination, sorting by keys, complex filtering by keys, all along a multitude of helpful responses for malformed requests. On my local project, this is what one of these responses would look like:

```json
{
    "status": "BAD_REQUEST",
    "message": "The property x does not exist on the entity base-tag. Please select from the following list only: tagGroup.id (string), tagGroup.name (string), tagGroup.description (string), tagGroup.color (long), id (string), tagType (string), name (string), description (string), icon (string), tagGroupId (string)",
    "subErrors": [],
    "timestamp": "20-11-2023 09:45:52"
}
```
