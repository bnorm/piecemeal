import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Nullable(
  val value: String?,
)

fun TestNullable() {
  Nullable.build {
    value = ""
    value = null
  }
}

@Piecemeal
class Nonnull(
  val value: String,
)

fun TestNonnull() {
  Nonnull.build {
    value = ""
    value = <!NULL_FOR_NONNULL_TYPE!>null<!>
  }
}

@Piecemeal
class Primitive(
  val value: Int,
)

fun TestPrimitive() {
  Primitive.build {
    value = 1
    value = <!NULL_FOR_NONNULL_TYPE!>null<!>
  }
}
