package io.github.dubthree.mutantkiller.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.dubthree.mutantkiller.build.TestFramework;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Locates the source and test files for a mutation and extracts the context the model needs.
 * <p>
 * Handles inner classes ({@code Foo$Bar} lives in {@code Foo.java}), classes in the default
 * package, multi-module projects (several source roots), and test classes that live in a
 * different package than the class under test (common in older projects).
 */
public class MutantAnalyzer {

    private static final Pattern PACKAGE_DECL = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final int CONTEXT_LINES = 8;

    private final List<Path> sourceRoots;
    private final List<Path> testRoots;
    private final TestFramework defaultFramework;
    private final JavaParser parser;

    public MutantAnalyzer(List<Path> sourceRoots, List<Path> testRoots, TestFramework defaultFramework) {
        this.sourceRoots = sourceRoots;
        this.testRoots = testRoots;
        this.defaultFramework = defaultFramework == null ? TestFramework.UNKNOWN : defaultFramework;
        ParserConfiguration cfg = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        this.parser = new JavaParser(cfg);
    }

    public MutantAnalysis analyze(MutationResult mutation) throws IOException {
        Path sourceFile = findSourceFile(mutation);
        if (sourceFile == null) {
            throw new IOException("Could not find source file for " + mutation.outerClass()
                + " under " + sourceRoots);
        }
        String sourceCode = Files.readString(sourceFile);
        String methodSource = extractMethod(sourceCode, mutation);
        String context = extractContext(sourceCode, mutation.lineNumber());

        Path testFile = findTestFile(mutation);
        String existingTestCode = null;
        String testClassFqn;
        TestFramework framework = defaultFramework;
        if (testFile != null) {
            existingTestCode = Files.readString(testFile);
            testClassFqn = fqnOf(testFile, existingTestCode);
            TestFramework fromSource = TestFramework.fromTestSource(existingTestCode);
            if (fromSource != TestFramework.UNKNOWN) {
                framework = fromSource;
            }
        } else {
            Path testRoot = testRootFor(sourceFile);
            String pkg = mutation.packageName();
            Path dir = pkg.isEmpty() ? testRoot : testRoot.resolve(pkg.replace('.', '/'));
            String simple = mutation.simpleOuterClassName() + "Test";
            testFile = dir.resolve(simple + ".java");
            testClassFqn = pkg.isEmpty() ? simple : pkg + "." + simple;
        }

        return new MutantAnalysis(mutation, sourceFile, sourceCode, methodSource, context,
            testFile, testClassFqn, existingTestCode, framework);
    }

    Path findSourceFile(MutationResult mutation) {
        String relative = mutation.outerClass().replace('.', '/') + ".java";
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    Path findTestFile(MutationResult mutation) {
        String simple = mutation.simpleOuterClassName();
        List<String> names = List.of(simple + "Test", simple + "Tests", "Test" + simple, simple + "TestCase");
        String pkgPath = mutation.packageName().replace('.', '/');

        // 1. Same package, conventional name.
        for (Path root : testRoots) {
            Path dir = pkgPath.isEmpty() ? root : root.resolve(pkgPath);
            for (String name : names) {
                Path candidate = dir.resolve(name + ".java");
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        // 2. Anywhere under the test roots (tests in another package).
        List<Path> matches = new ArrayList<>();
        for (Path root : testRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        return fn.endsWith(".java") && names.contains(fn.substring(0, fn.length() - 5));
                    })
                    .forEach(matches::add);
            } catch (IOException ignored) {
                // skip unreadable root
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        // Prefer the conventional name, then the shortest path.
        matches.sort((a, b) -> {
            int ia = names.indexOf(stripJava(a));
            int ib = names.indexOf(stripJava(b));
            if (ia != ib) {
                return Integer.compare(ia, ib);
            }
            return Integer.compare(a.getNameCount(), b.getNameCount());
        });
        return matches.get(0);
    }

    private static String stripJava(Path p) {
        String fn = p.getFileName().toString();
        return fn.substring(0, fn.length() - 5);
    }

    /**
     * The test root belonging to the same module as {@code sourceFile}, or the first root.
     */
    private Path testRootFor(Path sourceFile) {
        for (Path src : sourceRoots) {
            if (sourceFile.startsWith(src)) {
                Path module = src.getParent() != null && src.getParent().getParent() != null
                    ? src.getParent().getParent().getParent() : null;
                if (module != null) {
                    for (Path test : testRoots) {
                        if (test.startsWith(module) && test.getNameCount() == src.getNameCount()) {
                            return test;
                        }
                    }
                    return module.resolve("src").resolve("test").resolve("java");
                }
            }
        }
        return testRoots.isEmpty() ? Path.of("src/test/java") : testRoots.get(0);
    }

    static String fqnOf(Path file, String content) {
        String simple = stripJava(file);
        Matcher m = PACKAGE_DECL.matcher(content);
        if (m.find()) {
            return m.group(1) + "." + simple;
        }
        return simple;
    }

    String extractMethod(String sourceCode, MutationResult mutation) {
        ParseResult<CompilationUnit> result;
        try {
            result = parser.parse(sourceCode);
        } catch (Exception e) {
            return null;
        }
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return null;
        }
        CompilationUnit cu = result.getResult().get();
        int line = mutation.lineNumber();
        String name = mutation.mutatedMethod();

        List<CallableDeclaration<?>> candidates = new ArrayList<>();
        if ("<init>".equals(name)) {
            candidates.addAll(cu.findAll(ConstructorDeclaration.class));
        } else if (!"<clinit>".equals(name)) {
            cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(name))
                .forEach(candidates::add);
        }
        // Prefer the overload whose body spans the mutated line.
        Optional<CallableDeclaration<?>> containing = candidates.stream()
            .filter(c -> c.getRange().map(r -> r.begin.line <= line && line <= r.end.line).orElse(false))
            .findFirst();
        CallableDeclaration<?> chosen = containing.orElse(candidates.isEmpty() ? null : candidates.get(0));
        if (chosen == null || chosen.getRange().isEmpty()) {
            return null;
        }
        // Slice the original text so formatting is preserved.
        String[] lines = sourceCode.split("\n", -1);
        int begin = chosen.getRange().get().begin.line - 1;
        int end = Math.min(lines.length, chosen.getRange().get().end.line);
        StringBuilder sb = new StringBuilder();
        for (int i = begin; i < end; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    static String extractContext(String sourceCode, int lineNumber) {
        String[] lines = sourceCode.split("\n", -1);
        int mutationLine = lineNumber - 1;
        int start = Math.max(0, mutationLine - CONTEXT_LINES);
        int end = Math.min(lines.length, mutationLine + CONTEXT_LINES + 1);
        StringBuilder context = new StringBuilder();
        for (int i = start; i < end; i++) {
            String prefix = (i == mutationLine) ? ">>> " : "    ";
            context.append(String.format("%s%4d: %s%n", prefix, i + 1, lines[i]));
        }
        return context.toString();
    }
}
