/*
 * @(#) XML.java
 *
 * javautil Java Utility Library
 * Copyright (c) 2013, 2014, 2015, 2016, 2017 Peter Wall
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.IntPredicate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.pwall.util.CharMapper;
import net.pwall.util.CharMapperEntry;
import net.pwall.util.CharUnmapper;
import net.pwall.util.Strings;

/**
 * Static methods for working with XML.
 *
 * <p>The descriptions of several methods in this class mention the definition of white space in
 * the XML specification.  This is a reference to the definition in Section 2.3 of
 * <a href="http://www.w3.org/TR/REC-xml/">Extensible Markup Language (XML) 1.0 (Fifth
 * Edition)</a>.</p>
 *
 * @author  Peter Wall
 *
 */
public class XML {

    public static final String NAMESPACES_FEATURE = "http://xml.org/sax/features/namespaces";
    public static final String VALIDATION_FEATURE = "http://xml.org/sax/features/validation";
    public static final String RESOLVE_DTD_URIS_FEATURE =
            "http://xml.org/sax/features/resolve-dtd-uris";
    public static final String EXTERNAL_GENERAL_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-general-entities";
    public static final String EXTERNAL_PARAMETER_ENTITIES_FEATURE =
            "http://xml.org/sax/features/external-parameter-entities";
    public static final String LEXICAL_HANDLER_PROPERTY =
            "http://xml.org/sax/properties/lexical-handler";

    private static DocumentBuilderFactory docBuilderFactory = null;
    private static DocumentBuilderFactory docBuilderFactoryNS = null;

    private static final IntPredicate spaceTest = (ch) -> isWhiteSpace(ch);

    private static final CharMapperEntry[] predefinedEntityMappings = new CharMapperEntry[] {
        new CharMapperEntry('&', "&amp;"),
        new CharMapperEntry('\'', "&apos;"),
        new CharMapperEntry('>', "&gt;"),
        new CharMapperEntry('<', "&lt;"),
        new CharMapperEntry('"', "&quot;")
    };

    private static final CharMapper defaultCharMapper = (codePoint) -> {
        if (codePoint == '<')
            return "&lt;";
        if (codePoint == '>')
            return "&gt;";
        if (codePoint == '&')
            return "&amp;";
        if (codePoint == '"')
            return "&quot;";
        if (codePoint < ' ' && !isWhiteSpace(codePoint) || codePoint >= 0x7F) {
            StringBuilder sb = new StringBuilder(10);
            sb.append("&#");
            sb.append(codePoint);
            sb.append(';');
            return sb.toString();
        }
        return null;
    };

    private static final CharMapper allCharMapper = (codePoint) -> {
        if (codePoint == '<')
            return "&lt;";
        if (codePoint == '>')
            return "&gt;";
        if (codePoint == '&')
            return "&amp;";
        if (codePoint == '"')
            return "&quot;";
        if (codePoint == '\'')
            return "&apos;";
        if (codePoint < ' ' && !isWhiteSpace(codePoint) || codePoint >= 0x7F) {
            StringBuilder sb = new StringBuilder(10);
            sb.append("&#");
            sb.append(codePoint);
            sb.append(';');
            return sb.toString();
        }
        return null;
    };

    private static final CharMapper dataCharMapper = (codePoint) -> {
        if (codePoint == '<')
            return "&lt;";
        if (codePoint == '>')
            return "&gt;";
        if (codePoint == '&')
            return "&amp;";
        if (codePoint < ' ' && !isWhiteSpace(codePoint) || codePoint >= 0x7F) {
            StringBuilder sb = new StringBuilder(10);
            sb.append("&#");
            sb.append(codePoint);
            sb.append(';');
            return sb.toString();
        }
        return null;
    };

