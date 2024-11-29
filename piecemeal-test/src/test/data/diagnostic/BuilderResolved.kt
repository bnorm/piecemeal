// FIR_DUMP

import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
)

fun person1(): Person {
  return Person.Builder()
    .setName("John")
    .build()
}

fun person2(): Person {
  return Person.Builder().apply {
    name = "John"
  }.build()
}

fun person3(): Person {
  return Person.build {
    name = "John"
  }
}
