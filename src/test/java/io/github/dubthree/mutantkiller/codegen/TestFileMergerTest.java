package io.github.dubthree.mutantkiller.codegen;

import io.github.dubthree.mutantkiller.build.TestFramework;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFileMergerTest {

    private static final String EXISTING = """
        package com.example;

        import org.junit.jupiter.api.Test;
        import static org.junit.jupiter.api.Assertions.assertEquals;

        class CalculatorTest {

            private final Calculator calc = new Calculator();

            /** adds */
            @Test
            void add() {
                assertEquals(2, calc.add(1, 1));
            }
        }
        """;

    private static GeneratedTest gen(String imports, String member) {
        return GeneratedTest.parse("```java\n" + imports + "\n" + member + "\n```");
    }

    @Test
    void appendsNewMethodBeforeClosingBraceWithIndent() {
        GeneratedTest g = gen("import static org.junit.jupiter.api.Assertions.assertTrue;",
            "@Test\nvoid isPositive() {\n    assertTrue(calc.isPositive(1));\n}");
        String merged = TestFileMerger.merge(EXISTING, g);

        String expectedTail = """
                @Test
                void add() {
                    assertEquals(2, calc.add(1, 1));
                }

                @Test
                void isPositive() {
                    assertTrue(calc.isPositive(1));
                }
            }
            """;
        assertTrue(merged.endsWith(expectedTail), merged);
        // import added right after the last existing import
        assertTrue(merged.contains("import static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n"), merged);
        // untouched prefix
        assertTrue(merged.startsWith("package com.example;\n\nimport org.junit.jupiter.api.Test;"));
    }

    @Test
    void replacesMethodWithSameNameAndArityIncludingItsComment() {
        GeneratedTest g = gen("", "@Test\nvoid add() {\n    assertEquals(3, calc.add(1, 2));\n}");
        String merged = TestFileMerger.merge(EXISTING, g);
        assertFalse(merged.contains("/** adds */"), merged);
        assertFalse(merged.contains("calc.add(1, 1)"), merged);
        assertTrue(merged.contains("    @Test\n    void add() {\n        assertEquals(3, calc.add(1, 2));\n    }\n}"), merged);
        assertEquals(1, merged.split("void add\\(").length - 1);
    }

    @Test
    void skipsImportsAlreadyPresentOrCoveredByWildcard() {
        String existing = EXISTING.replace("import static org.junit.jupiter.api.Assertions.assertEquals;",
            "import static org.junit.jupiter.api.Assertions.*;");
        GeneratedTest g = gen("import static org.junit.jupiter.api.Assertions.assertTrue;\nimport org.junit.jupiter.api.Test;",
            "@Test\nvoid x() {}");
        String merged = TestFileMerger.merge(existing, g);
        assertFalse(merged.contains("assertTrue;"), merged);
        assertEquals(1, merged.split("import org.junit.jupiter.api.Test;").length - 1);
    }

    @Test
    void preservesCrlfLineEndings() {
        String crlf = EXISTING.replace("\n", "\r\n");
        GeneratedTest g = gen("", "@Test\nvoid x() {}");
        String merged = TestFileMerger.merge(crlf, g);
        assertTrue(merged.contains("\r\n    @Test\r\n    void x() {}\r\n}"), merged);
        assertFalse(merged.replace("\r\n", "").contains("\n"));
    }

    @Test
    void fallsBackToLastBraceWhenFileDoesNotParse() {
        String broken = "class Broken {\n    void a( {\n}\n";
        GeneratedTest g = gen("import java.util.List;", "@Test\nvoid x() {}");
        String merged = TestFileMerger.merge(broken, g);
        assertTrue(merged.startsWith("import java.util.List;\n\nclass Broken {"), merged);
        assertTrue(merged.endsWith("    @Test\n    void x() {}\n}\n"), merged);
    }

    @Test
    void newFileUsesFrameworkImports() {
        GeneratedTest g = gen("import java.util.List;", "@Test\npublic void x() {\n    assertTrue(true);\n}");
        String junit4 = TestFileMerger.newTestFile("com.example", "FooTest", g, TestFramework.JUNIT4);
        assertTrue(junit4.startsWith("package com.example;\n\nimport org.junit.Test;\nimport static org.junit.Assert.*;\nimport java.util.List;\n"), junit4);
        assertTrue(junit4.contains("public class FooTest {\n\n    @Test\n    public void x() {\n        assertTrue(true);\n    }\n}\n"), junit4);

        String junit5 = TestFileMerger.newTestFile("", "FooTest", g, TestFramework.JUNIT5);
        assertFalse(junit5.contains("package"));
        assertTrue(junit5.contains("import org.junit.jupiter.api.Test;"));
    }

    @Test
    void closingBraceSharingALineIsSplit() {
        String existing = "class T { void a() {} }";
        GeneratedTest g = gen("", "void b() {}");
        String merged = TestFileMerger.merge(existing, g);
        assertEquals("class T { void a() {} \n\n    void b() {}\n}", merged);
    }
}
