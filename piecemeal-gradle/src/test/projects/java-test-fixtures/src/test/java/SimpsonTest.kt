import kotlin.test.Test
import kotlin.test.assertEquals

class SimpsonTest {
  @Test
  fun testApplyMutable() {
    val simpson = Simpson.Mutable().apply {
      firstName = "Homer"
      lastName = "Simpson"
    }.build()
    assertEquals(simpson.toString(), "Simpson{firstName=Homer, lastName=Simpson}")
  }

  @Test
  fun testCompanionBuild() {
    val person = Simpson.build {
      firstName = "Marjorie"
    }
    assertEquals(person.toString(), "Simpson{firstName=Marjorie, lastName=Simpson}")
  }
}
