

package dev.bnorm.piecemeal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link dev.bnorm.piecemeal.GenerateTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("piecemeal-test/src/test/data/box")
@TestDataPath("$PROJECT_ROOT")
public class BoxTestGenerated extends AbstractBoxTest {
  @Test
  public void testAllFilesPresentInBox() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("piecemeal-test/src/test/data/box"), Pattern.compile("^(.+)\\.kt$"), null, TargetBackend.JVM_IR, true);
  }

  @Test
  @TestMetadata("BuildException.kt")
  public void testBuildException() {
    runTest("piecemeal-test/src/test/data/box/BuildException.kt");
  }

  @Test
  @TestMetadata("Builder.kt")
  public void testBuilder() {
    runTest("piecemeal-test/src/test/data/box/Builder.kt");
  }

  @Test
  @TestMetadata("BuilderWithSetters.kt")
  public void testBuilderWithSetters() {
    runTest("piecemeal-test/src/test/data/box/BuilderWithSetters.kt");
  }

  @Test
  @TestMetadata("InlineBuild.kt")
  public void testInlineBuild() {
    runTest("piecemeal-test/src/test/data/box/InlineBuild.kt");
  }
}
