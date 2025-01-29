import dev.bnorm.piecemeal.Piecemeal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Piecemeal
class Test(
  val nullableValue: String?,
  val nonNullValue: String,
  val primitiveValue: Int,
)

fun box(): String {
  assertEquals(
    expected = "Uninitialized builder property 'nullableValue'.",
    actual = assertFailsWith<IllegalArgumentException> {
      Test.build {
      }
    }.message,
  )

  assertEquals(
    expected = "Uninitialized builder property 'nonNullValue'.",
    actual = assertFailsWith<IllegalArgumentException> {
      Test.build {
        nullableValue = null
      }
    }.message,
  )

  assertEquals(
    expected = "Uninitialized builder property 'primitiveValue'.",
    actual = assertFailsWith<IllegalArgumentException> {
      Test.build {
        nullableValue = null
        nonNullValue = ""
      }
    }.message,
  )

  return "OK"
}
