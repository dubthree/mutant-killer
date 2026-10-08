package io.github.dubthree.mutantkiller.pit;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Parses PIT {@code mutations.xml} reports.
 */
public class PitReportParser {

    private final XmlMapper xmlMapper;

    public PitReportParser() {
        this.xmlMapper = new XmlMapper();
        // PIT adds elements over time (indexes, blocks, killingTests, ...); never fail on them.
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Parse a single report file.
     */
    public List<MutationResult> parse(File reportFile) throws IOException {
        MutationsReport report = xmlMapper.readValue(reportFile, MutationsReport.class);
        if (report == null || report.mutations == null) {
            return List.of();
        }
        return report.mutations.stream().map(this::toMutationResult).toList();
    }

    /**
     * Parse several reports (for example one per Maven module) into one list.
     */
    public List<MutationResult> parseAll(Collection<File> reportFiles) throws IOException {
        List<MutationResult> all = new ArrayList<>();
        for (File f : reportFiles) {
            all.addAll(parse(f));
        }
        return all;
    }

    private MutationResult toMutationResult(MutationElement e) {
        return new MutationResult(
            e.mutatedClass,
            e.mutatedMethod,
            e.methodDescription,
            e.lineNumber,
            e.mutator,
            e.description,
            e.status,
            e.sourceFile,
            e.killingTest,
            firstInt(e.index, e.indexes),
            firstInt(e.block, e.blocks)
        );
    }

    /**
     * First integer in either the legacy scalar form or the wrapped list form.
     */
    static int firstInt(Object scalar, Object wrapped) {
        Integer fromScalar = toInt(scalar);
        if (fromScalar != null) {
            return fromScalar;
        }
        if (wrapped instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                Integer i = toInt(value);
                if (i != null) {
                    return i;
                }
            }
        }
        Integer direct = toInt(wrapped);
        return direct != null ? direct : 0;
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : toInt(list.get(0));
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty() ? null : toInt(map.values().iterator().next());
        }
        try {
            return Integer.parseInt(value.toString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JacksonXmlRootElement(localName = "mutations")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class MutationsReport {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "mutation")
        List<MutationElement> mutations = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class MutationElement {
        @JacksonXmlProperty(isAttribute = true)
        boolean detected;

        @JacksonXmlProperty(isAttribute = true)
        String status;

        @JacksonXmlProperty(isAttribute = true)
        int numberOfTestsRun;

        String sourceFile;
        String mutatedClass;
        String mutatedMethod;
        String methodDescription;
        int lineNumber;
        String mutator;
        String description;
        String killingTest;
        // PIT < 1.7 wrote <index>n</index>; newer versions nest <indexes><index>n</index></indexes>.
        // Both are read as untyped values to sidestep the element-name clash.
        @JacksonXmlProperty(localName = "index")
        Object index;

        @JacksonXmlProperty(localName = "indexes")
        Object indexes;

        @JacksonXmlProperty(localName = "block")
        Object block;

        @JacksonXmlProperty(localName = "blocks")
        Object blocks;
    }
}
