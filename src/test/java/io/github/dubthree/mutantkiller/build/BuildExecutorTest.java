package io.github.dubthree.mutantkiller.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildExecutorTest {

    @Test
    void detect_maven(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("pom.xml"), "<project/>");

        BuildExecutor executor = BuildExecutor.detect(tmp);

        assertNotNull(executor);
        assertEquals("Maven", executor.name());
        assertEquals(tmp.resolve("src/main/java"), executor.sourceDir());
        assertEquals(tmp.resolve("src/test/java"), executor.testDir());
    }

    @Test
    void detect_gradle(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("build.gradle"), "plugins {}");

        BuildExecutor executor = BuildExecutor.detect(tmp);

        assertNotNull(executor);
        assertEquals("Gradle", executor.name());
        assertEquals(tmp.resolve("src/main/java"), executor.sourceDir());
        assertEquals(tmp.resolve("src/test/java"), executor.testDir());
    }

    @Test
    void detect_gradleKts(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("build.gradle.kts"), "plugins {}");

        BuildExecutor executor = BuildExecutor.detect(tmp);

        assertNotNull(executor);
        assertEquals("Gradle", executor.name());
    }

    @Test
    void detect_mavenPreferredOverGradle(@TempDir Path tmp) throws IOException {
        // Both present - Maven wins (checked first)
        Files.writeString(tmp.resolve("pom.xml"), "<project/>");
        Files.writeString(tmp.resolve("build.gradle"), "plugins {}");

        BuildExecutor executor = BuildExecutor.detect(tmp);

        assertNotNull(executor);
        assertEquals("Maven", executor.name());
    }

    @Test
    void detect_noBuildSystem(@TempDir Path tmp) {
        BuildExecutor executor = BuildExecutor.detect(tmp);
        assertNull(executor);
    }

    @Test
    void detect_emptyDir(@TempDir Path tmp) {
        assertNull(BuildExecutor.detect(tmp));
    }
}
