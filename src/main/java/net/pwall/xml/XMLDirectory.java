/*
 * @(#) XMLDirectory.java
 */

package net.pwall.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.TimeZone;

import net.pwall.util.ISO8601Date;
import net.pwall.util.UserError;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Simple program to produce a file system directory listing in XML form.
 *
 * @author Peter Wall
 */
public class XMLDirectory implements Runnable {

    public static final String defaultEncoding = "UTF-8";

    public static final String directoryListElemName = "directory-list";
    public static final String directoryElemName = "directory";
    public static final String fileElemName = "file";
    public static final String pathAttrName = "path";
    public static final String titleAttrName = "title";
    public static final String nameAttrName = "name";
    public static final String hiddenAttrName = "hidden";
    public static final String modifiedAttrName = "modified";
    public static final String lengthAttrName = "length";
    public static final String trueValue = "yes";
    public static final String cdataType = "CDATA";
    public static final String nullStr = "";

    private String namespaceURI;
    private String namespacePrefix;
    private File dir;
    private OutputStream out;
    private String xslt;
    private String encoding;
    private String title;
    private int indent;

    public XMLDirectory() {
        namespaceURI = null;
        namespacePrefix = null;
        dir = null;
        out = null;
        xslt = null;
        encoding = null;
        title = null;
        indent = -1;
    }

    @Override
    public void run() {
        if (getNamespacePrefix() != null && getNamespaceURI() == null)
            throw new UserError("Namespace prefix with no namespace URI");
        run(out != null ? out : System.out);
    }

