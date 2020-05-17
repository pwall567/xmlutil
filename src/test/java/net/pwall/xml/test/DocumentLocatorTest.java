/*
 * @(#) DocumentLocatorTest.java
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

import net.pwall.xml.DocumentLocator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocumentLocatorTest {

    private final String publicId = "-//W3C//DTD HTML 4.01 Transitional//EN";
    private final String systemId = "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd";

    @Test
    public void shouldCreateDocumentLocator() {
        DocumentLocator documentLocator = new DocumentLocator(publicId, systemId);
        assertEquals(publicId, documentLocator.getPublicId());
        assertEquals(systemId, documentLocator.getSystemId());
    }

    @Test
    public void shouldKeepTrackOfLineNumber() {
        DocumentLocator documentLocator = new DocumentLocator(publicId, systemId);
        documentLocator.setLineNumber(27);
        assertEquals(27, documentLocator.getLineNumber());
        documentLocator.setLineNumber(28);
        assertEquals(28, documentLocator.getLineNumber());
    }

    @Test
    public void shouldKeepTrackOfColumnNumber() {
        DocumentLocator documentLocator = new DocumentLocator(publicId, systemId);
        documentLocator.setColumnNumber(40);
        assertEquals(40, documentLocator.getColumnNumber());
        documentLocator.setColumnNumber(50);
        assertEquals(50, documentLocator.getColumnNumber());
    }

}
