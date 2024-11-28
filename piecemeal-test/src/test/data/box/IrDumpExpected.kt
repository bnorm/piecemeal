// DUMP_IR

fun box() = "OK"

class Person private constructor(
  val name: String,
  val nickname: String? = name,
  val age: Int = 0,
) {
  fun newBuilder(): Builder {
    val builder = Builder()
    builder.name = name
    builder.nickname = nickname
    builder.age = age
    return builder
  }

  class Builder {
    @set:JvmSynthetic // Hide 'void' setter from Java.
    var name: String? = null

    @set:JvmSynthetic // Hide 'void' setter from Java.
    var nickname: String? = null

    @set:JvmSynthetic // Hide 'void' setter from Java.
    var age: Int? = null

    fun build(): Person {
      val name = name ?: throw IllegalStateException("Missing required parameter 'name'.")
      val nickname = nickname ?: name
      val age = age ?: 0
      return Person(
        name = name,
        nickname = nickname,
        age = age,
      )
    }

    fun setName(name: String): Builder {
      this.name = name
      return this
    }

    fun setNickname(nickname: String?): Builder {
      this.nickname = nickname
      return this
    }

    fun setAge(age: Int): Builder {
      this.age = age
      return this
    }
  }
}

@JvmSynthetic // Hide from Java callers who should use Builder.
fun Person(builder: Person.Builder.() -> Unit): Person {
  val tmp = Person.Builder()
  builder.invoke(tmp)
  return tmp.build()
}
