/*
 * @(#) XMLFormatter.java
 *
 * XML Formatter Class
 * Copyright (c) 2012, 2013, 2014 Peter Wall
 *
 * This file is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.pwall.xml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.pwall.util.UserError;

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
 * XML Formatter.  Takes a stream of SAX events and outputs the corresponding external
 * representation of the XML.
 *
 * @author Peter Wall
 */
public class XMLFormatter extends DefaultHandler2 implements AutoCloseable {

    /**
     * Enumeration to control whitespace handling in the formatted output:
     * <dl>
     *   <dt>NONE</dt>
     *   <dd>All non-essential whitespace will be dropped</dd>
     *   <dt>ALL</dt>
     *   <dd>All whitespace will be output</dd>
     *   <dt>INDENT</dt>
     *   <dd>The formatter will, where possible, format the output in a conventional indented
     *   form</dd>
     * </dl>
     */
    public enum Whitespace {
        NONE, ALL, INDENT
    }

    private static String eol = System.getProperty("line.separator");

    private OutputStream out;
    private Whitespace whitespace;
    private String encoding;
    private int indent;
    private StringBuilder data;
    private boolean elementCloseAngleBracketPending;
    private List<Element> elements;
    private List<PrefixMapping> prefixMappings;
    private Writer writer;
    private boolean inCDATA;
    private Locator locator;
    private boolean documentStarted;
    private boolean documentEnded;
    private boolean dtdStarted;
    private boolean dtdInternalSubset;
    private boolean dtdEnded;

    /**
     * Construct an {@code XMLFormatter} using the given {@link OutputStream}, with the given
     * whitespace option.
     *
     * @param out         the {@link OutputStream}
     * @param whitespace  the whitespace option
     */
    public XMLFormatter(OutputStream out, Whitespace whitespace) {
        this.out = out;
        this.whitespace = whitespace;
        encoding = "UTF-8";
        indent = 2;
        data = new StringBuilder();
        elementCloseAngleBracketPending = false;
        elements = new ArrayList<>();
        prefixMappings = new ArrayList<>();
        writer = null;
        inCDATA = false;
        locator = null;
        documentStarted = false;
        documentEnded = false;
    }

    /**
     * Construct an {@code XMLFormatter} using the given {@link OutputStream}, with the default
     * whitespace option.
     *
     * @param out         the {@link OutputStream}
     */
    public XMLFormatter(OutputStream out) {
        this(out, Whitespace.ALL);
    }

