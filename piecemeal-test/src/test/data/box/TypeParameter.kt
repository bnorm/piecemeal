import dev.bnorm.piecemeal.Piecemeal
import kotlin.test.assertEquals

@Piecemeal
class Thing<out T>(
  val value: T,
)

fun box(): String {
  val nullable = Thing.build<Any?> {
    value = null
  }
  assertEquals(nullable.value, null)

  val str1 = Thing.build<String> {
    value = "str1"
  }
  assertEquals(str1.value, "str1")

  val i1 = Thing.build<Int> {
    value = 1
  }
  assertEquals(i1.value, 1)

  val str2 = str1.copy {
    value = "str2"
  }
  assertEquals(str2.value, "str2")

  val i2 = i1.toMutable().apply {
    value += 1
  }.build()
  assertEquals(i2.value, 2)

  return "OK"
}
