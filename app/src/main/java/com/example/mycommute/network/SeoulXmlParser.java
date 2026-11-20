package com.example.mycommute.network;

import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Shared Android-compatible XML parsing for Seoul BIS responses.
 * Rejects DTDs and external entities without Android-incompatible SAX feature flags.
 */
public final class SeoulXmlParser {
    private static final int MAX_XML_BYTES = 3_200_000;
    private SeoulXmlParser() {}

    public static Document parse(byte[] bytes) throws Exception {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_XML_BYTES)
            throw new IllegalArgumentException("Seoul XML response size invalid");
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if(java.util.regex.Pattern.compile("<!\\s*(?:DOCTYPE|ENTITY)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(xml).find())
            throw new IllegalArgumentException("DTD/entities are prohibited");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> {
            throw new org.xml.sax.SAXException("External entities are prohibited");
        });
        return builder.parse(new ByteArrayInputStream(bytes));
    }
}
