package io.github.dubthree.mutantkiller.build;

/**
 * Test framework used by the project under test. Drives which imports and assertion style the
 * generated tests use, and which PIT test plugin is needed.
 */
public enum TestFramework {
    JUNIT5("JUnit 5 (Jupiter): @org.junit.jupiter.api.Test and org.junit.jupiter.api.Assertions"),
    JUNIT4("JUnit 4: @org.junit.Test and org.junit.Assert"),
    TESTNG("TestNG: @org.testng.annotations.Test and org.testng.Assert"),
    UNKNOWN("the same test framework the existing tests use");

    private final String description;

    TestFramework(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * Guess the framework from a test source file's imports.
     */
    public static TestFramework fromTestSource(String source) {
        if (source == null) {
            return UNKNOWN;
        }
        if (source.contains("org.junit.jupiter")) {
            return JUNIT5;
        }
        if (source.contains("org.testng")) {
            return TESTNG;
        }
        if (source.contains("import org.junit.Test") || source.contains("import org.junit.Assert")
            || source.contains("import static org.junit.Assert")) {
            return JUNIT4;
        }
        return UNKNOWN;
    }

    /**
     * Guess the framework from build descriptor text (pom.xml / build.gradle).
     */
    public static TestFramework fromBuildFile(String buildFile) {
        if (buildFile == null) {
            return UNKNOWN;
        }
        if (buildFile.contains("junit-jupiter") || buildFile.contains("junit-platform")
            || buildFile.contains("useJUnitPlatform")) {
            return JUNIT5;
        }
        if (buildFile.contains("testng")) {
            return TESTNG;
        }
        if (buildFile.contains("<artifactId>junit</artifactId>") || buildFile.contains("junit:junit")) {
            return JUNIT4;
        }
        return UNKNOWN;
    }
}
