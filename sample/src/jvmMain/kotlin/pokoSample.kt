import dev.bnorm.piecemeal.Piecemeal
import dev.drewhamilton.poko.Poko

/**
 * Piecemeal can be used with Poko to create a data API that can be safely evolved over time.
 */
@Piecemeal
@Poko class Place(
  val name: String,
  val type: Type,
) {
  enum class Type {
    City, State,
  }
}

fun main() {
  val chicago1 = Place.build {
    name = "Chicago"
    type = Place.Type.City
  }
  val chicago2 = Place.Builder()
    .setName("Chicago")
    .setType(Place.Type.City)
    .build()

  check(chicago1 == chicago2) // Uses Poko-generated equals
  println(chicago1) // Uses Poko-generated toString

}
