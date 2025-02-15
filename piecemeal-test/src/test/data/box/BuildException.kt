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
    expected = "Uninitialized property 'nullableValue'.",
    actual = assertFailsWith<IllegalStateException> {
      Test.build {
      }
    }.message,
  )

  assertEquals(
    expected = "Uninitialized property 'nonNullValue'.",
    actual = assertFailsWith<IllegalStateException> {
      Test.build {
        nullableValue = null
      }
    }.message,
  )

  assertEquals(
    expected = "Uninitialized property 'primitiveValue'.",
    actual = assertFailsWith<IllegalStateException> {
      Test.build {
        nullableValue = null
        nonNullValue = ""
      }
    }.message,
  )

  return "OK"
}
