package io.github.dubthree.mutantkiller.pit;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses PIT mutation testing XML reports.
 */
public class PitReportParser {

    private final XmlMapper xmlMapper;

    public PitReportParser() {
        this.xmlMapper = new XmlMapper();
    }

    /**
     * Parse a PIT mutations.xml file and return all mutation results.
     */
    public List<MutationResult> parse(File reportFile) throws IOException {
        MutationsReport report = xmlMapper.readValue(reportFile, MutationsReport.class);
        
        if (report.mutations == null) {
            return List.of();
        }

        return report.mutations.stream()
            .map(this::toMutationResult)
            .toList();
    }

    private MutationResult toMutationResult(MutationElement element) {
        return new MutationResult(
            element.mutatedClass,
            element.mutatedMethod,
            element.methodDescription,
            element.lineNumber,
            element.mutator,
            element.description,
            element.status,
            element.sourceFile,
            element.killingTest
        );
    }

    // XML mapping classes

    @JacksonXmlRootElement(localName = "mutations")
    static class MutationsReport {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "mutation")
        List<MutationElement> mutations = new ArrayList<>();
    }

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
    }
}
