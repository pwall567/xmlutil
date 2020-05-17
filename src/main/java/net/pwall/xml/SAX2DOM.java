/*
 * @(#) SAX2DOM.java
 *
 * javautil Java Utility Library
 * Copyright (c) 2020 Peter Wall
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.ext.Locator2;

/**
 * An implementation of the {@link SAXHandler} interface that takes a stream of SAX events and creates a DOM.
 *
 * @author  Peter Wall
 */
public class SAX2DOM implements SAXHandler {

    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";

    private final Deque<Node> nodeStack;
    private final List<PrefixMapping> prefixMappings;
    private Document document;
    private Locator documentLocator;
    private Node node;
    private boolean inCDATA;

    /**
     * Construct a {@link SAX2DOM}, providing a {@link Document} as the factory object for creating the various child
     * nodes.  (In most cases the {@link Document} will be created when {@link #startDocument()} is invoked, so the
     * parameter will not be needed.)
     *
     * @param   document    a {@link Document}, or {@code null} if not required
     */
    public SAX2DOM(Document document) {
        this.document = document;
        nodeStack = new ArrayDeque<>();
        prefixMappings = new ArrayList<>();
        documentLocator = null;
        node = null;
        inCDATA = false;
    }

    public SAX2DOM() {
        this(null);
    }

    public Node getNode() {
        return node;
    }

    public Document getDocument() {
        return document;
    }

    public Locator getDocumentLocator() {
        return documentLocator;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        documentLocator = locator;
    }

    @Override
    public void startDocument() {
        if (node != null)
            throw exception("Document already started");
        Document document = XML.newDocument();
        if (documentLocator instanceof Locator2)
            document.setXmlVersion(((Locator2)documentLocator).getXMLVersion());
        node = document;
        nodeStack.addLast(document);
        if (this.document == null)
            this.document = document;
    }

    @Override
    public void endDocument() {
        Node node = nodeStack.removeLast();
        if (!(node instanceof Document))
            throw exception("endDocument not on root node");
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        prefixMappings.add(new PrefixMapping(prefix, uri));
    }

    @Override
    public void endPrefixMapping(String prefix) {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) {
        Objects.requireNonNull(qName);
        if (uri == null)
            uri = "";
        if (document == null)
            throw exception("No document node");
        Element element;
        if (uri.length() == 0)
            element = document.createElement(qName);
        else
            element = document.createElementNS(uri, qName);

        for (PrefixMapping prefixMapping : prefixMappings)
            element.setAttributeNS(XMLNS_URI, prefixMapping.getXmlns(), prefixMapping.getUri());
        prefixMappings.clear();

        for (int i = 0, n = atts.getLength(); i < n; i++) {
            String attURI = atts.getURI(i);
            if (attURI == null)
                attURI = "";
            String attLocalName = atts.getLocalName(i);
            if (attLocalName == null)
                attLocalName = "";
            String attQName = Objects.requireNonNull(atts.getQName(i));
            String attValue = Objects.requireNonNull(atts.getValue(i));
            String attType = Objects.requireNonNull(atts.getType(i));
            if (attURI.length() == 0) {
                element.setAttribute(attQName, attValue);
                if (attType.equals("ID"))
                    element.setIdAttribute(attLocalName, true);
            }
            else {
                element.setAttributeNS(attURI,attQName, attValue);
                if (attType.equals("ID"))
                    element.setIdAttributeNS(attURI, attLocalName, true);
            }
        }

        if (node == null)
            node = element;
        else {
            if (nodeStack.isEmpty())
                throw exception("Illegal sequence of elements");
            nodeStack.peekLast().appendChild(element);
        }
        nodeStack.addLast(element);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        Objects.requireNonNull(qName);
        if (uri == null)
            uri = "";
        Node last = nodeStack.removeLast();
        if (last instanceof Element) {
            Element element = (Element)last;
            if (uri.length() == 0) {
                if (qName.equals(element.getTagName()))
                    return;
            }
            else {
                if (uri.equals(element.getNamespaceURI()) && qName.equals(element.getTagName()))
                    return;
            }
        }
        throw exception("endElement does not match");
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (!nodeStack.isEmpty()) {
            Node last = nodeStack.peekLast();
            if (last instanceof Element) {
                if (inCDATA)
                    last.appendChild(document.createCDATASection(new String(ch, start, length)));
                else
                    last.appendChild(document.createTextNode(new String(ch, start, length)));
            }
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    @Override
    public void processingInstruction(String target, String data) {
    }

    @Override
    public void skippedEntity(String name) {
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) {
    }

    @Override
    public void endDTD() {
    }

    @Override
    public void startEntity(String name) {
    }

    @Override
    public void endEntity(String name) {
    }

    @Override
    public void startCDATA() {
        inCDATA = true;
    }

    @Override
    public void endCDATA() {
        inCDATA = false;
    }

    @Override
    public void comment(char[] ch, int start, int length) {
        if (!nodeStack.isEmpty()) {
            Node last = nodeStack.peekLast();
            if (last instanceof Element) {
                last.appendChild(document.createComment(new String(ch, start, length)));
            }
        }
    }

    private XMLException exception(String text) {
        String message;
        if (documentLocator != null)
            message = documentLocator.getSystemId() + ':' + documentLocator.getLineNumber() + ':' +
                    documentLocator.getColumnNumber() + " - " + text;
        else
            message = text;
        return new XMLException(message);
    }

}
