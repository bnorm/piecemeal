import dev.bnorm.piecemeal.Piecemeal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Piecemeal
class Thing(
  val id: String,
)

fun box(): String {
  val thing1 = Thing.build {
    id = "thing1"
  }

  val thing2 = thing1.copy {
    id = id.replace('1', '2')
  }

  assertEquals(thing2.id, "thing2")

  return "OK"
}
