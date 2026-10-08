package io.github.dubthree.mutantkiller.codegen;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.github.dubthree.mutantkiller.build.TestFramework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merges generated members into an existing test file using line-based edits, so the rest of
 * the file keeps its formatting and the resulting diff is only the new code.
 */
public final class TestFileMerger {

    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+.*;\\s*$");
    private static final Pattern PACKAGE_LINE = Pattern.compile("^\\s*package\\s+.*;\\s*$");

    private TestFileMerger() {
    }

    /**
     * Merge {@code generated} into {@code existing} and return the new file content. Methods
     * with the same name and arity as an existing method replace it; everything else is
     * appended at the end of the primary class.
     */
    public static String merge(String existing, GeneratedTest generated) {
        List<String> lines = new ArrayList<>(Arrays.asList(existing.split("\n", -1)));
        boolean crlf = existing.contains("\r\n");
        if (crlf) {
            lines.replaceAll(l -> l.endsWith("\r") ? l.substring(0, l.length() - 1) : l);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        ParseResult<CompilationUnit> result = parser.parse(existing);
        TypeDeclaration<?> type = null;
        if (result.isSuccessful() && result.getResult().isPresent()) {
            CompilationUnit cu = result.getResult().get();
            type = cu.getPrimaryType().orElse(cu.getTypes().isEmpty() ? null : cu.getType(0));
        }

        String indent = "    ";
        int classEndLine; // 1-based line holding the class's closing brace
        List<int[]> replacements = new ArrayList<>(); // {beginLine, endLine, memberIndex}
        List<GeneratedTest.Member> toAppend = new ArrayList<>();

        if (type != null && type.getRange().isPresent()) {
            classEndLine = type.getRange().get().end.line;
            indent = detectIndent(type, lines);
            for (int i = 0; i < generated.members().size(); i++) {
                GeneratedTest.Member m = generated.members().get(i);
                MethodDeclaration existingMethod = m.isMethod() ? findMethod(type, m) : null;
                if (existingMethod != null && existingMethod.getRange().isPresent()) {
                    int begin = existingMethod.getRange().get().begin.line;
                    if (existingMethod.getComment().isPresent()
                            && existingMethod.getComment().get().getRange().isPresent()) {
                        begin = Math.min(begin, existingMethod.getComment().get().getRange().get().begin.line);
                    }
                    replacements.add(new int[]{begin, existingMethod.getRange().get().end.line, i});
                } else {
                    toAppend.add(m);
                }
            }
        } else {
            // Unparseable file: append before the last closing brace.
            classEndLine = lastLineContaining(lines, "}");
            toAppend.addAll(generated.members());
        }

        // Apply replacements bottom-up so earlier line numbers stay valid.
        replacements.sort((a, b) -> Integer.compare(b[0], a[0]));
        for (int[] r : replacements) {
            List<String> replacement = indentLines(generated.members().get(r[2]).text(), indent);
            for (int line = r[1]; line >= r[0]; line--) {
                lines.remove(line - 1);
            }
            lines.addAll(r[0] - 1, replacement);
            if (r[1] < classEndLine) {
                classEndLine += replacement.size() - (r[1] - r[0] + 1);
            }
        }

        // Append new members just before the closing brace.
        if (!toAppend.isEmpty()) {
            List<String> block = new ArrayList<>();
            for (GeneratedTest.Member m : toAppend) {
                block.add("");
                block.addAll(indentLines(m.text(), indent));
            }
            int idx = classEndLine - 1;
            String closing = lines.get(idx);
            if (closing.strip().equals("}")) {
                lines.addAll(idx, block);
            } else {
                // Closing brace shares its line with code: split the line at the last brace.
                int brace = closing.lastIndexOf('}');
                String before = closing.substring(0, brace);
                String after = closing.substring(brace);
                lines.set(idx, before);
                lines.addAll(idx + 1, block);
                lines.add(idx + 1 + block.size(), after);
            }
        }

        // Imports.
        Set<String> present = new LinkedHashSet<>();
        int lastImport = -1;
        int packageLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (IMPORT_LINE.matcher(l).matches()) {
                present.add(normalizeImport(l));
                lastImport = i;
            } else if (PACKAGE_LINE.matcher(l).matches()) {
                packageLine = i;
            }
        }
        List<String> newImports = new ArrayList<>();
        for (String imp : generated.imports()) {
            if (!present.contains(normalizeImport(imp)) && !coveredByWildcard(present, imp)) {
                newImports.add(imp.strip());
                present.add(normalizeImport(imp));
            }
        }
        if (!newImports.isEmpty()) {
            if (lastImport >= 0) {
                lines.addAll(lastImport + 1, newImports);
            } else if (packageLine >= 0) {
                List<String> block = new ArrayList<>();
                block.add("");
                block.addAll(newImports);
                lines.addAll(packageLine + 1, block);
            } else {
                List<String> block = new ArrayList<>(newImports);
                block.add("");
                lines.addAll(0, block);
            }
        }

        String nl = crlf ? "\r\n" : "\n";
        return String.join(nl, lines);
    }

