

package dev.bnorm.piecemeal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link dev.bnorm.piecemeal.GenerateTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("piecemeal-test/src/test/data/diagnostic")
@TestDataPath("$PROJECT_ROOT")
public class DiagnosticTestGenerated extends AbstractDiagnosticTest {
  @Test
  public void testAllFilesPresentInDiagnostic() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("piecemeal-test/src/test/data/diagnostic"), Pattern.compile("^(.+)\\.kt$"), null, true);
  }

  @Test
  @TestMetadata("BuilderPropertyTypes.kt")
  public void testBuilderPropertyTypes() {
    runTest("piecemeal-test/src/test/data/diagnostic/BuilderPropertyTypes.kt");
  }

  @Test
  @TestMetadata("BuilderResolved.kt")
  public void testBuilderResolved() {
    runTest("piecemeal-test/src/test/data/diagnostic/BuilderResolved.kt");
  }

  @Test
  @TestMetadata("NoPrimaryConstructor.kt")
  public void testNoPrimaryConstructor() {
    runTest("piecemeal-test/src/test/data/diagnostic/NoPrimaryConstructor.kt");
  }

  @Test
  @TestMetadata("PrimaryConstructorVisibility.kt")
  public void testPrimaryConstructorVisibility() {
    runTest("piecemeal-test/src/test/data/diagnostic/PrimaryConstructorVisibility.kt");
  }
}
