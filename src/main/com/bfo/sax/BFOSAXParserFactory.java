package com.bfo.sax;

import javax.xml.parsers.*;
import javax.xml.validation.Schema;
import org.xml.sax.*;
import javax.xml.XMLConstants;
import java.util.*;
import java.net.*;
import java.io.*;

public class BFOSAXParserFactory extends SAXParserFactory {

    private final BFOXMLReader FEATUREHOLDER = new BFOXMLReader(this);
    boolean xercescompat = true;
    private Cache cache = new Cache();

    /**
     * This feature determines whether parsing takes place in a secondary thread. The default is true
     */
    public static final String FEATURE_THREADS = "http://bfo.com/sax/features/threads";

    /**
     * This feature determines whether DTDs and other external entities are cached, based on their checksum if necessary. The default is true
     */
    public static final String FEATURE_CACHE = "http://bfo.com/sax/features/cache";

    /**
     * This feature determines whether two DTDs with the same public Id are assumed to be identical and unchanging for caching purposes. The default is true
     */
    public static final String FEATURE_CACHE_PUBLICID = "http://bfo.com/sax/features/cache-publicid";


    public List<String> getSupportedFeatures() {
        List<String> l = new ArrayList<String>();
        l.add("http://xml.org/sax/features/namespaces");
        l.add("http://xml.org/sax/features/validation");
        l.add("http://xml.org/sax/features/external-general-entities");
        l.add("http://xml.org/sax/features/external-parameter-entities");
        l.add("http://xml.org/sax/features/string-interning");
        l.add("http://xml.org/sax/features/namespace-prefixes");
        l.add("http://xml.org/sax/features/use-entity-resolver2");
        l.add("http://apache.org/xml/features/nonvalidating/load-external-dtd");
        l.add("http://apache.org/xml/features/disallow-doctype-dec");
        l.add(FEATURE_CACHE);
        l.add(FEATURE_CACHE_PUBLICID);
        l.add(FEATURE_THREADS);
        l.add(XMLConstants.FEATURE_SECURE_PROCESSING);
        return Collections.<String>unmodifiableList(l);
    }

    public List<String> getSupportedProperties() {
        List<String> l = new ArrayList<String>();
        l.add("http://xml.org/sax/properties/lexical-handler");
        l.add("http://xml.org/sax/properties/declaration-handler");
        l.add("http://xml.org/sax/properties/document-xml-version");
        l.add("http://apache.org/xml/properties/input-buffer-size");
        l.add("http://javax.xml.XMLConstants/property/accessExternalDTD");
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

    InputSourceURN resolveEntity(String publicid, String resolvedSystemId, BFOXMLReader xml) throws SAXException, IOException {
        if (resolvedSystemId != null) {
            try {
                final URL furl = new URL(resolvedSystemId);
                String scheme = furl.getProtocol();
                String urn = null;

                // Check if we're allowed to access this URL
                try {
                    String allowedSchemes = (String)xml.getProperty("http://javax.xml.XMLConstants/property/accessExternalDTD");
                    if (allowedSchemes == null) {
                        allowedSchemes = System.getProperty("javax.xml.accessExternalDTD");
                        if (allowedSchemes == null) {
                            allowedSchemes = "all";
                        }
                    }
                    if (scheme.equals("https")) {
                        scheme = "http";
                    }
                    if (allowedSchemes.length() == 0) {
                        xml.error(null, "External entity load from " + resolvedSystemId + " disallowed by \"http://javax.xml.XMLConstants/property/accessExternalDTD\"");
                    } else if (!allowedSchemes.equals("all")) {
                        List<String> l = Arrays.asList(allowedSchemes.split(","));
                        if (!l.contains(scheme) && !(scheme.equals("http") && l.contains("https"))) {
                            xml.error(null, "External entity load from " + resolvedSystemId + " disallowed by \"http://javax.xml.XMLConstants/property/accessExternalDTD\"");
                        }
                    }
                } catch (SAXNotRecognizedException e) {
                    throw new SAXException(e);
                }

                if (scheme.equals("file")) {
                    // File URLs are permanently identified by their URL and their last modified time
                    try {
                        final File file = new File(furl.toURI());
                        urn = furl + "#lastModified=" + file.lastModified();
                    } catch (Exception e) {
                        throw new SAXException(e);
                    }
                } else if (scheme.equals("jar")) {
                    // Jar URLs are permanently identified by their URL - they never change
                    urn = furl.toString();
                } else {
                    // Other URLs need to be checksummed to identify changes
                    urn = null;
                }

                // New InputSource that opens the requested stream on demand,
                // because we're hoping this will not be required due to caching
                InputSourceURN source = new InputSourceURN() {
                    @Override public InputStream getByteStream() {
                        try {
                            InputStream in = super.getByteStream();
                            if (in == null) {
                                URL url = furl;
                                URLConnection con = url.openConnection();
                                if (con instanceof HttpURLConnection) {
                                    // Because Java can't redirect from HTTP to HTTPS
                                    HttpURLConnection hcon = (HttpURLConnection)con;
                                    hcon.setRequestProperty("User-Agent", "https://github.com/faceless2/sax on Java/" + System.getProperty("java.version"));
                                    hcon.setInstanceFollowRedirects(false);
                                    hcon.connect();
                                    int count = 0, code;
                                    while (((code=hcon.getResponseCode()) == 301 || code == 302) && ++count < 8) {
                                        String location = hcon.getHeaderField("Location");
                                        if (location == null) {
                                            throw new IOException("Invalid " + code + "redirect (no Location)");
                                        }
                                        URL url2 = new URL(url, location);
                                        String scheme = url2 == null ? null : url2.getProtocol();
                                        if (!"http".equals(scheme) && !"https".equals(scheme)) {
                                            throw new IOException("Invalid " + code + "redirect to \"" + location + "\"");
                                        } else {
                                            url = url2;
                                            hcon.disconnect();
                                            hcon = (HttpURLConnection)url.openConnection();
                                            con = hcon;
                                        }
                                    }
                                }
                                in = con.getInputStream();
                                if (in instanceof FileInputStream) {
                                    in = new BufferedInputStream(in);
                                }
                                setByteStream(in);
                            }
                            return in;
                        } catch (IOException e) {
                            // This will be unpacked and thrown on as IOException
                            throw new RuntimeException(e);
                        }
                    }
                };
                source.setPublicId(publicid);
                source.setSystemId(resolvedSystemId);
                source.setURN(urn);
                if (urn == null) {
                    // Unpack immediately
                    try {
                        source.getByteStream();
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof IOException) {
                            throw (IOException)e.getCause();
                        }
                        throw e;
                    }
                }
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

    Cache getCache() {
        return cache;
    }

    String message(Locale locale, Object... msg) {
        ResourceBundle bundle = ResourceBundle.getBundle("com.bfo.sax.data.Messages", locale);
        String message = (String)msg[0];
        try {
            if (bundle.containsKey(message)) {
                String template = bundle.getString(message);
                boolean found = true;
                for (int i=1;i<msg.length && found;i++) {
                    found = false;
                    int j;
                    while ((j=template.indexOf("{" + (i - 1) + "}")) >= 0) {
                        Object t = msg[i];
                        if (t instanceof Integer) {
                            t = Integer.toHexString((Integer)t);
                        }
                        template = template.substring(0, j) + t + template.substring(j + 3);
                        found = true;
                    }
                }
                message = template;
            }
        } catch (Exception e) {}
        return message;
    }

}
