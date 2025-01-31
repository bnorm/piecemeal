import kotlin.test.Test
import kotlin.test.assertEquals

class SimpsonTest {
  @Test
  fun testApplyBuilder() {
    val simpson = Simpson.Builder().apply {
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
