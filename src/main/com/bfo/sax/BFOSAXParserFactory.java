package com.bfo.sax;

import javax.xml.parsers.*;
import javax.xml.validation.Schema;
import org.xml.sax.*;
import java.util.*;
import java.net.*;
import java.io.*;

public class BFOSAXParserFactory extends SAXParserFactory {

    private boolean entityResolver2 = true;
    boolean xercescompat = true;

    @Override public boolean getFeature(String name)  throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            return true;
        } else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
            return false;
        } else if ("http://xml.org/sax/features/use-entity-resolver2".equals(name)) {
            return entityResolver2;
        } else {
            return false;
        }
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
        try {
            sax.getXMLReader().setFeature("http://xml.org/sax/features/use-entity-resolver2", getFeature("http://xml.org/sax/features/use-entity-resolver2"));
        } catch (Exception e) {}
        return sax;
    }
    @Override public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            if (value == false) {
                throw new SAXNotSupportedException(name + " only supports true");
            }
        } else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
            if (value == false) {
                throw new SAXNotSupportedException(name + " only supports false");
            }
        } else if ("http://xml.org/sax/features/use-entity-resolver2".equals(name)) {
            entityResolver2 = value; 
        } else {
            throw new SAXNotRecognizedException(name);
        }
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
            if (out != null && out.startsWith("file:/") && !out.startsWith("file:///")) {
                out = "file:///" + out.substring(6);
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
