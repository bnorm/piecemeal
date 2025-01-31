import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class Simpson(
  val firstName: String,
  val lastName: String = "Simpson",
) {
  override fun toString(): String {
    return "Simpson{firstName=$firstName, lastName=$lastName}"
  }
}
