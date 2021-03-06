/*
 * @(#) SAXTrace.java
 *
 * xmlutil XML Library
 * Copyright (c) 2015, 2016 Peter Wall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.pwall.xml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import net.pwall.util.Java;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Tracing implementation of SAX interfaces.  Useful for determining the sequence of SAX events
 * generated for a specific section of XML.
 */
public class SAXTrace extends DefaultHandler2 {

    private PrintStream printStream;

    public SAXTrace(PrintStream printStream) {
        this.printStream = printStream;
    }

    public SAXTrace() {
        this(System.out);
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws IOException, SAXException {
        trace("EntityResolver", "resolveEntity", quote(publicId), quote(systemId));
        return null;
    }

    @Override
    public void notationDecl(String name, String publicId, String systemId)
            throws SAXException {
        trace("DTDHandler", "notationDecl", quote(name), quote(publicId), quote(systemId));
    }

    @Override
    public void unparsedEntityDecl(String name, String publicId, String systemId,
            String notationName) throws SAXException {
        trace("DTDHandler", "unparsedEntityDecl", quote(name), quote(publicId), quote(systemId),
                quote(notationName));
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        trace("ContentHandler", "setDocumentLocator", String.valueOf(locator));
    }

    @Override
    public void startDocument() throws SAXException {
        trace("ContentHandler", "startDocument");
    }

    @Override
    public void endDocument() throws SAXException {
        trace("ContentHandler", "endDocument");
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        trace("ContentHandler", "startPrefixMapping", quote(prefix), quote(uri));
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        trace("ContentHandler", "endPrefixMapping", quote(prefix));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        String att;
        if (attributes == null)
            att = "null";
        else {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            int len = attributes.getLength();
            if (len > 0) {
                int i = 0;
                for (;;) {
                    String attURI = attributes.getURI(i);
                    String attLocalName = attributes.getLocalName(i);
                    String attQName = attributes.getQName(i);
                    String attType = attributes.getType(i);
                    String attValue = attributes.getValue(i);
                    sb.append("\n  { uri: ").append(quote(attURI)).append(", localName: ").
                            append(quote(attLocalName)).append(", qName: ").
                            append(quote(attQName)).append(", type: ").
                            append(quote(attType)).append(", value: ").
                            append(quote(attValue)).append(" }");
                    if (++i >= len)
                        break;
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append(']');
            att = sb.toString();
        }
        trace("ContentHandler", "startElement", quote(uri), quote(localName), quote(qName),
                att);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        trace("ContentHandler", "endElement", quote(uri), quote(localName), quote(qName));
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        trace("ContentHandler", "characters", quote(new String(ch, start, length)));
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        trace("ContentHandler", "ignorableWhitespace", quote(new String(ch, start, length)));
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        trace("ContentHandler", "processingInstruction", quote(target), quote(data));
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        trace("ContentHandler", "skippedEntity", quote(name));
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        trace("ErrorHandler", "warning", quote(e.getMessage()));
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        trace("ErrorHandler", "error", quote(e.getMessage()));
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        trace("ErrorHandler", "fatalError", quote(e.getMessage()));
    }

    @Override
    public void startCDATA() throws SAXException {
        trace("LexicalHandler", "startCDATA");
    }

    @Override
    public void endCDATA() throws SAXException {
        trace("LexicalHandler", "endCDATA");
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        trace("LexicalHandler", "startDTD", quote(name), quote(publicId), quote(systemId));
    }

    @Override
    public void endDTD() throws SAXException {
        trace("LexicalHandler", "endDTD");
    }

    @Override
    public void startEntity(String name) throws SAXException {
        trace("LexicalHandler", "startEntity", quote(name));
    }

    @Override
    public void endEntity(String name) throws SAXException {
        trace("LexicalHandler", "endEntity", quote(name));
    }

    @Override
    public void comment(char ch [], int start, int length) throws SAXException {
        trace("LexicalHandler", "comment", quote(new String(ch, start, length)));
    }

    @Override
    public void attributeDecl(String eName, String aName, String type, String mode,
            String value) throws SAXException {
        trace("DeclHandler", "attributeDecl", quote(eName), quote(aName), quote(type),
                quote(mode), quote(value));
    }

    @Override
    public void elementDecl(String name, String model) throws SAXException {
        trace("DeclHandler", "elementDecl", quote(name), quote(model));
    }

    @Override
    public void externalEntityDecl(String name, String publicId, String systemId)
            throws SAXException {
        trace("DeclHandler", "externalEntityDecl", quote(name), quote(publicId),
                quote(systemId));
    }

    @Override
    public void internalEntityDecl(String name, String value) throws SAXException {
        trace("DeclHandler", "internalEntityDecl", quote(name), quote(value));
    }

    private String quote(String str) {
        return str == null ? "null" : Java.quote(str);
    }

    private void trace(String iface, String method, String ... args) {
        printStream.print(">>");
        printStream.print(iface);
        printStream.print('.');
        printStream.print(method);
        printStream.print('(');
        int numArgs = args.length;
        if (numArgs > 0) {
            int i = 0;
            for (;;) {
                printStream.print(args[i]);
                if (++i >= numArgs)
                    break;
                printStream.print(", ");
            }
        }
        printStream.println(')');
    }

    public static void main(String[] args) {
        PrintStream ps = System.out;
        boolean nsAware = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-ns".equals(arg))
                nsAware = true;
            // check more switches?
            else
                process(ps, arg, nsAware);
        }
    }

    private static void process(PrintStream ps, String filename, boolean nsAware) {
        ps.println("SaxTrace - " + filename + " - namespaces " + (nsAware ? "en" : "dis") +
                "abled");
        SAXTrace saxTrace = new SAXTrace(ps);
        try (InputStream is = new FileInputStream(filename)) {
            XMLReader reader = XMLReaderFactory.createXMLReader();
            reader.setFeature(XML.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            reader.setFeature(XML.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            reader.setFeature(XML.VALIDATION_FEATURE, false);
            reader.setFeature(XML.RESOLVE_DTD_URIS_FEATURE, false);
            reader.setFeature(XML.NAMESPACES_FEATURE, nsAware);
            reader.setContentHandler(saxTrace);
            reader.setDTDHandler(saxTrace);
            reader.setErrorHandler(saxTrace);
            try {
                reader.setProperty(XML.LEXICAL_HANDLER_PROPERTY, saxTrace);
            }
            catch (SAXNotRecognizedException e) {
                // ignore
            }
            InputSource in = new InputSource(is);
            reader.parse(in);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
