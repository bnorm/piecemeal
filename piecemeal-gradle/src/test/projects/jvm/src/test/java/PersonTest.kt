import kotlin.test.Test
import kotlin.test.assertEquals

class PersonTest {
  @Test
  fun testApplyBuilder() {
    val person = Person.Builder().apply {
      name = "Sam"
    }.build()
    assertEquals(person.toString(), "Person{name=Sam, nickname=Sam, age=0}")
  }

  @Test
  fun testCompanionBuild() {
    val person = Person.build {
      name = "Sam"
    }
    assertEquals(person.toString(), "Person{name=Sam, nickname=Sam, age=0}")
  }
}
