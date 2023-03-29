package com.bfo.sax;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import org.xml.sax.*;
import org.xml.sax.ext.*;

public class BFOXMLReader implements XMLReader, Locator, Location {

    final Logger cachelog = Logger.getLogger("com.bfo.sax.Cache");
    private int c, len;
    private char[] buf;
    private CPReader curreader;
    private int inputBufferSize;
    private boolean standalone;                                 // TODO
    private boolean featureThreads = true;
    private boolean featureNamespaces = true;
    private boolean featureNamespacePrefixes = false;            // xerces java internal defaults to true
    private boolean featureEntityResolver2 = true;
    private boolean featureLoadExternalDTD = true;
    private boolean featureExternalGeneralEntities = true;
    private boolean featureExternalParameterEntities = true;
    private boolean featureSecureProcessing = false;
    private boolean featureInternStrings = false;
    private boolean featureCache = true;
    private boolean featureCachePublicId = true;  // xerces does, so we do
    private boolean featureDisallowDoctype = false;
    private String externalPrefixes = null; // null will check system property
    private int parameterEntityExpansionCount, maxParameterEntityExpansionCount;
    private int generalEntityExpansionCount, maxGeneralEntityExpansionCount;
    private Queue q;
    private DTD dtd;
    private BFOXMLStreamReader xmlStreamReader;
    private ContentHandler contentHandler;
    private LexicalHandler lexicalHandler;
    private EntityResolver entityResolver;
    private DeclHandler declHandler;
    private DTDHandler dtdHandler;
    private ErrorHandler errorHandler;
    private List<Context> stack = new ArrayList<Context>();
    private List<Entity> entityStack = new ArrayList<Entity>();
    private BFOSAXParserFactory factory;
    private Locale locale = Locale.getDefault();
    private Map<String,String> internMap = new HashMap<String,String>();

    public BFOXMLReader(BFOSAXParserFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        this.factory = factory;
    }