    private static final CharUnmapper unmapper = new CharUnmapper() {
        @Override
        public boolean isEscape(CharSequence s, int offset) {
            return s.charAt(offset) == '&';
        }
        @Override
        public int unmap(StringBuilder sb, CharSequence s, int offset) {
            int start = offset + 1;
            if (start < s.length() && s.charAt(start) == '#') {
                int i = ++start;
                do {
                    if (i >= s.length())
                        throw new IllegalArgumentException("Unclosed character reference");
                } while (s.charAt(i++) != ';');
                int codePoint;
                try {
                    if (s.charAt(start) == 'x')
                        codePoint = Strings.convertHexToInt(s, start + 1, i - 1);
                    else
                        codePoint = Strings.convertToInt(s, start, i - 1);
                }
                catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Illegal digit in character reference");
                }
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    sb.append(Character.highSurrogate(codePoint));
                    sb.append(Character.lowSurrogate(codePoint));
                }
                else if (Character.isBmpCodePoint(codePoint) &&
                        !Character.isSurrogate((char)codePoint))
                    sb.append((char)codePoint);
                else
                    throw new IllegalArgumentException("Illegal character reference");
                return i - offset;
            }
            else {
                for (CharMapperEntry entry : predefinedEntityMappings) {
                    String mapping = entry.getString();
                    if (entriesEqual(s, offset, mapping)) {
                        sb.append((char)entry.getCodePoint()); // guaranteed to be in BMP
                        return mapping.length();
                    }
                }
                throw new IllegalArgumentException("Illegal entity reference");
            }
        }
    };

    private static boolean entriesEqual(CharSequence source, int start, CharSequence target) {
        int n = target.length();
        if (start + n > source.length())
            return false;
        for (int i = 0, j = start; i < n; i++, j++) {
            if (target.charAt(i) != source.charAt(j))
                return false;
        }
        return true;
    }

    /**
     * Private constructor - class is not to be instantiated.
     */
    private XML() {
    }

    /**
     * Parse an XML document from an {@link InputStream}.  This is a convenience method which
     * automates the use of {@link DocumentBuilderFactory} and {@link DocumentBuilder}.
     *
     * @param   is      the {@link InputStream}
     * @return          the result DOM
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     * @throws  SAXException if any parse errors occur
     * @throws  IOException if any I/O errors occur
     */
    public static Document parse(InputStream is)
            throws ParserConfigurationException, SAXException, IOException {
        return getDocumentBuilder().parse(is);
    }

    /**
     * Parse an XML document from an {@link InputStream} with a specified systemId.  This is a
     * convenience method which automates the use of {@link DocumentBuilderFactory} and
     * {@link DocumentBuilder}.
     *
     * @param   is          the {@link InputStream}
     * @param   systemId    the systemId
     * @return              the result DOM
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     * @throws  SAXException if any parse errors occur
     * @throws  IOException if any I/O errors occur
     */
    public static Document parse(InputStream is, String systemId)
            throws ParserConfigurationException, SAXException, IOException {
        return getDocumentBuilder().parse(is, systemId);
    }

    /**
     * Parse an XML document from a URI.  This is a convenience method which automates the use
     * of {@link DocumentBuilderFactory} and {@link DocumentBuilder}.
     *
     * @param   uri     the URI
     * @return          the result DOM
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     * @throws  SAXException if any parse errors occur
     * @throws  IOException if any I/O errors occur
     */
    public static Document parse(String uri)
            throws ParserConfigurationException, SAXException, IOException {
        return getDocumentBuilder().parse(uri);
    }

    /**
     * Parse an XML document from a {@link File}.  This is a convenience method which automates
     * the use of {@link DocumentBuilderFactory} and {@link DocumentBuilder}.
     *
     * @param   f       the {@link File}
     * @return          the result DOM
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     * @throws  SAXException if any parse errors occur
     * @throws  IOException if any I/O errors occur
     */
    public static Document parse(File f)
            throws ParserConfigurationException, SAXException, IOException {
        return getDocumentBuilder().parse(f);
    }

    /**
     * Parse an XML document from an {@link InputSource}.  This is a convenience method which
     * automates the use of {@link DocumentBuilderFactory} and {@link DocumentBuilder}.
     *
     * @param   is      the {@link InputSource}
     * @return          the result DOM
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     * @throws  SAXException if any parse errors occur
     * @throws  IOException if any I/O errors occur
     */
    public static Document parse(InputSource is)
            throws ParserConfigurationException, SAXException, IOException {
        return getDocumentBuilder().parse(is);
    }

    /**
     * Get a {@link DocumentBuilder}.  This is a convenience method which automates the process
     * of acquiring a {@link DocumentBuilder} from a {@link DocumentBuilderFactory}.
     *
     * @return  a {@link DocumentBuilder}
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     */
    public static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        return getDocumentBuilderFactory().newDocumentBuilder();
    }

    /**
     * Get a namespace-aware {@link DocumentBuilder}.  This is a convenience method which
     * automates the process of acquiring a {@link DocumentBuilder} from a namespace-aware
     * {@link DocumentBuilderFactory}.
     *
     * @return  a namespace-aware {@link DocumentBuilder}
     * @throws  ParserConfigurationException if the {@link DocumentBuilderFactory} can not
     *                                       create the {@link DocumentBuilder}
     */
    public static DocumentBuilder getDocumentBuilderNS() throws ParserConfigurationException {
        return getDocumentBuilderFactoryNS().newDocumentBuilder();
    }

    /**
     * Get a {@link DocumentBuilderFactory}.  This is a convenience method which returns a
     * shared instance.
     *
     * @return  a {@link DocumentBuilderFactory}
     */
    public static synchronized DocumentBuilderFactory getDocumentBuilderFactory() {
        if (docBuilderFactory == null)
            docBuilderFactory = DocumentBuilderFactory.newInstance();
        return docBuilderFactory;
    }

    /**
     * Get a namespace-aware {@link DocumentBuilderFactory}.  This is a convenience method which
     * returns a shared instance.
     *
     * @return  a namespace-aware {@link DocumentBuilderFactory}
     */
    public static synchronized DocumentBuilderFactory getDocumentBuilderFactoryNS() {
        if (docBuilderFactoryNS == null) {
            docBuilderFactoryNS = DocumentBuilderFactory.newInstance();
            docBuilderFactoryNS.setNamespaceAware(true);
        }
        return docBuilderFactoryNS;
    }

    /**
     * Create a new {@link Document}.
     *
     * @return  the {@link Document}
     * @throws  RuntimeException on parser configuration errors
     */
    public static Document newDocument() {
        try {
            return getDocumentBuilder().newDocument();
        }
        catch (ParserConfigurationException pce) {
            throw new RuntimeException("Parser configuration error", pce);
        }
    }

    /**
     * Escape a UTF-16 string for use in XML, with the default set of character mappings.
     * Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   s       the UTF-16 string to be escaped
     * @return          the escaped string
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String escapeUTF16(String s) {
        return Strings.escapeUTF16(s, defaultCharMapper);
    }

    /**
     * Escape a string for use in XML, with the default set of character mappings.
     * Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   s       the string to be escaped
     * @return          the escaped string
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String escape(String s) {
        return Strings.escape(s, defaultCharMapper);
    }

    /**
     * Escape a {@link CharSequence} for use in XML, with the default set of character mappings.
     * Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   cs      the {@link CharSequence} to be escaped
     * @return          the escaped {@link CharSequence}
     * @throws          NullPointerException if the input {@link CharSequence} is {@code null}
     */
    public static CharSequence escape(CharSequence cs) {
        return Strings.escape(cs, defaultCharMapper);
    }

    /**
     * Escape a string for use in XML, including apostrophe.  Specifically, this method
     * converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt><tt>&apos;</tt> (apostrophe)</dt>
     * <dd><tt>&amp;apos;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   s       the string to be escaped
     * @return          the escaped string
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String escapeAll(String s) {
        return Strings.escape(s, allCharMapper);
    }

    /**
     * Escape a {@link CharSequence} for use in XML, including apostrophe.  Specifically, this
     * method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt><tt>&apos;</tt> (apostrophe)</dt>
     * <dd><tt>&amp;apos;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   cs      the {@link CharSequence} to be escaped
     * @return          the escaped {@link CharSequence}
     * @throws          NullPointerException if the input {@link CharSequence} is {@code null}
     */
    public static CharSequence escapeAll(CharSequence cs) {
        return Strings.escape(cs, allCharMapper);
    }

    /**
     * Escape a string for use in XML, with only the character mappings required for element
     * content (not attributes).  Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   s       the string to be escaped
     * @return          the escaped string
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String escapeData(String s) {
        return Strings.escape(s, dataCharMapper);
    }

    /**
     * Escape a {@link CharSequence} for use in XML, with only the character mappings required
     * for element content (not attributes).  Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   cs      the {@link CharSequence} to be escaped
     * @return          the escaped {@link CharSequence}
     * @throws          NullPointerException if the input {@link CharSequence} is {@code null}
     */
    public static CharSequence escapeData(CharSequence cs) {
        return Strings.escape(cs, dataCharMapper);
    }

    /**
     * Append characters to an {@link Appendable}, escaping characters for use in XML with the
     * default set of character mappings.
     * Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   a       the {@link Appendable} (e.g. a {@link StringBuilder})
     * @param   cs      the source {@link CharSequence}
     * @throws  IOException if thrown by the {@link Appendable}
     */
    public static void appendEscaped(Appendable a, CharSequence cs) throws IOException {
        Strings.appendEscaped(a, cs, defaultCharMapper);
    }

    /**
     * Append characters to an {@link Appendable}, escaping characters for use in XML including
     * apostrophe.  Specifically, this method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt><tt>&quot;</tt> (double quote)</dt>
     * <dd><tt>&amp;quot;</tt></dd>
     * <dt><tt>&apos;</tt> (apostrophe)</dt>
     * <dd><tt>&amp;apos;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   a       the {@link Appendable} (e.g. a {@link StringBuilder})
     * @param   cs      the source {@link CharSequence}
     * @throws  IOException if thrown by the {@link Appendable}
     */
    public static void appendEscapedAll(Appendable a, CharSequence cs) throws IOException {
        Strings.appendEscaped(a, cs, allCharMapper);
    }

    /**
     * Append characters to an {@link Appendable}, escaping characters for use in XML with only
     * the character mappings required for element content (not attributes).  Specifically, this
     * method converts:
     * <dl>
     * <dt><tt>&lt;</tt> (less than)</dt>
     * <dd><tt>&amp;lt;</tt></dd>
     * <dt><tt>&gt;</tt> (greater than)</dt>
     * <dd><tt>&amp;gt;</tt></dd>
     * <dt><tt>&amp;</tt> (ampersand)</dt>
     * <dd><tt>&amp;amp;</tt></dd>
     * <dt>Characters less than 0x20 (except for 0x09, 0x0A, 0x0D) or greater than 0x7E</dt>
     * <dd><tt>&amp;#<i>nnn</i>;</tt> (where <i>nnn</i> is the code position in decimal)</dd>
     * </dl>
     *
     * @param   a       the {@link Appendable} (e.g. a {@link StringBuilder})
     * @param   cs      the source {@link CharSequence}
     * @throws  IOException if thrown by the {@link Appendable}
     */
    public static void appendEscapedData(Appendable a, CharSequence cs) throws IOException {
        Strings.appendEscaped(a, cs, dataCharMapper);
    }

    /**
     * Unescape a string escaped with XML character or entity references.
     *
     * @param   s       the string to be unescaped
     * @return          the unescaped string
     */
    public static String unescape(String s) {
        return Strings.unescape(s, unmapper);
    }

    /**
     * Trim leading and trailing white space characters from a {@link String}, using the
     * definition of white space in the XML specification.
     *
     * @param   s       the {@link String} to be trimmed
     * @return          the trimmed {@link String}
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String trim(String s) {
        return Strings.trim(s, spaceTest);
    }

    /**
     * Trim leading and trailing white space characters from a {@link CharSequence}, using the
     * definition of white space in the XML specification.
     *
     * @param   cs      the {@link CharSequence} to be trimmed
     * @return          the trimmed {@link CharSequence}
     * @throws          NullPointerException if the input {@link CharSequence} is {@code null}
     */
    public static CharSequence trim(CharSequence cs) {
        return Strings.trim(cs, spaceTest);
    }

    /**
     * Tests whether a {@link CharSequence} (a {@link String}, {@link StringBuilder} etc.) is
     * comprised entirely of white space characters, using the definition of white space in the
     * XML specification.
     *
     * @param   cs      the {@link CharSequence}
     * @return          {@code true} if the contents are all space characters
     * @throws          NullPointerException if the input {@link CharSequence} is {@code null}
     */
    public static boolean isAllWhiteSpace(CharSequence cs) {
        for (int i = 0, n = cs.length(); i < n; i++)
            if (!isWhiteSpace(cs.charAt(i)))
                return false;
        return true;
    }

    /**
     * Tests whether a Unicode code point is a white space character, using the definition of
     * white space in the XML specification.
     *
     * @param   cp      the code point to be tested
     * @return          {@code true} if the code point is a white space character
     */
    public static boolean isWhiteSpace(int cp) {
        // the first comparison gives an immediate 'false' for characters > space
        // the second gives 'true' for space itself
        // the most common case will be decided with 1 comparison; the second most common, 2
        return cp <= ' ' && (cp == ' ' || cp == '\n' || cp == '\t' || cp == '\r');
    }

    /**
     * Split a string into white space delimited tokens, using the definition of white space in
     * the XML specification.
     *
     * @param   s       the string to be split
     * @return          an array of tokens
     * @throws          NullPointerException if the input string is {@code null}
     */
    public static String[] split(String s) {
        return split(s, 0, s.length());
    }

    /**
     * Split a portion of a string into white space delimited tokens, using the definition of
     * white space in the XML specification.
     *
     * @param   s       the string to be split
     * @param   start   the start index of the portion to be examined
     * @param   end     the end index (exclusive) of the portion to be examined
     * @return          an array of tokens
     * @throws          NullPointerException if the input string is {@code null}
     * @throws          StringIndexOutOfBoundsException if {@code start} or {@code end} is
     *                  invalid
     */
    public static String[] split(String s, int start, int end) {
        return Strings.split(s, start, end, spaceTest);
    }

    /**
     * Get an {@link ElementIterator} to iterate over the element contents of the given
     * {@link Node}.
     *
     * @param   parent  the parent {@link Node}
     * @return          the {@link ElementIterator}
     */
    public static ElementIterator elementIterator(Node parent) {
        return new ElementIterator(parent);
    }

    /**
     * Test whether an {@link Element} matches a specified tag name.
     *
     * @param   elem    the {@link Element}
     * @param   tagName the tag name
     * @return          {@code true} if the names match
     */
    public static boolean match(Element elem, String tagName) {
        return Objects.equals(elem.getTagName(), tagName);
    }

    /**
     * Test whether an {@link Element} matches a specified name and namespace URI.
     *
     * @param   elem            the {@link Element}
     * @param   localName       the local portion of the tag name
     * @param   namespaceURI    the namespace URI
     * @return                  {@code true} if the names match
     */
    public static boolean matchNS(Element elem, String localName, String namespaceURI) {
        return Objects.equals(elem.getLocalName(), localName) &&
                Objects.equals(elem.getNamespaceURI(), namespaceURI);
    }

    /**
     * Get the root element of a document, and check that the element name is correct.
     *
     * @param   document    the document
     * @param   name        the expected root element name
     * @param   nsuri       the namespace URI
     * @return              the root element
     * @throws  XMLException if the root element name is incorrect
     */
    public static Element getDocumentElement(Document document, String name, String nsuri) {
        Element root = document.getDocumentElement();
        if (root == null)
            throw new XMLException("Missing document element");
        if (!matchNS(root, name, nsuri))
            throw new XMLException("Incorrect document element: <" + root.getTagName() + '>');
        return root;
    }

    /**
     * Get the root element of a document, and check that the element name is correct.
     *
     * @param   document    the document
     * @param   name        the expected root element name
     * @return              the root element
     * @throws  XMLException if the root element name is incorrect
     */
    public static Element getDocumentElement(Document document, String name) {
        Element root = document.getDocumentElement();
        if (root == null)
            throw new XMLException("Missing document element");
        if (!match(root, name))
            throw new XMLException("Incorrect document element: <" + root.getTagName() + '>');
        return root;
    }

    /**
     * Create an "unrecognized element" exception.
     *
     * @param   elem    the element which was not recognized
     * @return          the exception
     */
    public static XMLException unrecognizedElement(Element elem) {
        StringBuilder sb = new StringBuilder("Unrecognized element <");
        sb.append(elem.getTagName());
        sb.append('>');
        Node parent = elem.getParentNode();
        if (parent != null && parent instanceof Element) {
            sb.append(" in <");
            sb.append(((Element)parent).getTagName());
            sb.append('>');
        }
        return new XMLException(sb.toString());
    }

    /**
     * Check that a node has no significant content.  This method checks that each child node of
     * the given node is either a comment node or a whitespace-only text node.
     *
     * @param   node    the node
     * @throws  XMLException if the node has child nodes that are not comment
     *                       nodes or empty text nodes
     */
    public static void checkChildNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; ++i)
            checkNode(children.item(i));
    }

    /**
     * Check that a node is a comment node or a whitespace-only text node.  When iterating
     * through a list of nodes to find the element nodes, it is useful to be able to confirm
     * that the non-element nodes are valid.  This method checks that those nodes are just
     * comment nodes or text nodes composed entirely of whitespace.
     *
     * @param   node    the node
     * @throws  XMLException if the node is not a comment or an empty text node
     */
    public static void checkNode(Node node) {
        if (node instanceof Comment)
            return;
        if (node instanceof Text)
            checkText((Text)node);
        else {
            String message = "Incorrect node type";
            Node parent = node.getParentNode();
            if (parent != null && parent instanceof Element)
                message = message + " in <" + ((Element)parent).getTagName() + '>';
            throw new XMLException(message);
        }
    }

    /**
     * Check that a Text node contains only whitespace.
     *
     * @param   text    the Text node
     * @throws  XMLException if the node is not empty
     */
    public static void checkText(Text text) {
        String data = text.getData();
        for (int i = 0, n = data.length(); i < n; ++i) {
            char ch = data.charAt(i);
            if (!isWhiteSpace(ch)) {
                String message = "No text allowed";
                Node parent = text.getParentNode();
                if (parent != null && parent instanceof Element)
                    message = message + " in <" + ((Element)parent).getTagName() + '>';
                throw new XMLException(message);
            }
        }
    }

}