    /**
     * Create a brand new test class holding the generated members.
     */
    public static String newTestFile(String packageName, String simpleName, GeneratedTest generated,
                                     TestFramework framework) {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        Set<String> imports = new LinkedHashSet<>();
        switch (framework) {
            case JUNIT4 -> {
                imports.add("import org.junit.Test;");
                imports.add("import static org.junit.Assert.*;");
            }
            case TESTNG -> {
                imports.add("import org.testng.annotations.Test;");
                imports.add("import static org.testng.Assert.*;");
            }
            default -> {
                imports.add("import org.junit.jupiter.api.Test;");
                imports.add("import static org.junit.jupiter.api.Assertions.*;");
            }
        }
        for (String imp : generated.imports()) {
            imports.add(imp.strip());
        }
        for (String imp : imports) {
            sb.append(imp).append('\n');
        }
        sb.append("\n/**\n * Tests generated by mutant-killer to improve mutation coverage.\n */\n");
        sb.append("public class ").append(simpleName).append(" {\n");
        for (GeneratedTest.Member m : generated.members()) {
            sb.append('\n');
            for (String l : indentLines(m.text(), "    ")) {
                sb.append(l).append('\n');
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static MethodDeclaration findMethod(TypeDeclaration<?> type, GeneratedTest.Member m) {
        for (MethodDeclaration existing : type.getMethods()) {
            if (existing.getNameAsString().equals(m.name()) && existing.getParameters().size() == m.paramCount()) {
                return existing;
            }
        }
        return null;
    }

    private static String detectIndent(TypeDeclaration<?> type, List<String> lines) {
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member.getRange().isPresent()) {
                int line = member.getRange().get().begin.line - 1;
                if (line >= 0 && line < lines.size()) {
                    String l = lines.get(line);
                    int n = 0;
                    while (n < l.length() && (l.charAt(n) == ' ' || l.charAt(n) == '\t')) {
                        n++;
                    }
                    if (n > 0) {
                        return l.substring(0, n);
                    }
                }
            }
        }
        return "    ";
    }

    private static int lastLineContaining(List<String> lines, String needle) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains(needle)) {
                return i + 1;
            }
        }
        return lines.size();
    }

    static List<String> indentLines(String text, String indent) {
        List<String> out = new ArrayList<>();
        for (String l : GeneratedTest.dedent(text).split("\n", -1)) {
            out.add(l.isBlank() ? "" : indent + l);
        }
        return out;
    }

    static String normalizeImport(String line) {
        return line.strip().replaceAll("\\s+", " ").replace(" ;", ";");
    }

    private static boolean coveredByWildcard(Set<String> present, String imp) {
        Matcher m = Pattern.compile("import\\s+(static\\s+)?([\\w.]+)\\.(\\w+)\\s*;").matcher(normalizeImport(imp));
        if (!m.find()) {
            return false;
        }
        String wildcard = "import " + (m.group(1) == null ? "" : "static ") + m.group(2) + ".*;";
        return present.contains(wildcard);
    }
}
