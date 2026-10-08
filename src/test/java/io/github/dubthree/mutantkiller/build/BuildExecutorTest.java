package io.github.dubthree.mutantkiller.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildExecutorTest {

    @Test
    void detectsMavenBeforeGradleUnlessForced(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.writeString(dir.resolve("build.gradle"), "");
        assertEquals("Maven", BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, null).name());
        assertEquals("Gradle", BuildExecutor.detect(dir, "gradle", Duration.ofMinutes(1), false, null).name());
        assertThrows(IllegalArgumentException.class, () -> BuildExecutor.detect(dir, "ant", Duration.ofMinutes(1), false, null));
    }

    @Test
    void returnsNullWithoutBuildFiles(@TempDir Path dir) {
        assertNull(BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, null));
    }

    @Test
    void findsSourceRootsAcrossModulesSkippingBuildDirs(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.createDirectories(dir.resolve("core/src/main/java"));
        Files.createDirectories(dir.resolve("core/src/test/java"));
        Files.createDirectories(dir.resolve("target/generated/src/main/java"));
        Files.createDirectories(dir.resolve("build/src/main/java"));

        BuildExecutor b = BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, null);
        assertEquals(List.of(dir.resolve("src/main/java"), dir.resolve("core/src/main/java")), b.sourceRoots());
        assertEquals(List.of(dir.resolve("core/src/test/java")), b.testRoots());
    }

    @Test
    void defaultsToConventionalRootWhenNoneExist(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        BuildExecutor b = BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, null);
        assertEquals(List.of(dir.resolve("src/test/java")), b.testRoots());
    }

    @Test
    void detectsFrameworkFromPoms(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project><modules><module>a</module></modules></project>");
        Files.createDirectories(dir.resolve("a"));
        Files.writeString(dir.resolve("a/pom.xml"), "<project><dependencies><dependency><artifactId>junit-jupiter</artifactId></dependency></dependencies></project>");
        BuildExecutor b = BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, null);
        assertEquals(TestFramework.JUNIT5, b.detectTestFramework());
    }

    @Test
    void executesCommandsAndCapturesOutput(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        BuildExecutor b = BuildExecutor.detect(dir, null, Duration.ofMinutes(1), false, dir.resolve("logs"));
        BuildResult ok = b.execute(List.of("sh", "-c", "echo hello; echo world"), "echo");
        assertTrue(ok.success());
        assertEquals("hello\nworld\n", ok.output());
        assertEquals("world", ok.tail(1));
        assertTrue(Files.exists(ok.logFile()));
        assertTrue(Files.readString(ok.logFile()).contains("hello"));

        BuildResult bad = b.execute(List.of("sh", "-c", "echo '[ERROR] boom'; exit 3"), "fail");
        assertEquals(3, bad.exitCode());
        assertEquals("[ERROR] boom", bad.errorSummary(5));
    }

    @Test
    void timesOut(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        BuildExecutor b = BuildExecutor.detect(dir, null, Duration.ofSeconds(1), false, null);
        BuildException e = assertThrows(BuildException.class, () -> b.execute(List.of("sh", "-c", "sleep 30"), "sleep"));
        assertTrue(e.getMessage().contains("timed out"));
    }

    @Test
    void frameworkDetectionFromSources() {
        assertEquals(TestFramework.JUNIT5, TestFramework.fromTestSource("import org.junit.jupiter.api.Test;"));
        assertEquals(TestFramework.JUNIT4, TestFramework.fromTestSource("import org.junit.Test;"));
        assertEquals(TestFramework.TESTNG, TestFramework.fromTestSource("import org.testng.annotations.Test;"));
        assertEquals(TestFramework.UNKNOWN, TestFramework.fromTestSource("class X {}"));
        assertEquals(TestFramework.JUNIT4, TestFramework.fromBuildFile("testImplementation 'junit:junit:4.13.2'"));
        assertEquals(TestFramework.JUNIT5, TestFramework.fromBuildFile("test { useJUnitPlatform() }"));
    }
}
