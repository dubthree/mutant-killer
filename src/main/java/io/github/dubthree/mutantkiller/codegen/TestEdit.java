package io.github.dubthree.mutantkiller.codegen;

import io.github.dubthree.mutantkiller.analysis.MutantAnalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A generated test applied to disk, with enough state to undo it when it turns out not to
 * compile, fail, or leave the mutant alive.
 */
public final class TestEdit {

    private final Path file;
    private final String originalContent; // null when the file was created
    private final String newContent;
    private boolean reverted;

    private TestEdit(Path file, String originalContent, String newContent) {
        this.file = file;
        this.originalContent = originalContent;
        this.newContent = newContent;
    }

    /**
     * Write the generated code into the analysis' test file (merging into it if it exists).
     */
    public static TestEdit apply(MutantAnalysis analysis, GeneratedTest generated) throws IOException {
        Path file = analysis.testFile();
        String original = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        String updated;
        if (original != null) {
            updated = TestFileMerger.merge(original, generated);
        } else {
            updated = TestFileMerger.newTestFile(analysis.testPackage(), analysis.testSimpleName(),
                generated, analysis.testFramework());
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, updated, StandardCharsets.UTF_8);
        return new TestEdit(file, original, updated);
    }

    /**
     * Preview the result of applying without touching disk.
     */
    public static String preview(MutantAnalysis analysis, GeneratedTest generated) {
        if (analysis.existingTestCode() != null) {
            return TestFileMerger.merge(analysis.existingTestCode(), generated);
        }
        return TestFileMerger.newTestFile(analysis.testPackage(), analysis.testSimpleName(),
            generated, analysis.testFramework());
    }

    public void revert() throws IOException {
        if (reverted) {
            return;
        }
        reverted = true;
        if (originalContent == null) {
            Files.deleteIfExists(file);
        } else {
            Files.writeString(file, originalContent, StandardCharsets.UTF_8);
        }
    }

    public Path file() {
        return file;
    }

    public boolean created() {
        return originalContent == null;
    }

    public String newContent() {
        return newContent;
    }
}
