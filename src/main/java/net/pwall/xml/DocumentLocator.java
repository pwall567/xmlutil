/*
 * @(#) DocumentLocator.java
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

import org.xml.sax.Locator;

/**
 * Simple implementation of {@link Locator} interface.
 *
 * @author  Peter Wall
 */
public class DocumentLocator implements Locator {

    private final String publicId;
    private final String systemId;
    private int lineNumber;
    private int columnNumber;

    public DocumentLocator(String publicId, String systemId) {
        this.publicId = publicId;
        this.systemId = systemId;
        lineNumber = 0;
        columnNumber = 0;
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

}