    /**
     * Close the formatter.
     *
     * @throws IOException on any errors closing the output {@link Writer}.
     */
    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (!documentEnded) {
            documentEnded = true;
            throw new IOException("Premature XMLFormatter close");
        }
    }

    /**
     * Receive notification of the beginning of the DTD.
     *
     * @param   name        the document element name
     * @param   publicId    the public id
     * @param   systemId    the system id
     * @throws  SAXException    on any errors
     * @see     org.xml.sax.ext.LexicalHandler#startDTD(String, String, String)
     */
    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (dtdStarted || dtdEnded)
            throw new SAXException("Misplaced DTD");
        dtdStarted = true;
        String data = checkData();
        if (!XML.isAllWhiteSpace(data))
            throw new SAXException("Misplaced data before DOCTYPE");
        try {
            if (whitespace == Whitespace.ALL)
                write(data);
            write("<!DOCTYPE ");
            write(name);
            if (!isEmpty(publicId)) {
                write(" PUBLIC \"");
                write(publicId);
                write("\" \"");
                write(systemId);
                write('"');
            }
            else if (!isEmpty(systemId)) {
                write(" SYSTEM \"");
                write(systemId);
                write('"');
            }
        }
        catch (IOException ioe) {
            throw new SAXException("Error in XMLFormatter", ioe);
        }
    }

    /**
     * Receive notification of the end of the DTD.
     *
     * @throws  SAXException    on any errors
     * @see     org.xml.sax.ext.LexicalHandler#endDTD()
     */
    @Override
    public void endDTD() throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (!dtdStarted || dtdEnded)
            throw new SAXException("Misplaced End DTD");
        dtdEnded = true;
        try {
            if (dtdInternalSubset)
                write(']');
            write('>');
            if (whitespace == Whitespace.INDENT)
                write(eol);
        }
        catch (IOException ioe) {
            throw new SAXException("Error in XMLFormatter", ioe);
        }
    }

    /**
     * Receive notification of the beginning of the document.
     *
     * @exception   org.xml.sax.SAXException    on any errors
     * @see         org.xml.sax.ContentHandler#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        if (documentStarted)
            throw new SAXException("Document already started");
        documentStarted = true;
    }

    /**
     * Receive notification of the end of the document.
     *
     * @exception   org.xml.sax.SAXException    on any errors
     * @see         org.xml.sax.ContentHandler#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        documentEnded = true;
        if (elements.size() > 0)
            throw new SAXException("Premature document end");
        if (!XML.isAllWhiteSpace(data))
            throw new SAXException("Data after last element");
        try {
            write(checkData());
        }
        catch (IOException ioe) {
            throw new SAXException("Error in XMLFormatter", ioe);
        }
    }

    /**
     * Receive notification of the start of a Namespace mapping.
     *
     * @param   prefix  the Namespace prefix being declared
     * @param   uri     the Namespace URI mapped to the prefix
     * @exception       org.xml.sax.SAXException    on any errors
     * @see     org.xml.sax.ContentHandler#startPrefixMapping(String, String)
     */
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (prefix == null || uri == null)
            throw new SAXException("Null argument not allowed");
        if ("xml".equals(prefix))
            throw new SAXException("Can't use xml as namespace prefix");
        prefixMappings.add(new PrefixMapping(prefix, uri));
    }

    /**
     * Receive notification of the end of a Namespace mapping.
     *
     * @param   prefix  the Namespace prefix
     * @exception       org.xml.sax.SAXException    on any errors
     * @see     org.xml.sax.ContentHandler#endPrefixMapping(String)
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if (prefix == null)
            throw new SAXException("Null argument not allowed");
        // do nothing
    }

    /**
     * Set the {@link Locator} object for references from this document.
     *
     * @param locator  the {@link Locator} object
     */
    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            write(checkData());
            if (whitespace == Whitespace.INDENT)
                writeSpaces(elements.size() * getIndent());
            int prefixMappingIndex = elements.isEmpty() ? 0 :
                    elements.get(elements.size() - 1).getPrefixMappingIndex();
            write('<');
            write(qName); // if qualified, check that prefix mapping exists?
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                write(' ');
                write(attributes.getQName(i));
                write("=\"");
                write(XML.escape(attributes.getValue(i)));
                write('"');
            }
            for (; prefixMappingIndex < prefixMappings.size(); prefixMappingIndex++) {
                PrefixMapping prefixMapping = prefixMappings.get(prefixMappingIndex);
                write(" xmlns");
                if (prefixMapping.getPrefix().length() > 0) {
                    write(':');
                    write(prefixMapping.getPrefix());
                }
                write("=\"");
                write(XML.escape(prefixMapping.getUri()));
                write('"');
            }
            elements.add(new Element(uri, localName, qName, prefixMappingIndex));
            elementCloseAngleBracketPending = true;
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    private boolean elementsMatch(String uri, String localName, String qName) {
        int n = elements.size();
        if (n == 0)
            return false;
        Element e = elements.remove(n - 1);
        return Objects.equals(uri, e.getUri()) && Objects.equals(localName, e.getLocalName()) &&
                Objects.equals(qName, e.getQName());
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            if (!elementsMatch(uri, localName, qName))
                throw new IllegalStateException("Unmatched element end");
            String data = this.data.toString();
            this.data.setLength(0);
            if (whitespace == Whitespace.NONE) {
                if (elementCloseAngleBracketPending) {
                    if (XML.isAllWhiteSpace(data))
                        write("/>");
                    else {
                        write('>');
                        write(XML.escapeData(XML.trim(data)));
                        write("</");
                        write(qName);
                        write('>');
                    }
                    elementCloseAngleBracketPending = false;
                }
                else {
                    if (!XML.isAllWhiteSpace(data)) {
                        if (XML.isWhiteSpace(data.charAt(0)))
                            write(' ');
                        write(XML.escapeData(XML.trim(data)));
                    }
                    write("</");
                    write(qName);
                    write('>');
                }
            }
            else if (whitespace == Whitespace.ALL) {
                if (elementCloseAngleBracketPending) {
                    if (data.length() > 0) {
                        write('>');
                        write(XML.escapeData(data));
                        write("</");
                        write(qName);
                        write('>');
                    }
                    else
                        write("/>");
                    elementCloseAngleBracketPending = false;
                }
                else {
                    write(XML.escapeData(data));
                    write("</");
                    write(qName);
                    write('>');
                }
            }
            else { // whitespace == Whitespace.INDENT
                if (elementCloseAngleBracketPending) {
                    if (XML.isAllWhiteSpace(data)) {
                        write("/>");
                        write(eol);
                    }
                    else {
                        write('>');
                        write(XML.escapeData(XML.trim(data)));
                        write("</");
                        write(qName);
                        write('>');
                        write(eol);
                    }
                    elementCloseAngleBracketPending = false;
                }
                else {
                    if (!XML.isAllWhiteSpace(data)) {
                        writeSpaces((elements.size() + 1) * getIndent());
                        write(XML.escapeData(XML.trim(data)));
                        write(eol);
                    }
                    writeSpaces(elements.size() * getIndent());
                    write("</");
                    write(qName);
                    write('>');
                    write(eol);
                }
            }
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            if (inCDATA) {
                for (int i = 0, n = length - 1; i < n; i++) {
                    if (ch[i] == ']' && ch[i + 1] == ']')
                        throw new SAXParseException("Illegal content in CDATA", locator);
                }
                write(ch, start, length);
            }
            else
                data.append(ch, start, length);
        }
        catch (SAXException saxe) {
            throw saxe;
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    public void characters(String str) throws SAXException {
        characters(str.toCharArray(), 0, str.length());
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            write(checkData());
            if (target.indexOf("?>") >= 0 || data.indexOf("?>") >= 0)
                throw new SAXParseException("Illegal content in processing instruction",
                        locator);
            if (whitespace == Whitespace.INDENT)
                writeSpaces(elements.size() * getIndent());
            write("<?");
            write(target);
            write(' ');
            write(data);
            write("?>");
            if (whitespace == Whitespace.INDENT)
                write(eol);
        }
        catch (SAXException saxe) {
            throw saxe;
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        throw e;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        throw e;
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        throw e;
    }

    @Override
    public void startCDATA() throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            write(checkData());
            if (whitespace == Whitespace.INDENT)
                writeSpaces(elements.size() * getIndent());
            write("<![CDATA[");
            inCDATA = true;
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        if (!inCDATA)
            throw new SAXException("Mismatched end of CDATA");
        try {
            inCDATA = false;
            write("]]>");
            if (whitespace == Whitespace.INDENT)
                write(eol);
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (!documentStarted)
            throw new SAXException("Document not started");
        if (documentEnded)
            throw new SAXException("Document already ended");
        try {
            write(checkData());
            if (whitespace == Whitespace.INDENT)
                writeSpaces(elements.size() * getIndent());
            for (int i = 0, n = length - 1; i < n; i++) {
                if (ch[i] == '-' && ch[i + 1] == '-')
                    throw new SAXParseException("Illegal content in comment", locator);
            }
            write("<!--");
            write(ch, start, length);
            write("-->");
            if (whitespace == Whitespace.INDENT)
                write(eol);
        }
        catch (SAXException saxe) {
            throw saxe;
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    public void xslProcessingInstruction(String href) throws SAXException {
        if (href != null && href.length() > 0)
            processingInstruction("xml-stylesheet", "href=\"" + href + "\" type=\"text/xsl\"");
    }

    public void prefix(String standalone) throws SAXException {
        try {
            write(createPrefix(standalone));
            if (whitespace != Whitespace.NONE)
                write(eol);
        }
        catch (Exception e) {
            throw new SAXException("Error in XMLFormatter", e);
        }
    }

    public void prefix() throws SAXException {
        prefix(null);
    }

    public String createPrefix(String standalone) {
        StringBuilder output = new StringBuilder("<?xml version=\"1.0\"");
        String encoding = getEncoding();
        if (encoding != null)
            output.append(" encoding=\"").append(encoding).append('"');
        if (standalone != null)
            output.append(" standalone=\"").append(standalone).append('"');
        output.append("?>");
        return output.toString();
    }

    public String createPrefix() {
        return createPrefix(null);
    }

    private String checkData() {
        StringBuilder output = new StringBuilder();
        String data = this.data.toString();
        this.data.setLength(0);
        if (whitespace == Whitespace.NONE) {
            if (elementCloseAngleBracketPending) {
                output.append('>');
                elementCloseAngleBracketPending = false;
                if (!XML.isAllWhiteSpace(data)) {
                    output.append(XML.escapeData(XML.trim(data)));
                    if (XML.isWhiteSpace(data.charAt(data.length() - 1)))
                        output.append(' ');
                }
            }
            else if (data.length() > 0) {
                if (XML.isAllWhiteSpace(data))
                    output.append(' ');
                else {
                    if (XML.isWhiteSpace(data.charAt(0)))
                        output.append(' ');
                    output.append(XML.escapeData(XML.trim(data)));
                    if (XML.isWhiteSpace(data.charAt(data.length() - 1)))
                        output.append(' ');
                }
            }
        }
        else if (whitespace == Whitespace.ALL) {
            if (elementCloseAngleBracketPending) {
                output.append('>');
                elementCloseAngleBracketPending = false;
            }
            output.append(XML.escapeData(data));
        }
        else { // whitespace == Whitespace.INDENT
            if (elementCloseAngleBracketPending) {
                output.append('>');
                output.append(eol);
                elementCloseAngleBracketPending = false;
            }
            if (!XML.isAllWhiteSpace(data)) {
                addSpaces(output, elements.size() * getIndent());
                output.append(XML.escapeData(XML.trim(data)));
                output.append(eol);
            }
        }
        return output.toString();
    }

    private void write(String str) throws IOException {
        getWriter().write(str);
    }

    private void write(char ch) throws IOException {
        getWriter().write(ch);
    }

    private void write(char[] buf, int start, int length) throws IOException {
        getWriter().write(buf, start, length);
    }

    private void writeSpaces(int length) throws IOException {
        while (length-- > 0)
            getWriter().write(' ');
    }

    private synchronized Writer getWriter() throws IOException {
        if (writer == null) {
            if (out == null)
                throw new IllegalStateException("Output Stream not set");
            String encoding = getEncoding();
            writer = new BufferedWriter(encoding == null ? new OutputStreamWriter(out) :
                    new OutputStreamWriter(out, encoding));
        }
        return writer;
    }

    private static void addSpaces(StringBuilder a, int n) {
        for (; n > 0; n--)
            a.append(' ');
    }

    public Whitespace getWhitespace() {
        return whitespace;
    }

    public void setWhitespace(Whitespace whitespace) {
        this.whitespace = whitespace;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static void main(String[] args) {
        try {
            File in = null;
            File out = null;
            Whitespace ws = null;
            int indent = -1;
            Boolean nsAware = null;
            Boolean extEntities = null;
            for (int i = 0; i < args.length; ++i) {
                String arg = args[i];
                if (arg.equals("-in")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-in with no pathname");
                    if (in != null)
                        throw new UserError("Duplicate " + arg);
                    in = new File(args[i]);
                    if (!in.exists() || !in.isFile())
                        throw new UserError("-in file does not exist - " + args[i]);
                }
                else if (arg.equals("-out")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-out with no pathname");
                    if (out != null)
                        throw new UserError("Duplicate " + arg);
                    out = new File(args[i]);
                }
                else if (arg.equals("-whitespace") || arg.equals("-ws")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError(arg + " with no option");
                    if (ws != null)
                        throw new UserError("Duplicate " + arg);
                    try {
                        ws = Whitespace.valueOf(args[i].toUpperCase());
                    }
                    catch (IllegalArgumentException iae) {
                        throw new UserError("Illegal " + arg + " option - " + args[i]);
                    }
                    if ((ws == Whitespace.ALL || ws == Whitespace.NONE) && indent >= 0)
                        throw new UserError("-indent conflicts with whitespace option");
                }
                else if (arg.equals("-indent")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-indent with no value");
                    if (indent >= 0)
                        throw new UserError("Duplicate " + arg);
                    if (ws == Whitespace.ALL || ws == Whitespace.NONE)
                        throw new UserError("-indent conflicts with whitespace option");
                    try {
                        indent = Integer.parseInt(args[i]);
                        if (indent < 0 || indent > 100)
                            throw new UserError("Illegal -indent");
                    }
                    catch (NumberFormatException e) {
                        throw new UserError("Illegal -indent");
                    }
                }
                else if (arg.equals("-ns")) { // make parser namespace-aware
                    if (nsAware != null)
                        throw new UserError("Duplicate " + arg);
                    nsAware = Boolean.TRUE;
                }
                else if (arg.equals("-nons")) {
                    if (nsAware != null)
                        throw new UserError("Duplicate " + arg);
                    nsAware = Boolean.FALSE;
                }
                else if (arg.equals("-ext")) { // allow external entities
                    if (extEntities != null)
                        throw new UserError("Duplicate " + arg);
                    extEntities = Boolean.TRUE;
                }
                else if (arg.equals("-noext")) {
                    if (extEntities != null)
                        throw new UserError("Duplicate " + arg);
                    extEntities = Boolean.FALSE;
                }
                else
                    throw new UserError("Unrecognised argument - " + arg);
            }
            if (in == null)
                throw new UserError("No -in specified");
            if (nsAware == null)
                nsAware = Boolean.FALSE;
            if (extEntities == null)
                extEntities = Boolean.FALSE;
            if (out != null) {
                try (OutputStream os = new FileOutputStream(out)) {
                    run(os, in, ws, indent, nsAware, extEntities);
                }
                catch (IOException ioe) {
                    throw new RuntimeException("Error writing output file", ioe);
                }
            }
            else
                run(System.out, in, ws, indent, nsAware, extEntities);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(OutputStream os, File in, Whitespace ws, int indent,
            boolean nsAware, boolean extEntities) {
        try (XMLFormatter formatter = new XMLFormatter(os);
                InputStream is = new FileInputStream(in)) {
            formatter.setWhitespace(ws != null ? ws : Whitespace.INDENT);
            formatter.setIndent(indent >= 0 ? indent : 2);
            formatter.prefix();
            XMLReader reader = XMLReaderFactory.createXMLReader();
            reader.setFeature(XML.VALIDATION_FEATURE, false);
            reader.setFeature(XML.RESOLVE_DTD_URIS_FEATURE, false);
            reader.setFeature(XML.NAMESPACES_FEATURE, nsAware);
            reader.setFeature(XML.EXTERNAL_GENERAL_ENTITIES_FEATURE, extEntities);
            reader.setFeature(XML.EXTERNAL_PARAMETER_ENTITIES_FEATURE, extEntities);
            reader.setContentHandler(formatter);
            reader.setErrorHandler(formatter);
            try {
                reader.setProperty(XML.LEXICAL_HANDLER_PROPERTY, formatter);
            }
            catch (SAXNotRecognizedException e) {
                // ignore
            }
            reader.parse(new InputSource(is));
        }
        catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    /**
     * A simple class to store the tagname information about the current element.
     */
    public static class Element {

        private String uri;
        private String localName;
        private String qName;
        private int prefixMappingIndex;

        public Element(String uri, String localName, String qName, int prefixMappingIndex) {
            this.uri = uri;
            this.localName = localName;
            this.qName = qName;
            this.prefixMappingIndex = prefixMappingIndex;
        }

        public String getUri() {
            return uri;
        }

        public String getLocalName() {
            return localName;
        }

        public String getQName() {
            return qName;
        }

        public int getPrefixMappingIndex() {
            return prefixMappingIndex;
        }

    }

    public static class PrefixMapping {

        private String prefix;
        private String uri;

        public PrefixMapping(String prefix, String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getUri() {
            return uri;
        }

    }

}
