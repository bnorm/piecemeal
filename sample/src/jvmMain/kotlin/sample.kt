import com.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
) {
  override fun toString(): String {
    return "Person{name=$name,nickname=$nickname,age=$age}"
  }
}

fun main() {
  var me = Person.Builder()
    .setName("Brian")
    .build()
  println(me)

  me = me.newBuilder()
    .setAge(35)
    .build()
  println(me)
}
