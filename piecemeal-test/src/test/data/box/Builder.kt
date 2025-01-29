// DUMP_IR

import dev.bnorm.piecemeal.Piecemeal

fun box() = "OK"

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
)
