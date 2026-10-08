package io.github.dubthree.mutantkiller.build;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Temporarily adds the pitest-maven plugin to a pom.xml that does not declare it, so mutation
 * testing works on projects that never set PIT up. {@link #restore()} puts the original bytes back.
 */
public class MavenPitInjector {

    public static final String PIT_VERSION = "1.30.0";
    public static final String JUNIT5_PLUGIN_VERSION = "1.2.3";
    public static final String TESTNG_PLUGIN_VERSION = "1.0.0";

    private final Path pom;
    private byte[] original;

    public MavenPitInjector(Path pom) {
        this.pom = pom;
    }

    /**
     * @return true if the pom was modified
     */
    public boolean inject(TestFramework framework) throws IOException {
        if (!Files.exists(pom)) {
            return false;
        }
        String text = Files.readString(pom, StandardCharsets.UTF_8);
        if (text.contains("pitest-maven")) {
            return false;
        }
        original = Files.readAllBytes(pom);
        String modified = addPlugin(text, framework);
        Files.writeString(pom, modified, StandardCharsets.UTF_8);
        return true;
    }

    public void restore() {
        if (original != null) {
            try {
                Files.write(pom, original);
            } catch (IOException e) {
                System.err.println("Warning: could not restore " + pom + ": " + e.getMessage());
            }
            original = null;
        }
    }

    public boolean isInjected() {
        return original != null;
    }

    /**
     * Pure function: returns {@code pomText} with the PIT plugin appended to {@code <build><plugins>}.
     */
    static String addPlugin(String pomText, TestFramework framework) throws IOException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(pomText.getBytes(StandardCharsets.UTF_8)));
            Element project = doc.getDocumentElement();

            Element build = child(project, "build");
            if (build == null) {
                build = doc.createElement("build");
                project.appendChild(build);
            }
            Element plugins = child(build, "plugins");
            if (plugins == null) {
                plugins = doc.createElement("plugins");
                build.appendChild(plugins);
            }

            Element plugin = doc.createElement("plugin");
            plugin.appendChild(text(doc, "groupId", "org.pitest"));
            plugin.appendChild(text(doc, "artifactId", "pitest-maven"));
            plugin.appendChild(text(doc, "version", PIT_VERSION));

            if (framework == TestFramework.JUNIT5 || framework == TestFramework.TESTNG) {
                Element deps = doc.createElement("dependencies");
                Element dep = doc.createElement("dependency");
                dep.appendChild(text(doc, "groupId", "org.pitest"));
                if (framework == TestFramework.JUNIT5) {
                    dep.appendChild(text(doc, "artifactId", "pitest-junit5-plugin"));
                    dep.appendChild(text(doc, "version", JUNIT5_PLUGIN_VERSION));
                } else {
                    dep.appendChild(text(doc, "artifactId", "pitest-testng-plugin"));
                    dep.appendChild(text(doc, "version", TESTNG_PLUGIN_VERSION));
                }
                deps.appendChild(dep);
                plugin.appendChild(deps);
            }

            Element configuration = doc.createElement("configuration");
            Element formats = doc.createElement("outputFormats");
            formats.appendChild(text(doc, "outputFormat", "XML"));
            configuration.appendChild(formats);
            configuration.appendChild(text(doc, "timestampedReports", "false"));
            configuration.appendChild(text(doc, "failWhenNoMutations", "false"));
            plugin.appendChild(configuration);
            plugins.appendChild(plugin);

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not edit pom.xml to add PIT: " + e.getMessage(), e);
        }
    }

    private static Element child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element text(Document doc, String name, String value) {
        Element e = doc.createElement(name);
        e.setTextContent(value);
        return e;
    }
}