    private void run(OutputStream out) {
        try {
            XMLFormatter formatter = new XMLFormatter(out, XMLFormatter.Whitespace.INDENT);
            try {
                if (indent >= 0)
                    formatter.setIndent(indent);
                if (dir == null)
                    dir = new File(".");
                formatter.setEncoding(encoding != null ? encoding : defaultEncoding);
                formatter.prefix();
                formatter.xslProcessingInstruction(xslt);
                formatter.startDocument();
                AttributesImpl attributes = new AttributesImpl();
                addAttribute(attributes, pathAttrName, dir.getAbsolutePath());
                if (title != null)
                    addAttribute(attributes, titleAttrName, title);
                if (namespaceURI != null)
                    formatter.startPrefixMapping(namespacePrefix == null ? "" : namespacePrefix,
                            namespaceURI);
                startElement(formatter, directoryListElemName, attributes);
                outputDirectory(formatter, dir);
                endElement(formatter, directoryListElemName);
                if (namespaceURI != null)
                    formatter.endPrefixMapping(namespacePrefix == null ? "" : namespacePrefix);
                formatter.endDocument();
            }
            catch (Exception e) {
                throw new RuntimeException("Unexpected exception in XMLDirectory", e);
            }
            finally {
                formatter.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void outputDirectory(ContentHandler ch, File dir) throws SAXException {
        AttributesImpl attributes = baseAttributes(dir);
        startElement(ch, directoryElemName, attributes);
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory())
                outputDirectory(ch, file);
            else
                outputFile(ch, file);
        }
        endElement(ch, directoryElemName);
    }

    private void outputFile(ContentHandler ch, File file) throws SAXException {
        AttributesImpl attributes = baseAttributes(file);
        addAttribute(attributes, lengthAttrName, String.valueOf(file.length()));
        startElement(ch, fileElemName, attributes);
        endElement(ch, fileElemName);
    }

    private static AttributesImpl baseAttributes(File file) {
        AttributesImpl attributes = new AttributesImpl();
        addAttribute(attributes, nameAttrName, file.getName());
        if (file.isHidden())
            addAttribute(attributes, hiddenAttrName, trueValue);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(file.lastModified());
        String dateString = ISO8601Date.toString(cal, true,
                ISO8601Date.YEAR_MASK | ISO8601Date.MONTH_MASK | ISO8601Date.DAY_OF_MONTH_MASK |
                ISO8601Date.HOUR_OF_DAY_MASK | ISO8601Date.MINUTE_MASK |
                ISO8601Date.SECOND_MASK | ISO8601Date.ZONE_OFFSET_MASK);
        addAttribute(attributes, modifiedAttrName, dateString);
        // what about Unix permissions, owner and group?
        return attributes;
    }

    private void startElement(ContentHandler ch, String name, Attributes attributes)
            throws SAXException {
        ch.startElement(namespaceURI == null ? nullStr : namespaceURI, name,
                namespacePrefix == null ? name : namespacePrefix + ':' + name, attributes);
    }

    private void endElement(ContentHandler ch, String name) throws SAXException {
        ch.endElement(namespaceURI == null ? nullStr : namespaceURI, name,
                namespacePrefix == null ? name : namespacePrefix + ':' + name);
    }

    private static void addAttribute(AttributesImpl attributes, String qName, String value) {
        attributes.addAttribute(nullStr, nullStr, qName, cdataType, value);
    }

    public String getNamespaceURI() {
        return namespaceURI;
    }

    public void setNamespaceURI(String namespaceURI) {
        this.namespaceURI = namespaceURI;
    }

    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    public void setNamespacePrefix(String namespacePrefix) {
        this.namespacePrefix = namespacePrefix;
    }

    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public OutputStream getOut() {
        return out;
    }

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public String getXslt() {
        return xslt;
    }

    public void setXslt(String xslt) {
        this.xslt = xslt;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    /**
     * Main method.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            XMLDirectory instance = new XMLDirectory();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-dir")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-dir with no pathname");
                    if (instance.getDir() != null)
                        throw new UserError("Duplicate -dir");
                    File dir = new File(args[i]);
                    if (!dir.exists() || !dir.isDirectory())
                        throw new UserError("-dir file does not exist - " + args[i]);
                    instance.setDir(dir);
                }
                else if (arg.equals("-out")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-out with no pathname");
                    if (instance.getOut() != null)
                        throw new UserError("Duplicate -out");
                    try {
                        instance.setOut(new FileOutputStream(args[i]));
                    }
                    catch (IOException ioe) {
                        throw new UserError("Can't open output - " + args[i]);
                    }
                }
                else if (arg.equals("-xslt")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-xslt with no URL");
                    if (instance.getXslt() != null)
                        throw new UserError("Duplicate -xslt");
                    instance.setXslt(args[i]);
                }
                else if (arg.equals("-encoding")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-encoding with no name");
                    if (instance.getEncoding() != null)
                        throw new UserError("Duplicate -encoding");
                    instance.setEncoding(args[i]);
                }
                else if (arg.equals("-title")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-title with no value");
                    if (instance.getTitle() != null)
                        throw new UserError("Duplicate -title");
                    instance.setTitle(args[i]);
                }
                else if (arg.equals("-indent")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-indent with no value");
                    if (instance.getIndent() >= 0)
                        throw new UserError("Duplicate -indent");
                    try {
                        int n = Integer.parseInt(args[i]);
                        if (n < 0 || n > 100)
                            throw new UserError("Illegal -indent - " + args[i]);
                        instance.setIndent(n);
                    }
                    catch (NumberFormatException e) {
                        throw new UserError("Illegal -indent - " + args[i]);
                    }
                }
                else if (arg.equals("-nsuri")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-nsuri with no value");
                    if (instance.getNamespaceURI() != null)
                        throw new UserError("Duplicate -nsuri");
                    instance.setNamespaceURI(args[i]);
                }
                else if (arg.equals("-nspfx")) {
                    if (++i >= args.length || args[i].startsWith("-"))
                        throw new UserError("-nspfx with no value");
                    if (instance.getNamespacePrefix() != null)
                        throw new UserError("Duplicate -nspfx");
                    instance.setNamespacePrefix(args[i]);
                }
                else
                    throw new UserError("Unrecognised argument - " + arg);
            }
            instance.run();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
