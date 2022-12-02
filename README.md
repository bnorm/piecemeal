# piecemeal-plugin

> **Warning**
> Work in progress.

Kotlin FIR+IR plugin for generating a class builder.

```kotlin
import com.bnorm.piecemeal.Piecemeal

@Piecemeal
class Person(
    val name: String,
)

fun person(): Person {
    return Person.Builder().name("John").build()
}
```

## Inspiration

- https://github.com/ZacSweers/redacted-compiler-plugin/pull/86
