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
        boolean tr = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-tr"))
                tr = true;
            else {
                if (tr)
                    trace(arg);
                else
                    format(arg);
            }
        }
    }

    public static void format(String filename) {
        try (XMLFormatter formatter = new XMLFormatter(os);
                InputStream is = new FileInputStream(filename)) {
            Document document = XML.getDocumentBuilderNS().parse(is);
            DOM2SAX dom2sax = new DOM2SAX(document);
            dom2sax.process(formatter);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void trace(String filename) {
        SAXTrace saxTrace = new SAXTrace();
        try (InputStream is = new FileInputStream(filename)) {
            Document document = XML.getDocumentBuilderNS().parse(is);
            DOM2SAX dom2sax = new DOM2SAX(document);
            dom2sax.process(saxTrace);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
