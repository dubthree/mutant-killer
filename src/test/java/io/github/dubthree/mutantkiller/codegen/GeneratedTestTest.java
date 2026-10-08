package io.github.dubthree.mutantkiller.codegen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedTestTest {

    @Test
    void parsesBareMethodsWithImports() {
        String reply = """
            Here is the test:

            ```java
            import static org.junit.jupiter.api.Assertions.assertEquals;
            import java.util.List;

            // boundary check
            @Test
            void addHandlesBoundary() {
                assertEquals(3, new Calculator().add(1, 2));
            }

            @Test
            void second(int x, String y) {
            }
            ```
            """;
        GeneratedTest g = GeneratedTest.parse(reply);
        assertEquals(List.of("import static org.junit.jupiter.api.Assertions.assertEquals;", "import java.util.List;"), g.imports());
        assertEquals(2, g.members().size());
        GeneratedTest.Member first = g.members().get(0);
        assertEquals("method", first.kind());
        assertEquals("addHandlesBoundary", first.name());
        assertEquals(0, first.paramCount());
        assertTrue(first.text().startsWith("// boundary check\n@Test"), first.text());
        assertTrue(first.text().endsWith("}"));
        assertEquals(2, g.members().get(1).paramCount());
        assertFalse(g.rawCode().contains("Here is"));
    }

    @Test
    void parsesWholeClassAndTakesItsMembers() {
        String reply = """
            ```java
            package com.example;

            import org.junit.Test;
            import static org.junit.Assert.assertTrue;

            public class CalculatorTest {
                private final Calculator c = new Calculator();

                @Test
                public void positive() {
                    assertTrue(c.isPositive(1));
                }
            }
            ```
            """;
        GeneratedTest g = GeneratedTest.parse(reply);
        assertEquals(2, g.imports().size());
        assertEquals(2, g.members().size());
        assertEquals("field", g.members().get(0).kind());
        assertEquals("c", g.members().get(0).name());
        assertEquals("positive", g.members().get(1).name());
        assertTrue(g.members().get(1).text().contains("@Test"));
    }

    @Test
    void emptyWhenNoCode() {
        assertTrue(GeneratedTest.parse("I cannot help with that.").isEmpty());
        assertTrue(GeneratedTest.parse("").isEmpty());
        assertTrue(GeneratedTest.parse(null).isEmpty());
    }

    @Test
    void unparseableCodeIsKeptRaw() {
        GeneratedTest g = GeneratedTest.parse("```java\n@Test void broken( {\n```");
        assertEquals(1, g.members().size());
        assertEquals("raw", g.members().get(0).kind());
    }

    @Test
    void unfencedCodeIsAccepted() {
        GeneratedTest g = GeneratedTest.parse("@Test\nvoid x() {}\n");
        assertEquals(1, g.members().size());
        assertEquals("x", g.members().get(0).name());
    }

    @Test
    void dedentRemovesCommonIndent() {
        assertEquals("a\n    b\n\nc", GeneratedTest.dedent("    a\n        b\n\n    c\n\n"));
    }
}
