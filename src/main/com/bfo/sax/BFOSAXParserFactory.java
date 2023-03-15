package com.bfo.sax;

import javax.xml.parsers.*;
import javax.xml.validation.Schema;
import org.xml.sax.*;
import java.util.*;
import java.net.*;
import java.io.*;

public class BFOSAXParserFactory extends SAXParserFactory {

    private final BFOXMLReader FEATUREHOLDER = new BFOXMLReader(this);
    boolean xercescompat = true;

    public static final String FEATURE_THREADS = "http://bfo.com/sax/features/threads";

    public List<String> getSupportedFeatures() {
        List<String> l = new ArrayList<String>();
        l.add("http://xml.org/sax/features/namespaces");
        l.add("http://xml.org/sax/features/namespace-prefixes");
        l.add("http://xml.org/sax/features/use-entity-resolver2");
        l.add(FEATURE_THREADS);
        return Collections.<String>unmodifiableList(l);
    }

    @Override public boolean getFeature(String name)  throws SAXNotRecognizedException, SAXNotSupportedException {
        return FEATUREHOLDER.getFeature(name);
    }
    @Override public Schema getSchema() {
        return null;
    }
    @Override public boolean isNamespaceAware() {
        return true;
    }
    @Override public boolean isValidating() {
        return false;
    }
    @Override public boolean isXIncludeAware() {
        return false;
    }
    @Override public SAXParser newSAXParser() {
        BFOSAXParser sax = new BFOSAXParser(this);
        for (String s : getSupportedFeatures()) {
            try {
                sax.getXMLReader().setFeature(s, FEATUREHOLDER.getFeature(s));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sax;
    }
    @Override public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        FEATUREHOLDER.setFeature(name, value);
    }
    @Override public void setNamespaceAware(boolean awareness) {
        if (!awareness) {
            throw new UnsupportedOperationException("namespaces are required");
        }
    }
    @Override public void setSchema(Schema schema) {
        if (schema != null) {
            throw new UnsupportedOperationException("validating not supported");
        }
    }
    @Override public void setValidating(boolean validating) {
        if (validating) {
            throw new UnsupportedOperationException("validating not supported");
        }
    }
    @Override public void setXIncludeAware(boolean state) {
        if (state) {
            throw new UnsupportedOperationException("xinclude not supported");
        }
    }

    //----------------------------

    private final Map<DTD,DTD> dtdcache = new HashMap<DTD,DTD>();

    InputSource resolveEntity(String publicid, String resolvedSystemId) throws IOException {
        if (resolvedSystemId != null) {
            try {
                URL url = new URL(resolvedSystemId);
                InputStream in = url.openStream();
                if (in instanceof FileInputStream) {
                    in = new BufferedInputStream(in);
                }
                InputSource source = new InputSource(in);
                source.setPublicId(publicid);
                source.setSystemId(resolvedSystemId);
                return source;
            } catch  (MalformedURLException e) {
                throw new IOException("Invalid absolute URL " + BFOXMLReader.fmt(resolvedSystemId));
            }
        }
        return null;
    }

    DTD findDTD(DTD dtd) {
        synchronized(dtdcache) {
            return dtdcache.get(dtd);
        }
    }

    void addDTD(DTD dtd) {
        synchronized(dtdcache) {
            dtdcache.put(dtd,dtd);
        }
    }

    static String resolve(String base, String systemid) {
//        System.out.println("RESOLVE: base="+BFOXMLReader.fmt(base)+" SYS="+BFOXMLReader.fmt(systemid));
        if (systemid == null) {
            return null;
        }
        try {
            String out;
            if (base == null) {
                out = systemid;
            } else {
                URI uri = new URI(base);
                if (!uri.isAbsolute()) {
                    uri = new File("").toURI().resolve(uri);
                }
                out = uri.resolve(systemid).toString();
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
