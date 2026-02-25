package io.github.dubthree.mutantkiller.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.dubthree.mutantkiller.config.MutantKillerConfig;
import io.github.dubthree.mutantkiller.pit.MutationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Analyzes mutations to understand what code was mutated and why tests didn't catch it.
 */
public class MutantAnalyzer {

    private final MutantKillerConfig config;

    public MutantAnalyzer(MutantKillerConfig config) {
        this.config = config;
    }

    /**
     * Analyze a surviving mutation and gather context for test improvement.
     */
    public MutantAnalysis analyze(MutationResult mutation) throws IOException {
        // Find source file
        Path sourceFile = findSourceFile(mutation.mutatedClass());
        if (sourceFile == null) {
            throw new IOException("Could not find source file for: " + mutation.mutatedClass());
        }

        // Parse source
        String sourceCode = Files.readString(sourceFile);
        CompilationUnit cu = StaticJavaParser.parse(sourceCode);

        // Find the mutated method
        Optional<MethodDeclaration> method = cu.findAll(MethodDeclaration.class).stream()
            .filter(m -> m.getNameAsString().equals(mutation.mutatedMethod()))
            .findFirst();

        // Extract context around the mutation line
        String[] lines = sourceCode.split("\n");
        int mutationLine = mutation.lineNumber() - 1; // 0-indexed
        int contextStart = Math.max(0, mutationLine - 5);
        int contextEnd = Math.min(lines.length, mutationLine + 6);
        
        StringBuilder context = new StringBuilder();
        for (int i = contextStart; i < contextEnd; i++) {
            String prefix = (i == mutationLine) ? ">>> " : "    ";
            context.append(String.format("%s%4d: %s%n", prefix, i + 1, lines[i]));
        }

        // Find existing test file
        Path testFile = findTestFile(mutation.mutatedClass());
        String existingTestCode = testFile != null ? Files.readString(testFile) : null;

        return new MutantAnalysis(
            mutation,
            sourceFile,
            sourceCode,
            method.map(MethodDeclaration::toString).orElse(null),
            context.toString(),
            testFile,
            existingTestCode
        );
    }

    private Path findSourceFile(String className) {
        String relativePath = className.replace('.', '/') + ".java";
        Path candidate = config.sourceDir().resolve(relativePath);
        if (Files.exists(candidate)) {
            return candidate;
        }
        // Try without package prefix variations
        return null;
    }

    private Path findTestFile(String className) {
        int lastDot = className.lastIndexOf('.');
        String simpleClassName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
        String testClassName = simpleClassName + "Test";

        if (lastDot >= 0) {
            String packagePath = className.substring(0, lastDot).replace('.', '/');
            Path candidate = config.testDir().resolve(packagePath).resolve(testClassName + ".java");
            if (Files.exists(candidate)) {
                return candidate;
            }
        } else {
            // Default package
            Path candidate = config.testDir().resolve(testClassName + ".java");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
