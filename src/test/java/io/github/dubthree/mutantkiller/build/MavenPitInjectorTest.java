package io.github.dubthree.mutantkiller.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MavenPitInjectorTest {

    private static final String MINIMAL_POM = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <groupId>g</groupId>
            <artifactId>a</artifactId>
            <version>1</version>
        </project>
        """;

    @Test
    void addsPluginAndJunit5DependencyToPomWithoutBuildSection() throws Exception {
        String out = MavenPitInjector.addPlugin(MINIMAL_POM, TestFramework.JUNIT5);
        assertTrue(out.contains("<artifactId>pitest-maven</artifactId>"), out);
        assertTrue(out.contains("<version>" + MavenPitInjector.PIT_VERSION + "</version>"), out);
        assertTrue(out.contains("<artifactId>pitest-junit5-plugin</artifactId>"), out);
        assertTrue(out.contains("<outputFormat>XML</outputFormat>"), out);
        assertTrue(out.contains("<failWhenNoMutations>false</failWhenNoMutations>"), out);
        assertTrue(out.contains("xmlns=\"http://maven.apache.org/POM/4.0.0\""), out);
    }

    @Test
    void junit4NeedsNoExtraDependency() throws Exception {
        String out = MavenPitInjector.addPlugin(MINIMAL_POM, TestFramework.JUNIT4);
        assertFalse(out.contains("pitest-junit5-plugin"));
        assertFalse(out.contains("<dependencies>"));
    }

    @Test
    void appendsToExistingPluginsSection() throws Exception {
        String pom = MINIMAL_POM.replace("</project>", """
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """);
        String out = MavenPitInjector.addPlugin(pom, TestFramework.JUNIT5);
        assertEquals(1, out.split("<plugins>").length - 1);
        assertTrue(out.indexOf("maven-surefire-plugin") < out.indexOf("pitest-maven"));
    }

    @Test
    void injectAndRestoreRoundTrip(@TempDir Path dir) throws Exception {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, MINIMAL_POM);
        MavenPitInjector injector = new MavenPitInjector(pom);
        assertTrue(injector.inject(TestFramework.JUNIT5));
        assertTrue(injector.isInjected());
        assertTrue(Files.readString(pom).contains("pitest-maven"));
        injector.restore();
        assertEquals(MINIMAL_POM, Files.readString(pom));
        assertFalse(injector.isInjected());
    }

    @Test
    void doesNotTouchPomThatAlreadyHasPit(@TempDir Path dir) throws Exception {
        Path pom = dir.resolve("pom.xml");
        Files.writeString(pom, MINIMAL_POM.replace("</project>", "<build><plugins><plugin><artifactId>pitest-maven</artifactId></plugin></plugins></build></project>"));
        MavenPitInjector injector = new MavenPitInjector(pom);
        assertFalse(injector.inject(TestFramework.JUNIT5));
        assertFalse(injector.isInjected());
    }
}
