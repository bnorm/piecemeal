// FIR_DUMP
// ENABLE_JAVA_SETTERS

import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
)

fun person1(): Person {
  return Person.Mutable()
    .setName("John")
    .build()
}

fun person2(): Person {
  return Person.Mutable().apply {
    name = "John"
  }.build()
}

fun person3(): Person {
  return Person.build {
    name = "John"
  }
}