    @Override public ContentHandler getContentHandler() {
        return contentHandler;
    }
    @Override public DTDHandler getDTDHandler() {
        return dtdHandler;
    }
    @Override public EntityResolver getEntityResolver() {
        return entityResolver;
    }
    @Override public ErrorHandler getErrorHandler() {
        return errorHandler;
    }
    @Override public boolean getFeature(String name) {
       if ("http://xml.org/sax/features/namespaces".equals(name)) {
           return featureNamespaces;
       } else if ("http://xml.org/sax/features/validation".equals(name)) {
           return false;
       } else if ("http://xml.org/sax/features/string-interning".equals(name)) {
           return featureInternStrings;
       } else if ("http://apache.org/xml/features/nonvalidating/load-external-dtd".equals(name)) {
           return featureLoadExternalDTD;
       } else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
           return featureNamespacePrefixes;
       } else if ("http://xml.org/sax/features/external-general-entities".equals(name)) {
           return featureExternalGeneralEntities;
       } else if ("http://xml.org/sax/features/external-parameter-entities".equals(name)) {
           return featureExternalParameterEntities;
       } else if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
           return featureSecureProcessing;
       } else if ("http://xml.org/sax/features/use-entity-resolver2".equals(name)) {
           return featureEntityResolver2;
       } else if ("http://apache.org/xml/features/disallow-doctype-dec".equals(name)) {
           return featureDisallowDoctype;
       } else if (BFOSAXParserFactory.FEATURE_THREADS.equals(name)) {
           return featureThreads;
       } else if (BFOSAXParserFactory.FEATURE_CACHE.equals(name)) {
           return featureCache;
       } else if (BFOSAXParserFactory.FEATURE_CACHE_PUBLICID.equals(name)) {
           return featureCachePublicId;
       } else {
           return false;
       }
    }
    @Override public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
       if ("http://xml.org/sax/features/namespaces".equals(name)) {
           featureNamespaces = value;
       } else if ("http://xml.org/sax/features/validation".equals(name)) {
           if (value != false) {
               throw new SAXNotSupportedException(name + " only supports false");
           }
       } else if ("http://xml.org/sax/features/string-interning".equals(name)) {
           // This is done on a document-wide basis; "interned" strings are dropped
           // at the end of the parse. Element and Attribute localNames, qNames, and URIs
           // are interned - it actually makes good sense for huge documents.
           featureInternStrings = value;
       } else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
           featureNamespacePrefixes = value;
       } else if ("http://xml.org/sax/features/use-entity-resolver2".equals(name)) {
           featureEntityResolver2 = value;
       } else if ("http://xml.org/sax/features/external-general-entities".equals(name)) {
           featureExternalGeneralEntities = value;
       } else if ("http://xml.org/sax/features/external-parameter-entities".equals(name)) {
           featureExternalParameterEntities = value;
       } else if ("http://apache.org/xml/features/nonvalidating/load-external-dtd".equals(name)) {
           featureLoadExternalDTD = value;
       } else if ("http://apache.org/xml/features/disallow-doctype-dec".equals(name)) {
           featureDisallowDoctype = value;
       } else if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name)) {
           // What to do with this? Lets
           //  * limit entities to 1000/10000
           //  * limit timeout to 10s for external loads
           featureSecureProcessing = value;
       } else if (BFOSAXParserFactory.FEATURE_THREADS.equals(name)) {
           featureThreads = value;
       } else if (BFOSAXParserFactory.FEATURE_CACHE.equals(name)) {
           featureCache = value;
       } else if (BFOSAXParserFactory.FEATURE_CACHE_PUBLICID.equals(name)) {
           featureCachePublicId = value;
       } else {
           throw new SAXNotRecognizedException(name);
       }
    }
    @Override public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            return lexicalHandler;
        } else if ("http://xml.org/sax/properties/declaration-handler".equals(name)) {
            return declHandler;
        } else if ("http://xml.org/sax/properties/document-xml-version".equals(name)) {
            return curreader != null && curreader.isXML11();
        } else if ("http://apache.org/xml/properties/input-buffer-size".equals(name)) {
            return inputBufferSize;
        } else if ("http://javax.xml.XMLConstants/property/accessExternalDTD".equals(name)) {
            return externalPrefixes;
        }
        throw new SAXNotRecognizedException(name);
    }
    @Override public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            if (value instanceof LexicalHandler || value == null) {
                lexicalHandler = (LexicalHandler)value;
            } else {
                throw new SAXNotSupportedException(name + " wrong class");
            }
        } else if ("http://xml.org/sax/properties/declaration-handler".equals(name)) {
            declHandler = (DeclHandler)value;
            if (value instanceof DeclHandler || value == null) {
                declHandler = (DeclHandler)value;
            } else {
                throw new SAXNotSupportedException(name + " wrong class");
            }
        } else if ("http://apache.org/xml/properties/input-buffer-size".equals(name)) {
            if (value instanceof Integer) {
                int v = ((Integer)value).intValue();
                inputBufferSize = v <= 0 ? 0 : Math.max(16, v);
            } else {
                throw new SAXNotSupportedException(name + " wrong class");
            }
        } else if ("http://javax.xml.XMLConstants/property/accessExternalDTD".equals(name)) {
            if (value == null) {
                value = "";
            }
            if (value instanceof String) {
                this.externalPrefixes = (String)value;
            } else {
                throw new SAXNotSupportedException(name + " wrong class");
            }
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }
    @Override public void parse(String systemId) throws SAXException, IOException {
        parse(new InputSource(systemId));
    }
    @Override public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
    }
    @Override public void setDTDHandler(DTDHandler handler) {
        this.dtdHandler = handler;
    }
    @Override public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }
    @Override public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    @Override public int getCharacterOffset() {
        // return curreader != null ? curreader.getCharacterOffset() : -1;
        return -1;
    }
    @Override public int getColumnNumber() {
        return curreader != null ? curreader.getColumnNumber() : -1;
    }

    @Override public int getLineNumber() {
        return curreader != null ? curreader.getLineNumber() : -1;
    }

    @Override public String getPublicId() {
        return curreader != null ? curreader.getPublicId() : null;
    }

    @Override public String getSystemId() {
        return curreader != null ? curreader.getSystemId() : null;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"publicid\":");
        sb.append(BFOXMLReader.fmt(getPublicId()));
        sb.append(",\"systemid\":");
        sb.append(BFOXMLReader.fmt(getSystemId()));
        sb.append(",\"line\":");
        sb.append(getLineNumber());
        sb.append(",\"column\":");
        sb.append(getColumnNumber());
        sb.append(",\"class\":\"");
        sb.append(getClass().getName());
        sb.append("\"}");
        return sb.toString();
    }

    void parse(InputSource in, BFOXMLStreamReader xmlStreamReader) throws IOException, SAXException {
        featureEntityResolver2 = true;
        featureThreads = true;
        this.xmlStreamReader = xmlStreamReader;
        DefaultHandler2 dh = new DefaultHandler2();
        setContentHandler(dh);
        lexicalHandler = dh;
        setEntityResolver(dh);
        parse(in);
    }

    @Override public void parse(InputSource in) throws IOException, SAXException {
        if (in == null) {
            throw new IllegalArgumentException("InputSource is null");
        }
        if (q != null) {
            throw new IllegalStateException("Parsing");
        }
        if (featureSecureProcessing) {
            maxParameterEntityExpansionCount = 1000;
            maxGeneralEntityExpansionCount = 1000;
        } else {
            maxParameterEntityExpansionCount = 10000;
            maxGeneralEntityExpansionCount = 100000;
        }
        if (in.getSystemId() != null) {
            String systemId = factory.resolve("", in.getSystemId());
            if (!systemId.equals(in.getSystemId())) {
                InputSource in2 = new InputSource(systemId);
                in2.setPublicId(in.getPublicId());
                in2.setByteStream(in.getByteStream());
                in2.setCharacterStream(in.getCharacterStream());
                in = in2;
            }
        }
        if (in.getCharacterStream() == null && in.getByteStream() == null && in.getSystemId() != null) {
            // Xerces does not call our set handler here.
            InputSource in2 = factory.resolveEntity(in.getPublicId(), in.getSystemId(), this, false);
            if (in2 != null) {
                in = in2;
            }
        }
        if (in.getCharacterStream() == null && in.getByteStream() == null) {
            throw new SAXException("Can't resolve InputSource: no stream");
        }
        EntityResolver entityResolver = this.entityResolver;
        if (!featureEntityResolver2 && entityResolver instanceof EntityResolver2) {
            entityResolver = new EntityResolver() {
                public InputSource resolveEntity(String pubid, String sysid) throws SAXException, IOException {
                    return BFOXMLReader.this.entityResolver.resolveEntity(pubid, sysid);
                }
            };
        }
        curreader = CPReader.getReader("", in.getPublicId(), in.getSystemId(), 1, 1, false);

        try {
            parameterEntityExpansionCount = generalEntityExpansionCount = 0;
            buf = new char[2048];
            c = len = 0;
            if (featureThreads) {
                q = new ThreadedQueue(contentHandler, declHandler, dtdHandler, entityResolver, errorHandler, lexicalHandler);
                final InputSource fin = in;
                final ThreadedQueue tq = (ThreadedQueue)q;
                Runnable r = new Runnable() {
                    public void run() {
                        try {
                            try {
                                if (tq.isContentHandler()) {
                                    tq.setDocumentLocator(BFOXMLReader.this);
                                    tq.startDocument();
                                }
                                curreader = CPReader.normalize(CPReader.getReader(fin), false);
                                readDocument(curreader);
                                if (tq.isContentHandler()) {
                                    tq.endDocument();
                                }
                                tq.close();
                            } catch (SAXParseException e) {
                                // This will push a fatalError to the q and wait;
                                // the main app thread will pop this message, call
                                // fatalError, then throw this exception again.
                                // That will be caught on the main thread, passed back
                                // to this thread where it will trigger an "echo"
                                // exception of some sort on this thread again to
                                // halt.
                                tq.fatalError(e);
                            } catch (Exception e) {
                                // Same for other classes
                                tq.fatalError2(e);
                            }
                        } catch (Exception e) {
                            // All this does is capture the "echo" exception
                        }
                    }
                };
                if (factory.executorService == null) {
                    new Thread(r).start();
                } else {
                    try {
                        factory.executorService.submit(r).get();
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (this.xmlStreamReader != null) {
                    this.xmlStreamReader.init(this, tq);
                } else {
                    tq.run();
                }
            } else {
                q = new DirectQueue(contentHandler, declHandler, dtdHandler, entityResolver, errorHandler, lexicalHandler);

                curreader = CPReader.getReader("", in.getPublicId(), in.getSystemId(), 1, 1, false);
                try {
                    if (q.isContentHandler()) {
                        q.setDocumentLocator(this);
                        q.startDocument();
                    }
                    curreader = CPReader.normalize(CPReader.getReader(in), false);
                    readDocument(curreader);
                    if (q.isContentHandler()) {
                        q.endDocument();
                    }
                } catch (SAXParseException e) {
                    q.fatalError(e);
                    throw e;
                } finally {
                }
            }
        } finally {
            if (xmlStreamReader == null) {
                postParse();
            }
        }
    }

    void postParse() {
        q = null;
        buf = null;
        curreader = null;
        dtd = null;
        stack.clear();
        entityStack.clear();
        internMap.clear();
    }

    private static class Context {
        String name;
        Context parent;
        Map<String,String> map;
        List<String> prefixes;
        Element element;
        Context(String name, Element element, Context parent) {
            this.name = name;
            this.element = element;
            this.parent = parent;
            if (parent == null) {
               this.map = new HashMap<String,String>();
               map.put("", "");
            } else {
                this.map = parent.map;
            }
        }
        void register(String prefix, String uri) {
            if (parent != null && map == parent.map) {
                map = new HashMap<String,String>(parent.map);
            }
            map.put(prefix, uri);
        }
        String namespace(String prefix) {
            if (prefix.equals("xml")) {
                return XMLConstants.XML_NS_URI;
            } else if (prefix.equals("xmlns")) {
                return "";      // Yes, confirmed
            }
            return map.get(prefix);
        }
    }

    private String newString(int start) {
        return new String(buf, start, len - start);
    }

    private void append(int c) {
        if (c < 0) {
            throw new IllegalArgumentException(Integer.toString(c));
        }
        if (c > 0xffff) {
            if (len + 1 >= buf.length) {
                char[] buf2 = new char[len + (len>>1)];
                System.arraycopy(buf, 0, buf2, 0, len);
                buf = buf2;
            }
            int oc = c;
            c = CPReader.toUTF16(c);
            buf[len++] = (char)(c>>16);
            buf[len++] = (char)(c&0xFFFF);
        } else {
            if (len == buf.length) {
                char[] buf2 = new char[len + (len>>1)];
                System.arraycopy(buf, 0, buf2, 0, len);
                buf = buf2;
            }
            buf[len++] = (char)c;
        }
    }

    private void append(CPReader reader) throws IOException, SAXException {
        int c;
        while ((c=reader.read()) >= 0) {
            append(c);
        }
    }

    private void append(String s) {
        if (len + s.length() > buf.length) {
            char[] buf2 = new char[len + Math.max(s.length(), (len>>1))];
            System.arraycopy(buf, 0, buf2, 0, len);
            buf = buf2;
        }
        for (int i=0;i<s.length();i++) {
            buf[len++] = s.charAt(i);
        }
    }

    // PEReference [Parameter entity references] are recognized anywhere in the DTD (internal and external subsets and external parameter entities), except in literals [EntityValue, AttValue, SystemLiteral, PubidLiteral], processing instructions, comments, and the contents of ignored conditional sections (see 3.4 Conditional Sections). They are also recognized in entity value literals [EntityValue]. The use of parameter entities in the internal subset is restricted as described below.

    String error(CPReader reader, String... msg) throws SAXException, IOException {
        if (reader == null) {
            reader = curreader;
        }
        throw new SAXParseException(factory.message(locale, msg), reader == null ? null : reader.getPublicId(), reader == null ? null : reader.getSystemId(), reader == null ? -1 : reader.getLineNumber(), reader == null ? -1 : reader.getColumnNumber());
    }

    static String hex(int c) {
        if (c < 0) {
            return "EOF";
        } else if (c >= 0x20 && c <= 0x7f) {
            return "\"" + ((char)c) + "\"";
        } else {
           return "&#x" + Integer.toHexString(c);
        }
    }

    static boolean isS(int c) {
        return  c == 32 || c == 10 || c == 13 || c == 9;
    }

    static boolean isNameStartChar(int c) {
        if (c == ' ') {
            return false;
        }
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == ':' || c == '_' || c >= 0xC0 && c <= 0xD6 || c >= 0xD8 && c <= 0xF6 || c >= 0xF8 && c <= 0x2FF || c >= 0x370 && c <= 0x37D || c >= 0x37F && c <= 0x1FFF || c >= 0x200C && c <= 0x200D || c >= 0x2070 && c <= 0x218F || c >= 0x2C00 && c <= 0x2FEF || c >= 0x3001 && c <= 0xD7FF || c >= 0xF900 && c <= 0xFDCF || c >= 0xFDF0 && c <= 0xFFFD || c >= 0x10000 && c <= 0xEFFFF;
    }

    static boolean isNameChar(int c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '-' || c == '.' || c >= '0' && c <= '9' || c == ':' || c == '_' || c == 0xB7 || c >= 0xC0 && c <= 0xD6 || c >= 0xD8 && c <= 0xF6 || c >= 0xF8 && c <= 0x2FF || c >= 0x300 && c <= 0x37D || c >= 0x37F && c <= 0x1FFF || c >= 0x200C && c <= 0x200D || c >= 0x203F && c <= 0x2040 || c >= 0x2070 && c <= 0x218F || c >= 0x2C00 && c <= 0x2FEF || c >= 0x3001 && c <= 0xD7FF || c >= 0xF900 && c <= 0xFDCF || c >= 0xFDF0 && c <= 0xFFFD || c >= 0x10000 && c <= 0xEFFFF;
    }

    private void readS(final CPReader reader) throws SAXException, IOException {
        if (c != 32 && c != 10 && c != 13 && c != 9) {
            error(reader, "Expected whitespace, got " + hex(c));
        }
        do {
            c = reader.read();
        } while (c == 32 || c == 10 || c == 13 || c == 9);
    }

    /**
     * Read a name. Cursor is after first char of name. On exit cursor is after last char in name, ie on space etc
     */
    private String readName(final CPReader reader) throws SAXException, IOException {
        final int start = len;
        if (isNameStartChar(c)) {
            append(c);
            c = reader.read();
            while (c != ' ' && c != '>' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            String s = newString(start);
            len = start;
            return s;
        } else {
            return error(reader, "Invalid Name " + hex(c)+" in " + reader);
        }
    }

    private String readNmtoken(final CPReader reader) throws SAXException, IOException {
        final int start = len;
        if (isNameChar(c)) {
            append(c);
            c = reader.read();
            while (c != ' ' && c != '>' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            String s = newString(start);
            len = start;
            return s;
        } else {
            return error(reader, "Invalid Nmtoken " + hex(c));
        }
    }

    private String readAttType(final CPReader reader) throws SAXException, IOException {
        if (c == '(') {
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            sb.append(readNmtoken(reader));
            if (isS(c)) {
                readS(reader);
            }
            while (c == '|') {
                c = reader.read();
                if (isS(c)) {
                    readS(reader);
                }
                sb.append('|');
                sb.append(readNmtoken(reader));
                if (isS(c)) {
                    readS(reader);
                }
            }
            if (c == ')') {
                sb.append(')');
                c = reader.read();
                return sb.toString();
            } else {
                return error(reader, "Invalid NotationType token " + hex(c));
            }
        } else {
            String s = readName(reader);
            if (s.equals("NOTATION")) {
                StringBuilder sb = new StringBuilder();
                sb.append(s);
                sb.append(" (");
                readS(reader);
                if (c == '(') {
                    c = reader.read();
                    if (isS(c)) {
                        readS(reader);
                    }
                    sb.append(readName(reader));
                    if (isS(c)) {
                        readS(reader);
                    }
                    while (c == '|') {
                        c = reader.read();
                        if (isS(c)) {
                            readS(reader);
                        }
                        sb.append('|');
                        sb.append(readName(reader));
                        if (isS(c)) {
                            readS(reader);
                        }
                    }
                    if (c == ')') {
                        sb.append(')');
                        c = reader.read();
                        return sb.toString();
                    } else {
                        return error(reader, "Invalid NotationType token " + hex(c));
                    }
                } else {
                    return error(reader, "Invalid NotationType token " + hex(c));
                }
            } else {
                return s;
            }
        }
    }

    /**
     * children    ::=    (choice | seq) ('?' | '*' | '+')?
     * cp          ::=    (Name | choice | seq) ('?' | '*' | '+')?
     * choice      ::=    '(' S? cp ( S? '|' S? cp )+ S? ')'
     * seq         ::=    '(' S? cp ( S? ',' S? cp )* S? ')'
     */
    private void readElementContent(final CPReader reader, StringBuilder sb, boolean first) throws SAXException, IOException {
        if (c == '(' || first) {
            sb.append('(');
            if (!first) {
                c = reader.read();
            }
            if (isS(c)) {
                readS(reader);
            }
            readElementContent(reader, sb, false);
            if (isS(c)) {
                readS(reader);
            }
            int bar = 0;
            while ((c == '|' || c == ',') && (bar == 0 || bar == c)) {
                bar = c;
                c = reader.read();
                if (isS(c)) {
                    readS(reader);
                }
                sb.append((char)bar);
                readElementContent(reader, sb, false);
                if (isS(c)) {
                    readS(reader);
                }
            }
            if (c != ')') {
                error(reader, "Invalid ContentSpec " + hex(c));
            }
            sb.append(')');
            c = reader.read();
        } else if (!first) {
            String s = readName(reader);
            sb.append(s);
        }
        if (c == '?' || c == '+' || c == '*') {
            sb.append((char)c);
            c = reader.read();
        }
    }

    /**
     * contentspec ::= 'EMPTY' | 'ANY' | Mixed | children
     * Mixed       ::= '(' S? '#PCDATA' (S? '|' S? Name)* S? ')*' | '(' S? '#PCDATA' S? ')'
     */
    private String readContentSpec(final CPReader reader) throws SAXException, IOException {
        if (c == '(') {
            StringBuilder sb = new StringBuilder();
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            if (c == '#') {
                // Mixed
                c = reader.read();
                String s = readName(reader);
                if (s.equals("PCDATA")) {
                    sb.append("(#PCDATA");
                    if (isS(c)) {
                        readS(reader);
                    }
                    boolean bar = false;
                    while (c == '|') {
                        bar = true;
                        c = reader.read();
                        if (isS(c)) {
                            readS(reader);
                        }
                        sb.append('|');
                        s = readName(reader);
                        sb.append(s);
                        if (isS(c)) {
                            readS(reader);
                        }
                    }
                    if (c != ')') {
                        return error(reader, "Invalid ContentSpec " + fmt(sb.toString()) +" "+hex(c));
                    }
                    sb.append(')');
                    if (bar) {
                        // If there is a "|", asterisk is required.
                        // If not, asterisk is optional.
                        c = reader.read();
                        if (c != '*') {
                            return error(reader, "Invalid ContentSpec " + fmt(sb.toString()) +" "+hex(c));
                        }
                        sb.append('*');
                        c = reader.read();
                    } else {
                        c = reader.read();
                        if (c == '*') {
                            sb.append('*');
                            c = reader.read();
                        }
                    }
                }
            } else {
                readElementContent(reader, sb, true);
            }
            return sb.toString();
        } else {
            String s = readName(reader);
            if (s.equals("EMPTY") || s.equals("ANY")) {
                return s;
            } else {
                return error(reader, "Invalid ContentSpec " + hex(c));
            }
        }
    }

    /**
     * Call when quote has been read. Leaves cursor after final quote
     */
    private String readSystemLiteral(final CPReader reader, int quote) throws SAXException, IOException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            if (quote == '\'' || quote == '"') {
                final int start = len;
                while ((c = reader.read()) != quote) {
                    if (c >= 0) {
                        append(c);
                    } else {
                        return error(reader, "EOF reading SystemLiteral");
                    }
                }
                c = reader.read();
                String s = newString(start);
                len = start;
                return s;
            } else {
                return error(reader, "Invalid SystemLiteral " + hex(c));
            }
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    /**
     * Call when quote has been read. Leaves cursor after final quote
     */
    private String readPubidLiteral(final CPReader reader, int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"') {
            final int start = len;
            while ((c = reader.read()) != quote) {
                if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || "-'()+,./:=?;!*#@$_%".indexOf(c) >= 0) {
                    append(c);
                } else if (" \r\n".indexOf(c) >= 0) {
                    if (len == start) {
                        // trim first whitespace
                    } else if (buf[len - 1] != ' ') {
                        // collapse whitespace sequence
                        append(' ');
                    }
                } else if (c < 0) {
                    return error(reader, "EOF reading Pubiditeral");
                } else {
                    return error(reader, "Invalid PubidLiteral " + hex(c));
                }
            }
            c = reader.read();
            if (len > start && buf[len - 1] == ' ') {
                // trim last whitespace
                len--;
            }
            String s = newString(start);
            len = start;
            return s;
        } else {
            return error(reader, "Invalid PubidLiteral " + hex(quote));
        }
    }

    /**
     * Reference ::= EntityRef | CharRef
     * Call after read returns '&'. Leaves cursor after ';'
     * @param general if true, expand genreal references, if false just expand numeric references
     */
    private Entity readReference(final CPReader reader) throws SAXException, IOException {
        c = reader.read();
        Entity entity = null;
        if (c == '#') {
            c = reader.read();
            int v = 0;
            if (c == 'x') {
                c = reader.read();
                if (c >= '0' && c <= '9') {
                    v = c - '0';
                } else if (c >= 'A' && c <= 'F') {
                    v = c - 'A' + 10;
                } else if (c >= 'a' && c <= 'f') {
                    v = c - 'a' + 10;
                } else {
                    error(reader, "Invalid Reference " + hex(c));
                    return null;
                }
                for (int i=0;;i++) {
                    c = reader.read();
                    if (c == ';') {
                        break;
                    } else if (c >= '0' && c <= '9') {
                        v = (v<<4) + c - '0';
                    } else if (c >= 'A' && c <= 'F') {
                        v = (v<<4) + c - 'A' + 10;
                    } else if (c >= 'a' && c <= 'f') {
                        v = (v<<4) + c - 'a' + 10;
                    } else {
                        error(reader, "Invalid Reference " + hex(c));
                        return null;
                    }
                    if (v > 0x10FFFF) {
                        error(reader, "Invalid Reference " + hex(c));
                    }
                }
            } else if (c >= '0' && c <= '9') {
                v = c - '0';
                for (int i=0;;i++) {
                    c = reader.read();
                    if (c == ';') {
                        break;
                    } else if (c >= '0' && c <= '9') {
                        v = (v*10) + c - '0';
                    } else {
                        error(reader, "Invalid Reference " + hex(c));
                        return null;
                    }
                    if (v > 0x10FFFF) {
                        error(reader, "Invalid Reference " + hex(c));
                    }
                }
            } else {
                error(reader, "Invalid Reference " + hex(c));
                return null;
            }
            if (v < 20) {
                if (reader.isXML11()) {
                    if (v == 0) {
                        error(reader, "Invalid char " + hex(v));
                        return null;
                    }
                } else if (v != 9 && v != 10 && v != 13) {
                    error(reader, "Invalid char " + hex(v));
                    return null;
                }
            } else if (v >= 0xd800 && (v <= 0xdfff || v == 0xfffe || v == 0xffff || v > 0x10ffff)) { 
                error(reader, "Invalid Reference " + hex(v));
                return null;
            }
            entity = Entity.createCharacter(v);
        } else if (isNameStartChar(c)) {
            final int start = len;
            append(c);
            c = reader.read();
            while (c != ';' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            if (c != ';') {
                error(reader, "Invalid EntityReference " + hex(c));
                return null;
            }
            String name = newString(start);
            String value = null;
            len = start;
            if (name.equals("lt")) {
                entity = Entity.LT;
            } else if (name.equals("gt")) {
                entity = Entity.GT;
            } else if (name.equals("amp")) {
                entity = Entity.AMP;
            } else if (name.equals("apos")) {
                entity = Entity.APOS;
            } else if (name.equals("quot")) {
                entity = Entity.QUOT;
            } else {
                entity = dtd == null ? null : dtd.getEntity(name);
                if (entity == null) {
                    entity = Entity.createInvalid(name);
                }
            }
        } else {
            error(reader, "Invalid Reference " + hex(c));
        }
        return entity;
    }

    /**
     * PEReference ::= ...
     * Call after read returns '%' (if read=true) or after first char of name (if read=false). Leaves cursor after ';'
     * @param read whether to read the first char of the name
     */
    private Entity readPEReference(final CPReader reader, boolean read) throws SAXException, IOException {
        if (read) {
            c = reader.read();
        }
        if (isNameStartChar(c)) {
            final int start = len;
            append(c);
            c = reader.read();
            while (c != ';' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            String name = newString(start);
            if (c != ';') {
                error(reader, "SemicolonRequiredInPEReference", name);
                return null;
            }
            len = start;
            Entity entity = dtd == null ? null : dtd.getEntity("%" + name);
            if (entity == null) {
                error(reader, "Undefined parameter entity %" + name + ";");
                return null;
            }
            return entity;
        } else {
            error(reader, "Invalid PEReference " + hex(c));
            return null;
        }
    }

    /**
     * EntityValue      ::= '"' ([^%&"] | PEReference | Reference)* '"' |  "'" ([^%&'] | PEReference | Reference)* "'"
     */
    private String readEntityValue(final CPReader reader, final int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"') {
            final int start = len;
            // Expand parameters here (done in read) but not genreal entities
            // r- https://www.w3.org/TR/REC-xml/#intern-replacement
            while ((c = reader.read()) != quote) {
                if (c == '&') {
                    Entity entity = readReference(reader);
                    if (entity.isCharacter()) {
                        append(entity.getValue());
                    } else {
                        append('&');
                        append(entity.getName());
                        append(';');
                    }
                } else if (c < 0) {
                    return error(reader, "EOF reading EntityValue");
                } else {
                    append(c);
                }
            }
            String s = newString(start);
            len = start;
            return s;
        } else {
            return error(reader, "Invalid EntityValue " + hex(quote));
        }
    }

    /**
     * whitespace is collapsed to space; whitespace in entities is preserved.
     * entities must be expanded
     * Cursor left after final quote
     */
    private String readAttValue(final CPReader reader, final int quote, final String attName, final String elementName) throws SAXException, IOException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            if (quote == '\'' || quote == '"' || quote == -1) {
                final int start = len;
                while ((c = reader.read()) != quote) {
                    if (c == '&') {
                        Entity entity = readReference(reader);
                        if (entity.isInvalid()) {
                            // should warn
                            append('&');
                            append(entity.getName());
                            append(';');
                        } else if (entity.isSimple() || entity.isCharacter()) {
                            append(entity.getValue());
                        } else if (entity.isExternal()) {
                            error(reader, "ReferenceToExternalEntity", entity.getName());
                        } else {
                            if (entityStack.contains(entity)) {
                                error(reader, "RecursiveGeneralReference", entity.getName(), entityStack.get(entityStack.size() - 1).getName());
                            } else if (++generalEntityExpansionCount > maxGeneralEntityExpansionCount) {
                                error(reader, "EntityExpansionLimitExceeded", Integer.toString(maxGeneralEntityExpansionCount));
                            } else {
                                entityStack.add(entity);
                                CPReader entityReader = getEntityReader(reader, entity);
                                readAttValue(entityReader, -1, attName, elementName); // recursive!
                                entityReader.close();
                                entityStack.remove(entityStack.size() - 1);
                            }
                        }
                    } else if (c == '<') {
                        error(reader, "LessthanInAttValue", attName, elementName);
                    } else if (c < 0) {
                        error(reader, "AttributeValueUnterminated", attName);
                    } else if (isS(c)) {
                        append(' ');
                    } else {
                        append(c);
                    }
                }
                if (quote != -1) {
                    String s = newString(start);
                    len = start;
                    return s;
                } else {
                    return null;
                }
            } else {
                return error(reader, "QuoteRequiredInAttValue", attName);
            }
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    /**
     * Call after read '<' and '-'. Cursor left after final '>'
     */
    private void readComment(final CPReader reader) throws SAXException, IOException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            c = reader.read();
            if (c == '-') {
                final int start = len;
                while ((c=reader.read()) >= 0) {
                    if (c == '-') {
                        c = reader.read();
                        if (c == '-') {
                            c = reader.read();
                            if (c == '>') {
                                if (q.isLexicalHandler()) {
                                    q.comment(buf, start, len - start);
                                    len = postBuffer(start);
                                } else {
                                    len = start;
                                }
                                return;
                            } else {
                                error(reader, "DashDashInComment");
                            }
                        } else {
                            append('-');
                            append(c);
                        }
                    } else {
                        append(c);
                    }
                }
                error(reader, "CommentUnterminated");
            } else {
                error(reader, "InvalidCommentStart");
            }
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    /**
     * Call after <[CDATA. Leave cursor after final ']]>'
     */
    private void readCDATA(final CPReader reader) throws SAXException, IOException {
        if (c != '[') {
            error(reader, "InvalidCharInCDSect", Integer.toHexString(c));
        }
        if (q.isLexicalHandler()) {
            q.startCDATA();
        }
        final int start = len;
        int d = 0;
        c = reader.read();
        while (c >= 0) {
            if (c == ']' && d == 0) {
                d = 1;
            } else if (c == ']' && d == 1) {
                d = 2;
            } else if (c == ']' && d == 2) {
                append(']');
            } else if (c == '>' && d == 2) {
                flush(start, false);
                if (q.isLexicalHandler()) {
                    q.endCDATA();
                }
                return;
            } else if (d == 2) {
                append(']');
                append(']');
                append(c);
                d = 0;
            } else if (d == 1) {
                append(']');
                append(c);
                d = 0;
            } else {
                append(c);
            }
            c = reader.read();
        }
        error(reader, "CDSectUnterminated");
    }

    /**
     * Call after "<[INCLUDE". Leave cursor afte final ]]>
     * Return the CPReader to read
     */
    private CPReader readINCLUDE(final CPReader reader) throws SAXException, IOException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            if (isS(c)) {
                readS(reader);
            }
            if (c != '[') {
                error(reader, "Bad token <![INCLUDE " + hex(c));
            }
            final int start = len;
            final int line = reader.getLineNumber();
            final int col = reader.getColumnNumber();
            int depth = 1;
            while ((c=reader.read()) >= 0) {
                if (c == '<') {
                    append(c);
                    if ((c=reader.read()) == '!') {
                        append(c);
                        if ((c=reader.read()) == '[') {
                            append(c);
                            depth++;
                        } else {
                            append(c);
                        }
                    } else {
                        append(c);
                    }
                } else if (c == ']') {
                    if ((c=reader.read()) == ']') {
                        if ((c=reader.read()) == '>') {
                            depth--;
                            if (depth == 0) {
                                String input = newString(start);
                                len = start;
                                return CPReader.getReader(input, reader.getPublicId(), reader.getSystemId(), line, col, reader.isXML11());
                            } else {
                                append(']');
                                append(']');
                                append('>');
                            }
                        } else {
                            append(']');
                            append(']');
                            append(c);
                        }
                    } else {
                        append(']');
                        append(c);
                    }
                } else {
                    append(c);
                }
            }
            error(reader, "EOF in INCLUDE");
            return null;
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    private void readIGNORE(final CPReader reader) throws SAXException, IOException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            if (isS(c)) {
                readS(reader);
            }
            if (c != '[') {
                error(reader, "Bad token <![IGNORE " + hex(c));
            }
            int depth = 1;
            while ((c=reader.read()) >= 0) {
                if (c == '<') {
                    if ((c=reader.read()) == '!') {
                        if ((c=reader.read()) == '[') {
                            depth++;
                        }
                    }
                } else if (c == ']') {
                    if ((c=reader.read()) == ']') {
                        if ((c=reader.read()) == '>') {
                            depth--;
                            if (depth == 0) {
                                return;
                            }
                        }
                    }
                }
            }
            error(reader, "EOF in IGNORE " + reader);
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    /**
     * Call after "<!DOCTYPE", leave cursor after tailing ">"
     */
    private void readDOCTYPE(final CPReader reader) throws SAXException, IOException {
        if (featureDisallowDoctype) {
            error(reader, "DoctypeNotAllowed");
        }
        readS(reader);
        final String name = readName(reader);
        String pubid = null, sysid = null;
        String internalSubsetSource = null;
        CPReader internalSubset = null;
        if (isS(c)) {
            readS(reader);
        }
        // Read ExternalID
        if (c == 'S') {
            String s = readName(reader);
            if (!s.equals("SYSTEM")) {
                error(reader, "Invalid DOCTYPE " + fmt(name));
            }
            readS(reader);
            sysid = readSystemLiteral(reader, c);
            if (isS(c)) {
                readS(reader);
            }
        } else if (c == 'P') {
            String s = readName(reader);
            if (!s.equals("PUBLIC")) {
                error(reader, "Invalid DOCTYPE " + fmt(name));
            }
            readS(reader);
            pubid = readPubidLiteral(reader, c);
            readS(reader);
            sysid = readSystemLiteral(reader, c);
            if (isS(c)) {
                readS(reader);
            }
        }
        if (c == '[') {
            c = reader.read();
            final int line = reader.getLineNumber();
            final int col = reader.getColumnNumber();
            StringBuilder sb = new StringBuilder();
            int quote = 0;
            final int start = len;
            while (c >= 0 && (c != ']' || quote != 0)) {
                // We have to identity comments, because otherwise
                // quotes in comments break the "in quote" test
                if (c == '<') {
                    sb.append('<');
                    c = reader.read();
                    if (c == '!') {
                        sb.append('!');
                        c = reader.read();
                        if (c == '-') {
                            sb.append('-');
                            LexicalHandler lh = lexicalHandler;
                            Queue q = this.q;
                            this.q = new DirectQueue(null, null, null, q.entityResolver, q.errorHandler, new DefaultHandler2() {
                                public void comment(char[] buf, int off, int len) {
                                    sb.append('-');
                                    sb.append(buf, off, len);
                                    sb.append("--");
                                }
                            });
                            readComment(reader);
                            this.q = q;
                        }
                    }
                }
                if (c < 0x10000) {
                    sb.append((char)c);
                } else {
                    c = CPReader.toUTF16(c);
                    sb.append((char)(c>>16));
                    sb.append((char)c);
                }
                if (quote == 0 && (c == '"' || c == '\'')) {
                    quote = c;
                } else if (c == quote && quote > 0) {
                    quote = 0;
                }
                c = reader.read();
            }
            len = start;
            if (c != ']') {
                error(reader, "Bad token " + hex(c));
            }
            internalSubsetSource = sb.toString();
            internalSubset = CPReader.getReader(internalSubsetSource, reader.getPublicId(), reader.getSystemId(), line, col, reader.isXML11());
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
        }
        if (c != '>') {
            error(reader, "Bad token " + hex(c));
        }
        dtd = new DTD(factory, name, pubid, reader.getSystemId(), sysid);
        Entity dtdentity = Entity.createDTD(name, pubid, sysid, reader.getSystemId());
        CPReader dtdreader = null;
        // Xerces does (if appropriate)
        //   getExternalSubset
        //   startDTD
        //   parseInternalSubset
        //   resolveExternal
        //   parseExternal
        // To cache we must do
        //   getExternalSubset
        //   resolveExternal
        //   startDTD
        //   parseInternalSubset
        //   parseExternalSubset
        //
        // Xerces caching policy (in XMLDTDDescription.java) is:
        //  compare name; compare publicid if set; compare resolved systemid.
        // name isn't used anywhere though.
        // 
        if (pubid == null && sysid == null) {
            // First, to match Xerces - this calls getExternalSubset
            dtdreader = getEntityReader(reader, dtdentity);
        }
        String dtdurn = null;
        if (factory.getCache() != null && featureCache) {
            DTD cacheddtd = null;
            if (featureCachePublicId && dtd.getPublicId() != null && internalSubset == null) {
                // If the DTD has a public ID and the feature set says public Ids never change,
                // check the cache for this public ID. If there, don't revalidate.
                dtdurn = "urn:xml:" + dtd.getPublicId();
                cacheddtd = factory.getCache().getDTD(dtdurn, reader.isXML11());
                if (cachelog.isLoggable(Level.FINE)) {
                    cachelog.fine("Cache get(" + dtdurn + ") " + cacheddtd);
                }
            }
            InputSourceURN source = null;
            if (cacheddtd == null) {
                source = (InputSourceURN)getExternalEntityInputSource(reader, dtdentity);
                if (source != null) {
                    dtdurn = source.getURN();
                    if (internalSubset != null) {
                        MurmurHash3 hash = new MurmurHash3();
                        hash.update(internalSubsetSource.getBytes("UTF-8"));
                        dtdurn += ":" + hash.getValue128().toString(16);
                    }
                    cacheddtd = factory.getCache().getDTD(dtdurn, reader.isXML11());
                    if (cachelog.isLoggable(Level.FINE)) {
                        cachelog.fine("Cache get(" + dtdurn + ") " + cacheddtd);
                    }
                    // When loading an InputSource the only input parameters are
                    // [entityPublicId,parentSystemID,entitySystemId]
                    if (cacheddtd != null) {
                        // We have a cached DTD that matches the URN of the InputSource
                        // we've just loaded. Now we have to check all the dependencies
                        // referenced by that cached DTD to ensure they haven't changed.
                        // If any have, the cached DTD is invalid.
                        // We'll be resolving entities as part of this process; if the
                        // cached DTD is invalid, those entities shouldn't be reloaded
                        int ecount = 0;
                        for (Map.Entry<Entity,String> e : cacheddtd.getDependencies().entrySet()) {
                            final Entity entity = e.getKey();
                            final String suburn = e.getValue();
                            if (entity.isExternal()) {
                                InputSourceURN subsource = (InputSourceURN)getExternalEntityInputSource(reader, entity);
                                if (subsource != null) {
                                    String newurn = subsource.getURN();
                                    if (newurn == null) {
                                        newurn = subsource.setURNFromContent();
                                    }
                                    if (!suburn.equals(newurn)) {
                                        if (cachelog.isLoggable(Level.FINE)) {
                                            cachelog.fine("Cache entity " + subsource + " URN mismatch: want " + suburn + " found " + newurn);
                                        }
                                        cacheddtd = null;
                                        break;
                                    } else {
                                        if (cachelog.isLoggable(Level.FINE)) {
                                            cachelog.fine("Cache entity " + subsource + " URN match: want " + suburn);
                                        }
                                    }
                                }
                            }
                            ecount++;
                        }
                    }
                }
            }
            if (cacheddtd != null) {
                if (cachelog.isLoggable(Level.FINE)) {
                    cachelog.fine("Cache match");
                }
                dtd = cacheddtd;
            } else if (source != null) {
                dtdreader = CPReader.normalize(CPReader.getReader(source), reader.isXML11());
            }
        }
        if (!dtd.isClosed()) {
            if (q.isLexicalHandler()) {
                q.startDTD(name, pubid, sysid);
            }
            if (internalSubset != null) {
                CPReader tmp = curreader;
                curreader = internalSubset;
                internalSubset = new PEReferenceExpandingCPReader(internalSubset);
                readInternalSubset(internalSubset);
                curreader = tmp;
                internalSubset.close();
            }
            if (dtdreader == null && pubid != null || sysid != null) {
                dtdreader = getEntityReader(reader, dtdentity);
            }
            if (dtdreader != null && featureLoadExternalDTD) {
                if (q.isLexicalHandler()) {
                    q.startEntity("[dtd]");
                }
                CPReader tmp = curreader;
                curreader = dtdreader;
                dtdreader = new PEReferenceExpandingCPReader(dtdreader);
                readExternalSubset(dtdreader, true);
                curreader = tmp;
                dtdreader.close();
                if (q.isLexicalHandler()) {
                    q.endEntity("[dtd]");
                }
            }
            if (q.isLexicalHandler()) {
                q.endDTD();
            }
            dtd.close();
            if (factory.getCache() != null && featureCache) {
                if (cachelog.isLoggable(Level.FINE)) {
                    cachelog.fine("Cache put(" + dtdurn + ") " + dtd);
                }
                factory.getCache().putDTD(dtdurn, reader.isXML11(), dtd);
                if (featureCachePublicId && dtd.getPublicId() != null && internalSubset == null) {
                    dtdurn = "urn:xml:" + dtd.getPublicId();
                    factory.getCache().putDTD(dtdurn, reader.isXML11(), dtd);
                    if (cachelog.isLoggable(Level.FINE)) {
                        cachelog.fine("Cache put(" + dtdurn + ") " + dtd);
                    }
                }
            }
        }
    }

    /**
     * intSubset   ::= (markupdecl | PEReference | S)*
     * markupdecl  ::= elementdecl | AttlistDecl | EntityDecl | NotationDecl | PI | Comment
     */
    private void readInternalSubset(final CPReader reader) throws SAXException, IOException {
        c = reader.read();
        while (c > 0) {
            if (c == '<') {
                c = reader.read();
                if (c == '!') {
                    c = reader.read();
                    if (c == '-') {
                        readComment(reader);
                    } else {
                        String s = readName(reader);
                        if (s.equals("ELEMENT")) {
                            readELEMENT(reader);
                        } else if (s.equals("ATTLIST")) {
                            readATTLIST(reader);
                        } else if (s.equals("ENTITY")) {
                            readENTITY(reader);
                        } else if (s.equals("NOTATION")) {
                            readNOTATION(reader);
                        } else {
                            error(reader, "Bad token \"" + s + "\"");
                        }
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        error(reader, "ReservedPITarget");
                    } else {
                        readPI(reader, target, true);
                    }
                } else {
                    error(reader, "Bad token < " + hex(c));
                }
            } else if (!isS(c)) {
                error(reader, "InvalidCharInInternalSubset", Integer.toHexString(c));
            }
            c = reader.read();
        }
    }

    private void readExternalSubset(final CPReader reader, boolean allowProlog) throws SAXException, IOException {
        c = reader.read();
        if (c != '<') {
            allowProlog = false;
        }
        while (c > 0) {
            if (c == '<') {
                c = reader.read();
                if (c == '!') {
                    c = reader.read();
                    if (c == '-') {
                        readComment(reader);
                    } else if (c == '[') {
                        c = reader.read();
                        if (isS(c)) {
                            readS(reader);
                        }
                        String s = readName(reader);
                        if ("INCLUDE".equals(s)) {
                            CPReader includeReader = readINCLUDE(reader);
                            includeReader = new PEReferenceExpandingCPReader(includeReader);
                            readExternalSubset(includeReader, false);
                            includeReader.close();
                        } else if ("IGNORE".equals(s)) {
                            readIGNORE(reader);
                        } else {
                            error(reader, "Bad token \"<![" + s + "\"");
                        }
                    } else {
                        String s = readName(reader);
                        if (s.equals("ELEMENT")) {
                            readELEMENT(reader);
                        } else if (s.equals("ATTLIST")) {
                            readATTLIST(reader);
                        } else if (s.equals("ENTITY")) {
                            readENTITY(reader);
                        } else if (s.equals("NOTATION")) {
                            readNOTATION(reader);
                        } else {
                            error(reader, "Bad token \"<!" + s + "\"");
                        }
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        if (allowProlog) {
                            readXMLPI(reader, false);
                            allowProlog = false;
                        } else {
                            error(reader, "ReservedPITarget");
                        }
                    } else {
                        readPI(reader, target, true);
                    }
                } else {
                    error(reader, "Bad token < " + hex(c));
                }
            } else if (!isS(c)) {
                error(reader, "InvalidCharInInternalSubset", Integer.toHexString(c));
            }
            c = reader.read();
        }
    }

    /**
     * Call after "<!ENTITY", leave cursor after tailing ">"
     */
    private void readENTITY(final CPReader reader) throws SAXException, IOException {
        readS(reader);
        boolean ped = c == '%';
        String name;
        if (ped) {
            c = reader.read();
            readS(reader);
            name = "%" + readName(reader);
            readS(reader);
        } else {
            name = readName(reader);
            if (name.indexOf(":") >= 0) {
//                error(reader, "Entity name " + fmt(name) + " cannot contain colon when parsing with namespaces");
            }
            readS(reader);
        }
        if (c == '"' || c == '\'') {
            int line = reader.getLineNumber();
            int col = reader.getColumnNumber();
            String value = readEntityValue(reader, c);
            if (q.isDeclHandler()) {
                q.internalEntityDecl(name, value);
            }
            Entity entity;
            if (value.length() == 1 || (value.length() == 2 && value.codePointAt(0) > 0xffff)) {
                // for eg nbsp, this will be quicker, and won't risk hitting limits
                entity = Entity.createSimple(name, value);
            } else {
                entity = Entity.createInternal(name, value, reader.getPublicId(), reader.getSystemId(), line, col);
            }
            dtd.addEntity(entity);
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            if (c != '>') {
                error(reader, "Bad token " + hex(c));
            }
        } else {
            // Read ExternalID
            String pubid = null, sysid = null, ndata = null;
            String s = readName(reader);
            if (s.equals("SYSTEM")) {
                readS(reader);
                sysid = readSystemLiteral(reader, c);
            } else if (s.equals("PUBLIC")) {
                readS(reader);
                pubid = readPubidLiteral(reader, c);
                readS(reader);
                sysid = readSystemLiteral(reader, c);
            } else {
                error(reader, "Bad token \"" + name + "\"");
            }
            if (isS(c)) {
                readS(reader);
            }
            if (!ped && c == 'N') {
                s = readName(reader);
                if (s.equals("NDATA")) {
                    readS(reader);
                    ndata = readName(reader);
                    if (isS(c)) {
                        readS(reader);
                    }
                } else {
                    error(reader, "Bad token \"" + s + "\"");
                }
            }
            if (c != '>') {
                error(reader, "Bad token " + hex(c));
            }
            if (ndata != null) {
                if (q.isDTDHandler()) {
                    q.unparsedEntityDecl(name, pubid, factory.resolve(reader.getSystemId(), sysid), ndata);
                }
            } else {
                String r = factory.resolve(reader.getSystemId(), sysid);
                if (q.isDeclHandler()) {
                    q.externalEntityDecl(name, pubid, r);
                }
                Entity entity = Entity.createExternal(name, pubid, sysid, reader.getSystemId());
                dtd.addEntity(entity);
            }
        }
    }

    /**
     *  AttlistDecl ::= '<!ATTLIST' S Name AttDef* S? '>'
     *  AttDef      ::= S Name S AttType S DefaultDecl
     *  DefaultDecl ::= '#REQUIRED' | '#IMPLIED' | (('#FIXED' S)? AttValue)
     *
     * We've just read "ATTLIST"
     */
    private void readATTLIST(CPReader reader) throws SAXException, IOException {
        readS(reader);
        String name = readName(reader);
        if (isS(c)) {
            readS(reader);
        }
        while (c != '>') {
            String attName = readName(reader);
            readS(reader);
            String attType = readAttType(reader);
            readS(reader);
            String attMode, defaultValue = null;
            if (c == '#') {
                c = reader.read();
                attMode = "#" + readName(reader);
                if (attMode.equals("#FIXED")) {
                    readS(reader);
                    defaultValue = readAttValue(reader, c, attName, name);
                    c = reader.read();
                } else if (attMode.equals("#IMPLIED") || attMode.equals("#REQUIRED")) {
                    defaultValue = null;
                } else {
                    error(reader, "Bad token " + attMode);
                }
            } else {
                attMode = null; // "#FIXED";
                defaultValue = readAttValue(reader, c, attName, name);
                c = reader.read();
            }
            if (isS(c)) {
                readS(reader);
            }
            try {
                if (defaultValue != null) {
                    if ("ID".equals(attType) || "IDREF".equals(attType) || "ENTITY".equals(attType)) {
                        for (int i=0;i<defaultValue.length();) {
                            int c = defaultValue.codePointAt(i);
                            if (i == 0 ? !isNameStartChar(c) : !isNameChar(c)) {
                                throw new IllegalStateException(attType + " attr \"" + attName + "\" default " + fmt(defaultValue) + " not a Name");
                            }
                            i += c < 0x10000 ? 1 : 2;
                        }
                    } else if ("NMTOKEN".equals(attType)) {
                        for (int i=0;i<defaultValue.length();) {
                            int c = defaultValue.codePointAt(i);
                            if (!isNameChar(c)) {
                                throw new IllegalStateException(attType + " attr \"" + attName + "\" default " + fmt(defaultValue) + " not an Nmtoken");
                            }
                            i += c < 0x10000 ? 1 : 2;
                        }
                    }
                }
                Element elt = dtd.getElement(name);
                if (elt == null) {
                    elt = new Element(dtd, name, null); // placeholder elt
                    dtd.addElement(elt);
                }
                elt.attributeDecl(attName, attType, attMode, defaultValue);
            } catch (IllegalStateException e) {
                error(reader, e.getMessage());
            }
            if (q.isDeclHandler()) {
                q.attributeDecl(name, attName, attType, attMode, defaultValue);
            }
        }
    }

    private void readELEMENT(CPReader reader) throws SAXException, IOException {
        readS(reader);
        String name = readName(reader);
        c = reader.read();
        if (isS(c)) {
            readS(reader);
        }
        String contentSpec = readContentSpec(reader);
        if (isS(c)) {
            readS(reader);
        }
        if (c == '>') {
            try {
                Element elt = dtd.getElement(name);
                if (elt == null) {
                    dtd.addElement(elt = new Element(dtd, name, contentSpec));
                } else {
                    elt.setModel(contentSpec);
                }
            } catch (RuntimeException e) {
                error(reader, e.getMessage());
            }
            if (q.isDeclHandler()) {
                q.elementDecl(name, contentSpec);
            }
        } else {
            error(reader, "EOF in ElementDecl: " + hex(c));
        }
    }

    private void readNOTATION(CPReader reader) throws SAXException, IOException {
        readS(reader);
        String name = readName(reader);
        readS(reader);
        String pubid = null, sysid = null;
        String s = readName(reader);
        if (s.equals("SYSTEM")) {
            readS(reader);
            sysid = readSystemLiteral(reader, c);
        } else if (s.equals("PUBLIC")) {
            readS(reader);
            pubid = readPubidLiteral(reader, c);
            if (isS(c)) {
                readS(reader);
                if (c != '>') {
                    sysid = readSystemLiteral(reader, c);
                }
            }
            if (isS(c)) {
                readS(reader);
            }
        } else {
            error(reader, "Bad NotationDecl \"" + s + "\"");
        }
        if (c != '>') {
            error(reader, "Bad NotationDecl " + hex(c));
        }
        if (q.isDTDHandler()) {
            q.notationDecl(name, pubid, factory.resolve(reader.getSystemId(), sysid));
        }
    }

    /**
     * Call after "<?xml". Leave cursor after final '>'
     * @param document if true we are reading a document, false if we're reading an entity
     */
    private void readXMLPI(final CPReader reader, boolean document) throws IOException, SAXException {
        if (isS(c)) {
            readS(reader);
        }
        String s = null, version = null, encoding = null, standalone = null;
        if (c != '?') {
            s = readName(reader);
        }
        // In doc, it is "version encoding? standalone?"
        // In entity, it is "version? encoding?"
        if ("version".equals(s)) {
            if (isS(c)) {
                readS(reader);
            }
            if (c != '=') {
                error(reader, "EqRequiredInXMLDecl", "version");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            version = readAttValue(reader, c, "version", "xml");
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            if (c != '?') {
                s = readName(reader);
            }
        }
        if ("encoding".equals(s)) {
            if (isS(c)) {
                readS(reader);
            }
            if (c != '=') {
                error(reader, "EqRequiredInXMLDecl", "encoding");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            encoding = readAttValue(reader, c, "encoding", "xml");
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            if (c != '?') {
                s = readName(reader);
            }
        }
        if ("standalone".equals(s)) {
            if (isS(c)) {
                readS(reader);
            }
            if (c != '=') {
                error(reader, "EqRequiredInXMLDecl", "standalone");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            standalone = readAttValue(reader, c, "standalone", "xml");
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
        }
        if (document) {
            if (version == null) {
                error(reader, "VersionInfoRequired");
            } else if ("1.0".equals(version)) {
                reader.setXML11(false);
            } else if ("1.1".equals(version)) {
                reader.setXML11(true);
            } else {
                error(reader, "VersionInfoInvalid", version);
            }
            if (standalone == null) {
                this.standalone = false;
            } else if (standalone.equals("yes")) {
                this.standalone = true;
            } else if (standalone.equals("no")) {
                this.standalone = false;
            } else {
                error(reader, "SDDeclInvalid", standalone);
            }
            if (c != '?') {
                error(reader, "XMLDeclUnterminated");
            }
            c = reader.read();
            if (c != '>') {
                error(reader, "XMLDeclUnterminated");
            }
            q.xmlpi(reader.getEncoding(), encoding, version, standalone);
        } else {
            if (encoding == null) {
                error(reader, "EncodingDeclRequired");
            }
            if (standalone != null) {
                error(reader, "SDDeclInvalid", standalone);
            }
            if (c != '?') {
                error(reader, "TextDeclUnterminated");
            }
            c = reader.read();
            if (c != '>') {
                error(reader, "TextDeclUnterminated");
            }
        }
    }

    private void readPI(final CPReader reader, final String target, final boolean indoctype) throws IOException, SAXException {
        boolean expanding = toggleParameterEntityExpansion(reader, false);
        try {
            if (target.indexOf(":") >= 0) {
    //            error(reader, "PI target " + fmt(target) + " cannot contain colon when parsing with namespaces");
            }
            if (isS(c)) {
                readS(reader);
                final int start = len;
                boolean done = false;
                while (!done && c >= 0) {
                    if (c == '?') {
                        if ((c=reader.read()) == '>') {
                            // PI in DTD seems ignored? eg xmlconf/ibm/xml-1.1/valid/P02/ibm02v01.xml
                            if (q.isContentHandler() && !indoctype) {
                                q.processingInstruction(target, newString(start));
                            }
                            done = true;
                        } else {
                            append('?');
                            continue;
                        }
                    } else {
                        append(c);
                    }
                    if (!done) {
                        c = reader.read();
                    }
                }
                if (!done) {
                    error(reader, "EOF in ProcessingInstruction");
                }
                len = start;
            } else if (c == '?' && reader.read() == '>') {
                if (q.isContentHandler() && !indoctype) {
                    q.processingInstruction(target, "");
                }
            } else {
                error(reader, "Bad token " + hex(c));
            }
        } finally {
            toggleParameterEntityExpansion(reader, expanding);
        }
    }

    private void readETag(final CPReader reader) throws IOException, SAXException {
        c = reader.read();
        // endElement
        String qName = readName(reader);
        if (featureInternStrings) {
            qName = intern(qName);
        }
        if (isS(c)) {
            readS(reader);
        }
        if (stack.isEmpty()) {
            error(reader, "Unexpected end element tag \"</" + qName + ">\"");
        }
        Context ctx = stack.remove(stack.size() - 1);
        if (!qName.equals(ctx.name)) {
            error(reader, "ETagRequired", ctx.name, qName);
        }
        if (c == '>') {
            if (featureNamespaces) {
                int ix = qName.indexOf(':');
                String uri;
                if (ix > 0) {
                    String prefix = qName.substring(0, ix);
                    String localName = qName.substring(ix + 1);
                    if (featureInternStrings) {
                        localName = intern(localName);
                    }
                    uri = ctx.namespace(prefix);
                    if (q.isContentHandler()) {
                        q.endElement(uri, localName, qName);
                    }
                } else {
                    uri = ctx.namespace("");
                    if (q.isContentHandler()) {
                        q.endElement(uri, qName, qName);
                    }
                }
                if (q.isContentHandler() && ctx.prefixes != null) {
                    for (String s : ctx.prefixes) {
                        q.endPrefixMapping(s);
                    }
                }
            } else {
                if (q.isContentHandler()) {
                    q.endElement("", "", qName);
                }
            }
        } else {
            error(reader, "ETagUnterminated", qName, Integer.toHexString(c));
        }
    }

    private void readSTag(final CPReader reader) throws IOException, SAXException {
        // startElement
        final String qName = featureInternStrings ? intern(readName(reader)) : readName(reader);
        List<String> tmpatts = null;
        boolean selfClosing = false;
        if (isS(c)) {
            readS(reader);
            while (c != '>' && c != '/') {
                if (!isNameChar(c)) {
                    error(reader, "ElementUnterminated", qName);
                }
                String attName = readName(reader);
                if (featureInternStrings) {
                    attName = intern(attName);
                }
                if (isS(c)) {
                    readS(reader);
                }
                if (c == '=') {
                    if (tmpatts == null) {
                        tmpatts = new ArrayList<String>();
                    }
                    for (int i=0;i<tmpatts.size();i+=2) {
                        if (tmpatts.get(i).equals(attName)) {
                            error(reader, "AttributeNotUnique", qName, attName);
                        }
                    }
                    c = reader.read();
                    if (isS(c)) {
                        readS(reader);
                    }
                    String attValue = readAttValue(reader, c, attName, qName);
                    tmpatts.add(attName);
                    tmpatts.add(attValue);
                } else {
                    error(reader, "EqRequiredInAttribute", qName, attName);
                }
                c = reader.read();
                if (isS(c)) {
                    readS(reader);
                }
            }
        }
        if (c == '/') {
            selfClosing = true;
            c = reader.read();
        }
        if (c == '>') {
            final Element element = dtd == null ? null : dtd.getElement(qName);
            Context parent;
            if (stack.isEmpty()) {
                // This next requirement is only for validating!
                //if (dtd != null && !qName.equals(dtd.getName())) {
                //    error(reader, "RootElementTypeMustMatchDoctypedecl", dtd.getName(), qName);
                //}
                parent = null;
            } else {
                parent = stack.get(stack.size() - 1);
            }
            Context ctx = new Context(qName, element, parent);
            BFOAttributes atts = null;
            Map<String,Attribute> defaultAtts = element == null ? null : element.getAttributesWithDefaults();
            if (defaultAtts != null) {
                defaultAtts = new HashMap<String,Attribute>(defaultAtts);
                if (tmpatts == null) {
                    tmpatts = new ArrayList<String>();
                }
                for (int i=0;i<tmpatts.size();i+=2) {
                    String attQName = tmpatts.get(i);
                    if (defaultAtts != null) {
                        defaultAtts.remove(attQName);
                    }
                }
                for (Attribute a : defaultAtts.values()) {
                    tmpatts.add(a.getQName());
                    tmpatts.add(a.getDefaultValue());
                }
                if (defaultAtts.isEmpty()) {
                    defaultAtts = null;
                }
            }
            if (tmpatts != null) {
                if (featureNamespaces) {
                    for (int i=0;i<tmpatts.size();i+=2) {
                        String attQName = tmpatts.get(i);
                        if (attQName.equals("xmlns")) {
                            String attValue = tmpatts.get(i + 1);
                            if (featureInternStrings) {
                                attValue = intern(attValue);
                            }
                            if (q.isContentHandler()) {
                                q.startPrefixMapping("", attValue);
                                if (ctx.prefixes == null) {
                                    ctx.prefixes = new ArrayList<String>();
                                }
                                ctx.prefixes.add("");
                            }
                            ctx.register("", attValue);
                            if (!featureNamespacePrefixes) {
                                tmpatts.set(i, null);
                            }
                        } else if (attQName.startsWith("xmlns:")) {
                            String prefix = attQName.substring(6);
                            if (prefix.equals("xmlns") || prefix.equals("xml")) {
                                error(reader, "CantBindXMLNS");
                            } else if (prefix.equals("xml")) {
                                error(reader, "CantBindXML");
                            }
                            String attValue = tmpatts.get(i + 1);
                            if (featureInternStrings) {
                                attValue = intern(attValue);
                            }
                            if (q.isContentHandler()) {
                                q.startPrefixMapping(prefix, attValue);
                                if (ctx.prefixes == null) {
                                    ctx.prefixes = new ArrayList<String>();
                                }
                                ctx.prefixes.add(prefix);
                            }
                            ctx.register(prefix, attValue);
                            if (!featureNamespacePrefixes) {
                                tmpatts.set(i, null);
                            }
                        }
                    }
                }
                for (int i=0;i<tmpatts.size();) {
                    String attQName = tmpatts.get(i++);
                    if (attQName != null) {
                        if (atts == null) {
                            atts = new BFOAttributes();
                        }
                        final boolean specified = defaultAtts == null || !defaultAtts.containsKey(attQName);
                        String attValue = tmpatts.get(i++);
                        // "If the attribute type is not CDATA, then the XML processor must further process
                        //  the normalized attribute value by discarding any leading and trailing space
                        //  characters, and by replacing sequences of space characters by a
                        //  single  character."
                        //
                        // on balance, most attributes will be CDATA, so check that first.

                        Attribute a = element != null ? element.getAttributes().get(attQName) : null;
                        if (a != null && !"CDATA".equals(a.getType())) {
                            StringBuilder sb = new StringBuilder();
                            boolean ws = false;
                            for (int j=0;j<attValue.length();j++) {
                                char c = attValue.charAt(j);
                                if (c == ' ') {
                                    if (sb.length() > 0) {
                                        ws = true;
                                    }
                                } else {
                                    if (ws) {
                                        sb.append(' ');
                                        ws = false;
                                    }
                                    sb.append(c);
                                }
                            }
                            attValue = sb.toString();
                        }

                        if (featureNamespaces) {
                            int ix = attQName.indexOf(":");
                            if (ix == 0 && factory.xercescompat && reader.isXML11()) {
                                error(reader, "Attribute " + fmt(attQName) + " has zero-length prefix");
                            }
                            if (ix > 0) {
                                String prefix = attQName.substring(0, ix);
                                String localName = attQName.substring(ix + 1);
                                if (featureInternStrings) {
                                    localName = intern(localName);
                                }
                                if (localName.indexOf(':') >= 0) {
                                    error(reader, "Attribute " + fmt(attQName) + " not a valid QName");
                                }
                                if (factory.xercescompat && localName.length() == 0 && reader.isXML11()) {
                                    error(reader, "Attribute " + fmt(attQName) + " has zero-length localName");
                                }
                                String uri = ctx.namespace(prefix);
                                if (uri == null) {
                                    error(reader, "AttributePrefixUnbound", qName, attQName, prefix);
                                }
                                for (int j=0;j<atts.getLength();j++) {
                                    if (atts.getLocalName(j).equals(localName) && atts.getURI(j).equals(uri)) {
                                        error(reader, "AttributeNSNotUnique", qName, localName, uri);
                                    }
                                }
                                atts.add(uri, localName, attQName, "CDATA", attValue, specified);
                            } else {
                                atts.add("", attQName, attQName, "CDATA", attValue, specified);
                            }
                        } else {
                            atts.add("", "", attQName, "CDATA", attValue, specified);
                        }
                    } else {
                        i++;
                    }
                }
            }
            if (featureNamespaces) {
                int ix = qName.indexOf(':');
                if (ix == 0 && factory.xercescompat && reader.isXML11()) {
                    error(reader, "Element " + fmt(qName) + " has zero-length prefix");
                }
                String uri;
                if (ix > 0) {
                    String prefix = qName.substring(0, ix);
                    if (prefix.equals("xmlns")) {
                        error(reader, "ElementXMLNSPrefix");
                    }
                    String localName = qName.substring(ix + 1);
                    if (featureInternStrings) {
                        localName = intern(localName);
                    }
                    if (localName.indexOf(':') >= 0) {
                        error(reader, "Element " + fmt(qName) + " not a valid QName");
                    }
                    if (factory.xercescompat && localName.length() == 0 && reader.isXML11()) {
                        error(reader, "Element " + fmt(qName) + " has zero-length localName");
                    }
                    uri = ctx.namespace(prefix);
                    if (uri == null) {
                        error(reader, "ElementPrefixUnbound", qName, prefix);
                    }
                    if (q.isContentHandler()) {
                        q.startElement(uri, localName, qName, atts != null ? atts : BFOAttributes.EMPTYATTS);
                        if (selfClosing) {
                            q.endElement(uri, localName, qName);
                        } else {
                            stack.add(ctx);
                        }
                    }
                } else {
                    uri = ctx.namespace("");
                    if (q.isContentHandler()) {
                        q.startElement(uri, qName, qName, atts != null ? atts : BFOAttributes.EMPTYATTS);
                        if (selfClosing) {
                            q.endElement(uri, qName, qName);
                        } else {
                            stack.add(ctx);
                        }
                    }
                }
                if (selfClosing && q.isContentHandler() && ctx.prefixes != null) {
                    for (String s : ctx.prefixes) {
                        q.endPrefixMapping(s);
                    }
                }
            } else {
                if (q.isContentHandler()) {
                    q.startElement("", "", qName, atts != null ? atts : BFOAttributes.EMPTYATTS);
                    if (selfClosing) {
                        q.endElement("", "", qName);
                    } else {
                        stack.add(ctx);
                    }
                }
            }
        }
    }

    private void readDocument(final CPReader reader) throws IOException, SAXException {
        // Start by reading the prolog
        //
        // prolog  ::= XMLDecl? Misc* (doctypedecl Misc*)?
        // XMLDecl ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
        // Misc	   ::= Comment | PI | S
        c = reader.read();
        boolean skip = false, xmlpi = false;
        if (c == '<') {
            c = reader.read();
            if (c == '?') {
                c = reader.read();
                String target = readName(reader);
                if (target.equalsIgnoreCase("xml")) {
                    readXMLPI(reader, true);
                    xmlpi = true;
                } else {
                    readPI(reader, target, false);
                }
                c = reader.read();
            } else {
                q.xmlpi(null, null, null, null);
                skip = true;
            }
        }
        if (!xmlpi) {
            q.xmlpi(null, null, null, null);
        }
        while (c > 0) {
            if (c == '<' || skip) {
                if (!skip) {
                    c = reader.read();
                }
                if (c == '!') {
                    c = reader.read();
                    if (c == '-') {
                        readComment(reader);
                        // Comments must come after <?xml?> but can be before DOCTYPE
                    } else {
                        String s = readName(reader);
                        if (s.equals("DOCTYPE")) {
                            readDOCTYPE(reader);
                        } else {
                            error(reader, "Bad token \"" + s + "\"");
                        }
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        error(reader, "ReservedPITarget");
                    } else {
                        readPI(reader, target, false);
                    }
                } else {
                    // We are done with the prolog
                    break;
                }
            } else if (c < 0) {
                error(reader, "EOF after prolog");
            } else if (!isS(c)) {
                error(reader, "Invalid prolog " + hex(c));
            }
            c = reader.read();
            skip = false;
        }
        // prolog finished, cursor is start start of name after '<'

        // element ::= EmptyElemTag | STag content ETag
        // content ::= CharData? ((element | Reference | CDSect | PI | Comment) CharData?)*
        // 
        readSTag(reader);
        if (!stack.isEmpty()) {
            readContent(reader, false);
            if (!stack.isEmpty()) {
                String name = stack.get(stack.size() - 1).name;
                error(reader, "ETagRequired", name);
            }
        }

        // Trailer is comments, whitespace and PIs
        c = reader.read();
        while (c > 0) {
            if (c == '<') {
                c = reader.read();
                if (c == '!') {
                    readComment(reader);
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        error(reader, "ReservedPITarget");
                    } else {
                        readPI(reader, target, false);
                    }
                } else {
                    error(reader, "ContentIllegalInTrailingMisc");
                }
            } else if (!isS(c)) {
                error(reader, "ContentIllegalInTrailingMisc");
            }
            c = reader.read();
        }
    }

    private int flush(int start, boolean ignore) throws IOException, SAXException {
        if (q.isContentHandler()) {
            boolean ignoreWhitespace = false;
            if (ignore) {
                if (dtd != null) {
                    Context ctx = stack.get(stack.size() - 1);
                    String name = ctx.name;
                    Element elt = ctx.element;
                    if (elt != null) {
                        ignoreWhitespace = !elt.hasText();
                    }
                }
                if (ignoreWhitespace) {
                    for (int i=start;i<len;i++) {
                        if (!isS(buf[i])) {
                            ignoreWhitespace = false;
                            break;
                        }
                    }
                }
            }
            if (ignoreWhitespace) {
                q.ignorableWhitespace(buf, start, len - start);
                len = postBuffer(start);
            } else {
                q.characters(buf, start, len - start);
                len = postBuffer(start);
            }
        } else {
            len = start;
        }
        return len;
    }

    private int postBuffer(int start) {
        if (!q.isBufSafe()) {
            int max = inputBufferSize == 0 ? 65536 : inputBufferSize * 4;
            if (len > max) {
                buf = new char[buf.length];
                return 0;
            } else {
                return len;
            }
        } else {
            return start;
        }
    }

    /**
     * content ::= CharData? ((element | Reference | CDSect | PI | Comment) CharData?)*
     */
    void readContent(final CPReader reader, boolean allowProlog) throws IOException, SAXException {
        c = reader.read();
        if (c != '<') {
            allowProlog = false;
        }
        int bba = 0;
        int start = len;
        while (c > 0) {
            if (c == '<') {
                bba = 0;
                if (len > 0) {
                    start = flush(start, true);
                }
                c = reader.read();
                if (c == '!') {
                    c = reader.read();
                    if (c == '-') {
                        readComment(reader);
                    } else if (c == '[') {
                        c = reader.read();
                        final String name = readName(reader);
                        if ("CDATA".equals(name)) {
                            readCDATA(reader);
                        } else {
                            error(reader, "Bad token \"<![" + name + "\"");
                        }
                    } else {
                        error(reader, "Bad token \"<! " + hex(c));
                    }
                    start = len;
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        if (allowProlog) {
                            readXMLPI(reader, false);
                            allowProlog = false;
                        } else {
                            error(reader, "ReservedPITarget");
                        }
                    } else {
                        readPI(reader, target, false);
                    }
                    start = len;
                } else if (c == '/') {
                    readETag(reader);
                    if (stack.isEmpty()) {
                        break;
                    }
                    start = len;
                } else {
                    readSTag(reader);
                    start = len;
                }
            } else if (c == '&') {
                if (len > 0) {
                    start = flush(start, true);
                }
                // API says
                // "When a SAX2 driver is providing these events, all other events
                //  must be properly nested within start/end entity events. There
                //  is no additional requirement that events from DeclHandler or
                //  DTDHandler be properly ordered."
                // But Xerces doesn't do this, see samples/xmlconf/xmltest/valid/ext-sa/014.xml
                Entity entity = readReference(reader);
                if (entity.isCharacter()) {
                    append(entity.getValue());
                } else if (entity.isInvalid()) {
                    error(reader, "EntityNotDeclared", "&" + entity.getName() + ";");
                } else {
                    if (q.isLexicalHandler()) {
                        q.startEntity(entity.getName());
                    }
                    if (entity.isSimple()) {
                        append(entity.getValue());
                    } else {
                        if (entityStack.contains(entity)) {
                            error(reader, "RecursiveGeneralReference", entity.getName(), entityStack.get(entityStack.size() - 1).getName());
                        } else if (++generalEntityExpansionCount > maxGeneralEntityExpansionCount) {
                            error(reader, "EntityExpansionLimitExceeded", Integer.toString(maxGeneralEntityExpansionCount));
                        } else {
                            entityStack.add(entity);
                            CPReader entityReader = getEntityReader(reader, entity);
                            readContent(entityReader, true);
                            entityReader.close();
                            entityStack.remove(entityStack.size() - 1);
                        }
                        start = len;
                    }
                    if (q.isLexicalHandler()) {
                        q.endEntity(entity.getName());
                    }
                }
                bba = 0;
            } else {
                append(c);
                if (c == ']') {
                    if (bba == 0) {
                        bba = 1;
                    } else if (bba == 1) {
                        bba = 2;
                    } else {
                        // keep at 2
                    }
                } else if (c == '>') {
                    if (bba == 2) {
                        error(reader, "CDEndInContent");
                        bba = 2;
                    } else {
                        bba = 0;
                    }
                } else {
                    bba = 0;
                }
                if (inputBufferSize > 0 && len > inputBufferSize) {
//                    start = flush(start, true);
                }
            }
            c = reader.read();
        }
        if (len > start) {
            start = flush(start, true);
        }
    }

    static String fmt(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i=0;i<s.length();) {
            int c = s.codePointAt(i);
            if (c >= 0x20 && c < 0x7F) {
                sb.append((char)c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == '"') {
                sb.append("\\\"");
            } else {
                sb.append("\\u");
                String q = Integer.toHexString(c);
                for (int j=q.length();j<4;j++) {
                    sb.append('0');
                }
                sb.append(q);
            }
            i += c > 0xFFFF ? 2 : 1;
        }
        sb.append("\"");
        return sb.toString();
    }

    private boolean toggleParameterEntityExpansion(CPReader reader, boolean expanding) {
        if (reader instanceof PEReferenceExpandingCPReader) {
            return ((PEReferenceExpandingCPReader)reader).setExpanding(expanding);
        } else {
            return false;
        }
    }
    
    private CPReader getEntityReader(final CPReader reader, final Entity entity) throws SAXException, IOException {
        final boolean xml11 = reader.isXML11();
        CPReader out = null;
        if (entity.getValue() != null) {
            out = CPReader.getReader(entity.getValue(), entity.getPublicId(), entity.getSystemId(), entity.getLineNumber(), entity.getColumnNumber(), xml11);
        } else {
            InputSource source = getExternalEntityInputSource(reader, entity);
            if (source != null) {
                String urn = null;
                if (factory.getCache() != null && featureCachePublicId && source.getPublicId() != null) {
                    urn = "urn:xml:" + source.getPublicId();
                    String value = factory.getCache().getEntity(urn, xml11);
                    if (cachelog.isLoggable(Level.FINE)) {
                        cachelog.fine("Cache entityget(" + urn + ") " + (value == null ? "miss":"hit"));
                    }
                    if (value != null) {
                        out = CPReader.getReader(value, entity.getPublicId(), entity.getSystemId(), -1, -1, xml11);
                    }
                }
                if (out == null) {
                    out = CPReader.normalize(CPReader.getReader(source), xml11);
                    if (urn != null) {
                        String value = out.asString();
                        if (cachelog.isLoggable(Level.FINE)) {
                            cachelog.fine("Cache entityput(" + urn + ")");
                        }
                        factory.getCache().putEntity(urn, xml11, value);
                        out = CPReader.getReader(value, entity.getPublicId(), entity.getSystemId(), -1, -1, xml11);
                    }
                }
            }
        }
        return out;
    }

    private InputSource getExternalEntityInputSource(final CPReader reader, final Entity entity) throws SAXException, IOException {
        final String publicId = entity.getPublicId();
        final String systemId = entity.getSystemId();
        final String parentSystemId = entity.getParentSystemId();

        InputSource source = null;
        final String resolvedSystemId = factory.resolve(parentSystemId, systemId);

        // Stage 1: ask EntityResolver
        if (publicId == null && systemId == null) {
            if (entity.isDTD() && q.isEntityResolver2()) {
                source = q.getExternalSubset(entity.getName(), parentSystemId);
            }
        } else {
            if (q.isEntityResolver2()) {
                // Xerces/JVM does not pass the name through here
                // Xerces/Latest does
                source = q.resolveEntity(factory.xercescompat ? null : entity.getName(), publicId, parentSystemId, systemId != null ? systemId : "");
            } else if (q.isEntityResolver()) {
                source = q.resolveEntity(publicId, resolvedSystemId != null ? resolvedSystemId : "");
            }
        }

        // Stage 2: ask Factory
        if (source == null) {
            // We have to ask the factory, so do security checks here
            if (entity.isParameter() && !featureExternalParameterEntities) {
                error(reader, "External parameter entity " + entity.getName() + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            } else if (entity.isGeneral() && !featureExternalGeneralEntities) {
                error(reader, "External general entity " + entity.getName() + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            }
            source = factory.resolveEntity(publicId, resolvedSystemId, this, true);
        }

        if (dtd != null && !dtd.isClosed() && factory.getCache() != null) {
            // If we are using a cache, then we have to have a URN for each InputSource.
            if (source != null) {
                if (source != null && !(source instanceof InputSourceURN)) {
                    source = new InputSourceURN(source, null);
                }
                if (((InputSourceURN)source).getURN() == null) {
                    ((InputSourceURN)source).setURNFromContent();
                }
            }
            dtd.getWorkingDependencies().put(entity, (InputSourceURN)source);
        }
        return source;
    }

    private String intern(String s) {
        String t = internMap.putIfAbsent(s, s);
        return t == null ? s : t;
    }

    /**
     * A PEReference expanding reader
     */
    private class PEReferenceExpandingCPReader extends CPReader {
        private final CPReader freader;
        private CPReader reader;
        private List<CPReader> readerStack;
        private List<Entity> entityStack;
        private boolean expanding = true;
        private int hold = -1;

        PEReferenceExpandingCPReader(CPReader r) {
            this.freader = r;
            this.reader = freader;
        }

        boolean setExpanding(boolean expanding) {
            boolean b = this.expanding;
            this.expanding = expanding;
            return b;
        }

        @Override public int read() throws SAXException, IOException {
            int c2;
            if (hold >= 0) {
                c2 = hold;
                hold = -1;
            } else {
                c2 = reader.read();
            }
            while (c2 == '%' && expanding) {
                c2 = reader.read();
                if (isNameStartChar(c2)) {
                    c = c2;
                    Entity entity = readPEReference(reader, false);
                    if (entityStack == null) {
                        entityStack = new ArrayList<Entity>();
                        readerStack = new ArrayList<CPReader>();
                    }
                    if (entityStack.contains(entity)) {
                        error(reader, "RecursivePEReference", entity.getName(), entityStack.get(entityStack.size() - 1).getName());
                    } else if (++parameterEntityExpansionCount > maxParameterEntityExpansionCount) {
                        error(reader, "EntityExpansionLimitExceeded", Integer.toString(maxParameterEntityExpansionCount));
                    } else {
                        if (q.isLexicalHandler()) {
                        //    q.startEntity("%" + entity.getName());
                        }
                        reader = getEntityReader(reader, entity);
                        entityStack.add(entity);
                        readerStack.add(reader);
                        c2 = reader.read();
                    }
                } else {
                    hold = c2;
                    c2 = '%';
                    break;
                }
            }
            if (c2 < 0 && reader != freader) {
                reader.close();
                while (c2 < 0 && !readerStack.isEmpty()) {
                    Entity entity = entityStack.remove(entityStack.size() - 1);
                    readerStack.remove(readerStack.size() - 1);
                    if (q.isLexicalHandler()) {
                    //    q.endEntity("%" + entity.getName());
                    }
                    reader = readerStack.isEmpty() ? freader : readerStack.get(readerStack.size() - 1);
                    c2 = reader.read();
                }
            }
            return c2;
        }
        @Override public boolean isXML11() {
            return reader.isXML11();
        }
        @Override public String getPublicId() {
            return reader.getPublicId();
        }
        @Override public String getSystemId() {
            return reader.getSystemId();
        }
        @Override public int getLineNumber() {
            return reader.getLineNumber();
        }
        @Override public int getColumnNumber() {
            return reader.getColumnNumber();
        }
        @Override public void close() throws IOException {
            reader.close();
        }
        @Override public String toString() {
            String entity = entityStack.isEmpty() ? null : entityStack.remove(entityStack.size() - 1).getName();
            return "{pe-expanding ex=" +expanding + " et="+entity+" r="+ reader + "}";
        }
    }

}
