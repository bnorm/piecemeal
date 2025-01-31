# piecemeal-plugin

> **Warning**
> Work in progress.

Kotlin FIR+IR plugin for generating a nested `Mutable` class and associated `build` function.

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
    fun toMutable(): Mutable {
        val mutable = Mutable()
        mutable.name = name
        mutable.nickname = nickname
        mutable.age = age
        return mutable
    }

    inline fun copy(transform: Person.Mutable.() -> Unit): Person {
        val tmp = this.toMutable()
        tmp.transform
        return tmp.build()
    }

    companion object {
        @JvmSynthetic // Hide from Java callers who should use Mutable.
        fun build(builder: Person.Mutable.() -> Unit): Person {
            return Mutable().apply(builder).build()
        }
    }

    class Mutable {
        @set:JvmSynthetic // Hide 'void' setter from Java.
        var name: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var nickname: String? = null

        @set:JvmSynthetic // Hide 'void' setter from Java.
        var age: Int? = null

        fun setName(name: String?): Mutable {
            this.name = name
            return this
        }

        fun setNickname(nickname: String?): Mutable {
            this.nickname = nickname
            return this
        }

        fun setAge(age: Int): Mutable {
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

## Development

When working with compiler tests, 
it is recommended to use [Kotlin Compiler Test Helper](https://github.com/demiurg906/test-data-helper-plugin).
