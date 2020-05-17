/*
 * @(#) SAX2DOMTest.java
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

package net.pwall.xml.test;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Locator;

import net.pwall.xml.DocumentLocator;
import net.pwall.xml.SAX2DOM;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SAX2DOMTest {

    @Test
    public void shouldSetDocumentLocator() {
        Locator locator = new DocumentLocator("PUBLICID", "SYSTEMID");
        SAX2DOM sax2dom = new SAX2DOM();
        sax2dom.setDocumentLocator(locator);
        assertSame(locator, sax2dom.getDocumentLocator());
    }

    @Test
    public void shouldInitialiseWithNullNode() {
        SAX2DOM sax2dom = new SAX2DOM();
        assertNull(sax2dom.getNode());
    }

    @Test
    public void shouldCreateDocumentNodeAsNode() {
        SAX2DOM sax2dom = new SAX2DOM();
        sax2dom.startDocument();
        assertTrue(sax2dom.getNode() instanceof Document);
    }

    @Test
    public void shouldCreateDocumentNodeAsDocument() {
        SAX2DOM sax2dom = new SAX2DOM();
        sax2dom.startDocument();
        assertNotNull(sax2dom.getDocument());
    }

    @Test
    public void shouldCreateDocumentElement() {
        SAX2DOM sax2dom = new SAX2DOM();
        sax2dom.startDocument();
        sax2dom.startElement("", "", "superb", new AttributesImpl());
        sax2dom.endElement("", "", "superb");
        sax2dom.endDocument();
        Document document = sax2dom.getDocument();
        assertNotNull(document);
        Element element = document.getDocumentElement();
        assertNotNull(document);
        assertEquals("superb", element.getTagName());
    }

    @Test
    public void shouldCreateDocumentElementWithNamespace() {
        SAX2DOM sax2dom = new SAX2DOM();
        sax2dom.startDocument();
        sax2dom.startPrefixMapping("a", "http://dummy");
        sax2dom.startElement("http://dummy", "superb", "a:superb", new AttributesImpl());
        sax2dom.endElement("http://dummy", "superb", "a:superb");
        sax2dom.endPrefixMapping("a");
        sax2dom.endDocument();
        Document document = sax2dom.getDocument();
        assertNotNull(document);
        Element element = document.getDocumentElement();
        assertNotNull(document);
        assertEquals("a:superb", element.getTagName());
    }

}
