// DUMP_IR

import com.bnorm.piecemeal.Piecemeal

fun box() = "OK"

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
) {
  fun mutate(block: Person.() -> Unit) {}
}