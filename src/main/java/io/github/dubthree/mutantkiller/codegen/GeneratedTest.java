package io.github.dubthree.mutantkiller.codegen;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The model's reply, parsed into imports and class members so they can be merged into an
 * existing test class without rewriting the rest of the file.
 *
 * @param imports import statements, e.g. {@code import static org.junit.Assert.assertEquals;}
 * @param members methods, fields, nested types and initializers to add to the test class
 * @param rawCode the code block as extracted from the reply, for display
 */
public record GeneratedTest(List<String> imports, List<Member> members, String rawCode) {

    /**
     * One class member.
     *
     * @param kind       {@code method}, {@code constructor}, {@code field}, {@code type}, {@code initializer} or {@code raw}
     * @param name       method/field/type name, or null
     * @param paramCount parameter count for methods, else -1
     * @param text       source text, de-indented
     */
    public record Member(String kind, String name, int paramCount, String text) {
        public boolean isMethod() {
            return "method".equals(kind);
        }
    }

    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:java)?[ \\t]*\\r?\\n(.*?)\\r?\\n?```", Pattern.DOTALL);
    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+(?:static\\s+)?[\\w.*]+\\s*;\\s*$", Pattern.MULTILINE);
    private static final Pattern PACKAGE_LINE = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;\\s*$", Pattern.MULTILINE);
    private static final Pattern TYPE_DECL = Pattern.compile(
        "^(?:\\s*@\\w+(?:\\([^)]*\\))?\\s*)*(?:(?:public|final|abstract|static)\\s+)*(?:class|interface|enum|record)\\s+\\w+",
        Pattern.MULTILINE);

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public String methodNames() {
        return members.stream().filter(Member::isMethod).map(Member::name).toList().toString();
    }

    /**
     * Parse an LLM reply. Code is taken from fenced blocks; if there are none and the text
     * looks like Java, the whole reply is used.
     */
    public static GeneratedTest parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return new GeneratedTest(List.of(), List.of(), "");
        }
        String code = extractCode(reply);
        if (code.isBlank()) {
            return new GeneratedTest(List.of(), List.of(), "");
        }

        Set<String> imports = new LinkedHashSet<>();
        Matcher im = IMPORT_LINE.matcher(code);
        while (im.find()) {
            imports.add(im.group().strip().replaceAll("\\s+", " ").replace(" ;", ";"));
        }
        String body = IMPORT_LINE.matcher(code).replaceAll("");
        body = PACKAGE_LINE.matcher(body).replaceAll("").strip();

        List<Member> members = new ArrayList<>();
        JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));

        if (TYPE_DECL.matcher(body).find() && looksLikeWholeClass(body)) {
            ParseResult<CompilationUnit> result = parser.parse(body);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                cu.getImports().forEach(i -> imports.add(i.toString().strip()));
                TypeDeclaration<?> type = cu.getPrimaryType().orElse(
                    cu.getTypes().isEmpty() ? null : cu.getType(0));
                if (type != null) {
                    for (BodyDeclaration<?> member : type.getMembers()) {
                        members.add(toMember(member, member.toString()));
                    }
                }
                return new GeneratedTest(List.copyOf(imports), members, code.strip());
            }
        }

        // Bare members: wrap in a class so they parse, then slice the original text by line.
        String wrapped = "class __MutantKiller__ {\n" + body + "\n}\n";
        ParseResult<CompilationUnit> result = parser.parse(wrapped);
        if (result.isSuccessful() && result.getResult().isPresent()
                && !result.getResult().get().getTypes().isEmpty()) {
            String[] bodyLines = body.split("\n", -1);
            TypeDeclaration<?> type = result.getResult().get().getType(0);
            for (BodyDeclaration<?> member : type.getMembers()) {
                if (member.getRange().isEmpty()) {
                    continue;
                }
                int begin = member.getRange().get().begin.line;
                if (member.getComment().isPresent() && member.getComment().get().getRange().isPresent()) {
                    begin = Math.min(begin, member.getComment().get().getRange().get().begin.line);
                }
                int end = member.getRange().get().end.line;
                // wrapped line n == body line n-1 (1-based) == bodyLines[n-2]
                StringBuilder text = new StringBuilder();
                for (int i = begin - 2; i <= end - 2 && i < bodyLines.length; i++) {
                    if (i >= 0) {
                        text.append(bodyLines[i]).append('\n');
                    }
                }
                members.add(toMember(member, text.toString()));
            }
            if (!members.isEmpty()) {
                return new GeneratedTest(List.copyOf(imports), members, code.strip());
            }
        }

        // Could not parse: hand the text over verbatim and let the compiler explain.
        members.add(new Member("raw", null, -1, dedent(body)));
        return new GeneratedTest(List.copyOf(imports), members, code.strip());
    }

    private static boolean looksLikeWholeClass(String body) {
        // A class declaration at the very start (after annotations/comments), not a nested helper
        // after some test methods.
        String head = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "").strip();
        return TYPE_DECL.matcher(head).lookingAt();
    }

    private static Member toMember(BodyDeclaration<?> member, String text) {
        String dedented = dedent(text);
        if (member instanceof CallableDeclaration<?> c) {
            String kind = member.isMethodDeclaration() ? "method" : "constructor";
            return new Member(kind, c.getNameAsString(), c.getParameters().size(), dedented);
        }
        if (member instanceof FieldDeclaration f) {
            String name = f.getVariables().isEmpty() ? null : f.getVariable(0).getNameAsString();
            return new Member("field", name, -1, dedented);
        }
        if (member instanceof TypeDeclaration<?> t) {
            return new Member("type", t.getNameAsString(), -1, dedented);
        }
        return new Member("initializer", null, -1, dedented);
    }

    static String extractCode(String reply) {
        Matcher m = CODE_BLOCK.matcher(reply);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(m.group(1));
        }
        if (sb.length() > 0) {
            return sb.toString();
        }
        if (reply.contains("@Test") || reply.contains("void ")) {
            return reply;
        }
        return "";
    }

    /**
     * Remove the common leading whitespace from every non-blank line and trailing blank lines.
     */
    static String dedent(String text) {
        String[] lines = text.split("\n", -1);
        int common = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int n = 0;
            while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
                n++;
            }
            common = Math.min(common, n);
        }
        if (common == Integer.MAX_VALUE) {
            common = 0;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.isBlank() ? "" : line.substring(common)).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
