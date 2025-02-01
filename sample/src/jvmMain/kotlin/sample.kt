import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = null,
  val age: Int = 0,
) {
  override fun toString(): String {
    return "Person{name=$name,nickname=$nickname,age=$age}"
  }
}

fun main() {
  var brian = Person.Mutable()
    .setName("Brian")
    .build()
  println(brian)

  brian = brian.toMutable()
    .setAge(35)
    .build()
  println(brian)

  var melinda = Person.build {
    name = "Melinda"
  }
  println(melinda)

  melinda = melinda.copy {
    age = 34
  }
  println(melinda)
}
