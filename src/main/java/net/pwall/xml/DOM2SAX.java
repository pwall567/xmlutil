/*
 * @(#) DOM2SAX.java
 *
 * XML DOM to SAX Conversion Class
 * Copyright (c) 2016 Peter Wall
 */

package net.pwall.xml;

import java.util.Objects;

import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * XML DOM to SAX Conversion Class.
 *
 * @author Peter Wall
 */
public class DOM2SAX {

    private Document document;

    public DOM2SAX(Document document) {
        this.document = Objects.requireNonNull(document);
    }

    public Document getDocument() {
        return document;
    }

    public void process(ContentHandler contentHandler, LexicalHandler lexicalHandler)
            throws SAXException {
        Objects.requireNonNull(contentHandler);
        contentHandler.startDocument();
        Element element = document.getDocumentElement();
        processElement(contentHandler, lexicalHandler, element);
        // tbc
        contentHandler.endDocument();
    }

    public void process(DefaultHandler2 defaultHandler2) throws SAXException {
        Objects.requireNonNull(defaultHandler2);
        process(defaultHandler2, defaultHandler2);
    }

    private void processElement(ContentHandler contentHandler, LexicalHandler lexicalHandler,
            Element element) throws SAXException {
        String uri = element.getNamespaceURI();
        String localName = element.getLocalName();
        String qName = element.getNodeName();
        AttributesImpl atts = new AttributesImpl();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr)attributes.item(i);
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            if (attrName != null && attrName.equals("xmlns")) {
                contentHandler.startPrefixMapping("", attrValue);
            }
            else if (attrName != null && attrName.length() > 6 &&
                    attrName.startsWith("xmlns:")) {
                contentHandler.startPrefixMapping(attrName.substring(6), attrValue);
            }
            else
                atts.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attrName,
                        "CDATA", attr.getValue()); // ???
        }
        contentHandler.startElement(uri, localName, qName, atts);

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                processElement(contentHandler, lexicalHandler, (Element)child);
            }
            else if (child instanceof Text) {
                String data = ((Text)child).getData();
                int length = data.length();
                char[] chars = new char[length];
                data.getChars(0, length, chars, 0);
                contentHandler.characters(chars, 0, length);
            }
            else if (child instanceof CDATASection) {
                String data = ((CDATASection)child).getData();
                int length = data.length();
                char[] chars = new char[length];
                data.getChars(0, length, chars, 0);
                if (lexicalHandler != null)
                    lexicalHandler.startCDATA();
                contentHandler.characters(chars, 0, length);
                if (lexicalHandler != null)
                    lexicalHandler.endCDATA();
            }
            // any other node types?
        }

        contentHandler.endElement(uri, localName, qName);
    }

}
