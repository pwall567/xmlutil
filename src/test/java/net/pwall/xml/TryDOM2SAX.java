/*
 * @(#) TryDOM2SAX.java
 */

package net.pwall.xml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.w3c.dom.Document;

/**
 * Test class for DOM2SAX
 */
public class TryDOM2SAX {

    private static OutputStream os = System.out;

    public static void main(String[] args) {
        try (XMLFormatter formatter = new XMLFormatter(os);
                InputStream is = new FileInputStream(args[0])) {
            Document document = XML.getDocumentBuilderNS().parse(is);
            DOM2SAX dom2sax = new DOM2SAX(document);
            dom2sax.process(formatter);
            formatter.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
