# piecemeal-plugin

> **Warning**
> Work in progress.

Kotlin FIR+IR plugin for generating a class builder.

```kotlin
import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
    val name: String,
    val nickname: String? = name,
    val age: Int = 0,
)
```

The above example will generate the following

```kotlin
@Piecemeal
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
    
    companion object {
        @JvmSynthetic // Hide from Java callers who should use Builder.
        fun build(builder: Person.Builder.() -> Unit): Person {
            return Builder().apply(builder).build()
        }
    }

    class Builder {
        @set:JvmSynthetic // Hide 'void' setter from Java.
        var name: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var nickname: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var age: Int? = null

        fun setName(name: String?): Builder {
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

        fun build(): Person {
            val name = name ?: throw IllegalStateException("Missing required parameter 'name'.")
            val nickname = nickname ?: name
            val age = age ?: 0

            return Person(
                name = name,
                nickname = nickname ?: name,
                age = age,
            )
        }
    }
}
```

## Various References

- https://jakewharton.com/public-api-challenges-in-kotlin/
- https://github.com/ZacSweers/redacted-compiler-plugin/pull/86
