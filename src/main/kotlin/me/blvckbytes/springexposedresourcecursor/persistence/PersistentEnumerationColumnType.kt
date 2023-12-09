package me.blvckbytes.springexposedresourcecursor.persistence

import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.vendors.currentDialect
import kotlin.reflect.KClass

class PersistentEnumerationColumnType<T>(
  val type: KClass<T>
) : ColumnType() where T : Enum<T>, T : PersistentEnumeration {

  override fun sqlType(): String = currentDialect.dataTypeProvider.uintegerType()

  private val constantByIntegerValue: Map<Int, T>
  private val integerValueByName: Map<String, Int>

  init {
    val seenIntegers = HashSet<Int>()
    integerValueByName = buildMap nameMap@ {
      constantByIntegerValue = buildMap {
        for (constant in type.java.enumConstants) {
          val value = constant.integerValue

          if (value < 0)
            throw IllegalStateException("$type contained a negative integer value: ${constant.name} -> $value")

          if (!seenIntegers.add(value))
            throw IllegalStateException("$type contained a duplicate integer value: ${constant.name} -> $value")

          put(value, constant)
          this@nameMap[constant.name] = value
        }
      }
    }
  }

  fun getIntegerValueFromName(name: String): Int? {
    return integerValueByName[name]
  }

  fun getNames(): Set<String> {
    return integerValueByName.keys
  }

  @Suppress("UNCHECKED_CAST")
  override fun valueFromDB(value: Any): T = when (value) {
    is Number -> constantByIntegerValue[value.toInt()] ?: error("Value ${value.toInt()} is not defined by ${type.qualifiedName}")
    else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${type.qualifiedName}")
  }

  @Suppress("UNCHECKED_CAST")
  override fun notNullValueToDB(value: Any): Int {
    if (type.isInstance(value))
      return (value as T).integerValue

    error("$value of ${value::class.qualifiedName} is not valid for enum ${type.qualifiedName}")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as PersistentEnumerationColumnType<*>
    return type == other.type
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }
}