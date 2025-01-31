import dev.bnorm.piecemeal.Piecemeal
import kotlin.test.assertEquals

@Piecemeal
class Test(
  val value: Int = 0,
)

fun box(): String {
  val x: Int
  Test.build {
    x = 0
  }
  assertEquals(x, 0)

  val list = sequence {
    Test.build {
      yield(0)
    }
  }.toList()
  assertEquals(list, listOf(0))

  return "OK"
}
