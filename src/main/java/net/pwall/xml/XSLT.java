/*
 * @(#) XSLT.java
 */

package net.pwall.xml;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.pwall.util.UserError;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Simple XSLT class.  Perform an XSLT transform from the command line, or using arguments
 * provided by dependency injection.
 *
 * @author Peter Wall
 */
public class XSLT implements Runnable {

    private static TransformerFactory transformerFactory = null;

    private File inFile = null;
    private File outFile = null;
    private File xsltFile = null;
    private File rootDir = null;
    private Map<String, String> params = null;

    @Override
    public void run() {
        if (xsltFile == null)
            throw new UserError("XSLT - no xslt file specified");
        try {
            Templates templates =
                    getTransformerFactory().newTemplates(new StreamSource(xsltFile));
            Transformer transformer = templates.newTransformer();
            if (rootDir != null)
                transformer.setURIResolver(new Resolver(rootDir));
            if (params != null)
                for (Map.Entry<String, String> param : params.entrySet())
                    transformer.setParameter(param.getKey(), param.getValue());
//            StreamSource input = inFile != null ? new StreamSource(inFile) :
//                    new StreamSource(System.in);
//            StreamResult output = outFile != null ? new StreamResult(outFile) :
//                    new StreamResult(System.out);
//            transformer.transform(input, output);
            if (inFile != null) {
                try (InputStream is = new BufferedInputStream(new FileInputStream(inFile))) {
                    transform(transformer, is);
                }
            }
            else
                transform(transformer, System.in);
        }
        catch (Exception e) {
            throw new RuntimeException("Transformer exception: " + e.getMessage(), e);
        }
    }

    private void transform(Transformer transformer, InputStream is) throws Exception {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                return new InputSource(new ByteArrayInputStream(new byte[0]));
            }
        });
        reader.setFeature("http://xml.org/sax/features/validation", false);
        reader.setFeature("http://xml.org/sax/features/resolve-dtd-uris", false);
        SAXSource input = new SAXSource(reader, new InputSource(is));
        StreamResult output = outFile != null ? new StreamResult(outFile) :
                new StreamResult(System.out);
        transformer.transform(input, output);
    }

    public static synchronized TransformerFactory getTransformerFactory() {
        if (transformerFactory == null)
            transformerFactory = TransformerFactory.newInstance();
        return transformerFactory;
    }

    /**
     * Main method - check arguments and call XSLT transform.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            XSLT xslt = new XSLT();
            for (int i = 0; i < args.length; ++i) {
                String arg = args[i];
                if (arg.equals("-in")) {
                    if (++i >= args.length)
                        throw new UserError("-in with no filename");
                    if (xslt.getInFile() != null)
                        throw new UserError("Duplicate -in");
                    File inFile = new File(args[i]);
                    if (!inFile.exists() || !inFile.isFile())
                        throw new UserError("-in file does not exist - " + args[i]);
                    xslt.setInFile(inFile);
                }
                else if (arg.equals("-xslt")) {
                    if (++i >= args.length)
                        throw new UserError("-xslt with no filename");
                    if (xslt.getXsltFile() != null)
                        throw new UserError("Duplicate -xslt");
                    File xsltFile = new File(args[i]);
                    if (!xsltFile.exists() || !xsltFile.isFile())
                        throw new UserError("-xslt file does not exist - " + args[i]);
                    xslt.setXsltFile(xsltFile);
                }
                else if (arg.equals("-out")) {
                    if (++i >= args.length)
                        throw new UserError("-out with no filename");
                    if (xslt.getOutFile() != null)
                        throw new UserError("Duplicate -out");
                    xslt.setOutFile(new File(args[i]));
                }
                else if (arg.equals("-root")) {
                    if (++i >= args.length)
                        throw new UserError("-root with no filename");
                    if (xslt.getRootDir() != null)
                        throw new UserError("Duplicate -root");
                    File rootFile = new File(args[i]);
                    if (!rootFile.exists() || !rootFile.isDirectory())
                        throw new UserError("-root directory does not exist - " + args[i]);
                    xslt.setRootDir(rootFile);
                }
                else if (arg.startsWith("-D")) {
                    int j = arg.indexOf('=', 2);
                    if (j < 0)
                        throw new UserError("Incorrect param argument - " + arg);
                    String key = arg.substring(2, j);
                    if (xslt.getParam(key) != null)
                        throw new UserError("Duplicate param argument - " + arg);
                    xslt.setParam(key, arg.substring(j + 1));
                }
                else
                    throw new UserError("Unrecognised argument - " + arg);
            }
            xslt.run();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public File getInFile() {
        return inFile;
    }

    public void setInFile(File inFile) {
        this.inFile = inFile;
    }

    public File getOutFile() {
        return outFile;
    }

    public void setOutFile(File outFile) {
        this.outFile = outFile;
    }

    public File getXsltFile() {
        return xsltFile;
    }

    public void setXsltFile(File xsltFile) {
        this.xsltFile = xsltFile;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public String getParam(String key) {
        return params == null ? null : params.get(key);
    }

    public void setParam(String key, String value) {
        if (params == null)
            params = new HashMap<>();
        params.put(key, value);
    }

    public static class Resolver implements URIResolver {

        private File root;

        public Resolver(File root) {
            this.root = root;
        }

        @Override
        public Source resolve(String href, String base) {
            if (href.startsWith("/")) {
                File file = new File(root.getAbsolutePath() + href);
                if (file.exists())
                    return new StreamSource(file);
            }
            return null;
        }

    }

}
