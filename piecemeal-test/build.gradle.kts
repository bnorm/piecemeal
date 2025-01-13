plugins {
  kotlin("jvm")
  java
}

val piecemealRuntimeClasspath: Configuration by configurations.creating

dependencies {
  testImplementation(project(":piecemeal-plugin"))

  testImplementation(kotlin("test-junit5"))
  testImplementation(kotlin("compiler-internal-test-framework"))
  testImplementation(kotlin("compiler"))

  piecemealRuntimeClasspath(project(":piecemeal"))

  // Dependencies required to run the internal test framework.
  testRuntimeOnly(kotlin("reflect"))
  testRuntimeOnly(kotlin("test"))
  testRuntimeOnly(kotlin("script-runtime"))
  testRuntimeOnly(kotlin("annotations-jvm"))
}

tasks.withType<Test> {
  dependsOn(piecemealRuntimeClasspath)
  inputs.dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)

  workingDir = rootDir

  useJUnitPlatform()

  systemProperty("piecemealRuntime.classpath", piecemealRuntimeClasspath.asPath)

  // Properties required to run the internal test framework.
  systemProperty("idea.ignore.disabled.plugins", "true")
  systemProperty("idea.home.path", project.rootDir)
}

tasks.create<JavaExec>("generateTests") {
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("dev.bnorm.piecemeal.GenerateTestsKt")
  workingDir = rootDir

  inputs.dir(layout.projectDirectory.dir("src/test/data"))
    .withPropertyName("testData")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.dir(layout.projectDirectory.dir("src/test/java"))
    .withPropertyName("generatedTests")
}
