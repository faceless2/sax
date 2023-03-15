package com.bfo.sax;

import java.io.*;
import java.util.*;
import java.net.URL;
import org.xml.sax.*;
import org.xml.sax.ext.*;

public class BFOXMLReader implements XMLReader, Locator {

    private int c, len;
    private char[] buf = new char[256];
    private CPReader curreader;
    private boolean standalone, entityResolver2 = true;
    private DTD dtd;
    private ContentHandler contentHandler;
    private LexicalHandler lexicalHandler;
    private EntityResolver entityResolver;
    private DeclHandler declHandler;
    private DTDHandler dtdHandler;
    private ErrorHandler errorHandler;
    private List<Context> stack = new ArrayList<Context>();
    private List<Entity> entityStack = new ArrayList<Entity>();
    private BFOSAXParserFactory factory;
    private boolean fromFatalError;

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
           return true;
       } else if ("http://xml.org/sax/features/namespace-prefixes".equals(name)) {
           return false;
       } else if ("http://xml.org/sax/features/use-entity-resolver2".equals(name)) {
           return entityResolver2;
       } else {
           return false;
       }
    }
    @Override public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
            return lexicalHandler;
        } else if ("http://xml.org/sax/properties/declaration-handler".equals(name)) {
            return declHandler;
        } else if ("http://xml.org/sax/properties/document-xml-version".equals(name)) {
            return curreader != null && curreader.isXML11();
        }
        throw new SAXNotRecognizedException(name);
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
        } else {
            throw new SAXNotRecognizedException(name);
        }
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
        return getPublicId() + ":" + getLineNumber() + "." + getColumnNumber();
    }

    @Override public void parse(InputSource in) throws IOException, SAXException {
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
        curreader = CPReader.getReader("", in.getPublicId(), in.getSystemId(), 1, 1, false);
        final EntityResolver oldres = entityResolver;
        if (!entityResolver2 && entityResolver instanceof EntityResolver2) {
            entityResolver = new EntityResolver() {
                public InputSource resolveEntity(String pubid, String sysid) throws SAXException, IOException {
                    return oldres.resolveEntity(pubid, sysid);
                }
            };
        }
        try {
            if (contentHandler != null) {
                contentHandler.setDocumentLocator(this);
                contentHandler.startDocument();
            }
            curreader = CPReader.normalize(CPReader.getReader(in), false);
            readDocument(curreader);
            if (contentHandler != null) {
                contentHandler.endDocument();
            }
        } catch (SAXException e) {
            if (!fromFatalError) {
                fatalError(curreader, e.getMessage(), e);
            } else {
                throw e;
            }
        } finally {
            entityResolver = oldres;
        }
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
                return "http://www.w3.org/XML/1998/namespace";
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

    private String fatalError(final CPReader reader, String msg) throws SAXException, IOException {
        return fatalError(reader, msg, null);
    }

    private String fatalError(final CPReader reader, String msg, Throwable t) throws SAXException, IOException {
        SAXParseException e = new SAXParseException(msg, reader.getPublicId(), reader.getSystemId(), reader.getLineNumber(), reader.getColumnNumber());
        if (t != null) {
            e.initCause(t);
        }
        fromFatalError = true;
        if (errorHandler != null) {
            errorHandler.fatalError(e);
        }
        throw e;
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
            fatalError(reader, "Expected whitespace, got " + hex(c));
        }
        do {
            c = reader.read();
        } while (c == 32 || c == 10 || c == 13 || c == 9);
    }

    /**
     * Read a name. Cursor is after first char of name. On exit cursor is after last char in name, ie on space etc
     */
    private String readName(final CPReader reader) throws SAXException, IOException {
        int start = len;
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
            return fatalError(reader, "Invalid Name " + hex(c));
        }
    }

    private String readNmtoken(final CPReader reader) throws SAXException, IOException {
        int start = len;
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
            return fatalError(reader, "Invalid Nmtoken " + hex(c));
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
                return fatalError(reader, "Invalid NotationType token " + hex(c));
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
                        return fatalError(reader, "Invalid NotationType token " + hex(c));
                    }
                } else {
                    return fatalError(reader, "Invalid NotationType token " + hex(c));
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
                fatalError(reader, "Invalid ContentSpec " + hex(c));
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
                        return fatalError(reader, "Invalid ContentSpec " + fmt(sb.toString()) +" "+hex(c));
                    }
                    sb.append(')');
                    if (bar) {
                        // If there is a "|", asterisk is required.
                        // If not, asterisk is optional.
                        c = reader.read();
                        if (c != '*') {
                            return fatalError(reader, "Invalid ContentSpec " + fmt(sb.toString()) +" "+hex(c));
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
                return fatalError(reader, "Invalid ContentSpec " + hex(c));
            }
        }
    }

    /**
     * Call when quote has been read. Leaves cursor after final quote
     */
    private String readSystemLiteral(final CPReader reader, int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"') {
            int start = len;
            while ((c = reader.read()) != quote) {
                if (c >= 0) {
                    append(c);
                } else {
                    return fatalError(reader, "EOF reading SystemLiteral");
                }
            }
            c = reader.read();
            String s = newString(start);
            len = start;
            return s;
        } else {
            return fatalError(reader, "Invalid SystemLiteral " + hex(c));
        }
    }

    /**
     * Call when quote has been read. Leaves cursor after final quote
     */
    private String readPubidLiteral(final CPReader reader, int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"') {
            int start = len;
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
                    return fatalError(reader, "EOF reading Pubiditeral");
                } else {
                    return fatalError(reader, "Invalid PubidLiteral " + hex(c));
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
            return fatalError(reader, "Invalid PubidLiteral " + hex(quote));
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
                    fatalError(reader, "Invalid Reference " + hex(c));
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
                        fatalError(reader, "Invalid Reference " + hex(c));
                        return null;
                    }
                    if (v > 0x10FFFF) {
                        fatalError(reader, "Invalid Reference " + hex(c));
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
                        fatalError(reader, "Invalid Reference " + hex(c));
                        return null;
                    }
                    if (v > 0x10FFFF) {
                        fatalError(reader, "Invalid Reference " + hex(c));
                    }
                }
            } else {
                fatalError(reader, "Invalid Reference " + hex(c));
                return null;
            }
            if (v < 20) {
                if (reader.isXML11()) {
                    if (v == 0) {
                        fatalError(reader, "Invalid char " + hex(v));
                        return null;
                    }
                } else if (v != 9 && v != 10 && v != 13) {
                    fatalError(reader, "Invalid char " + hex(v));
                    return null;
                }
            } else if (v >= 0xd800 && (v <= 0xdfff || v == 0xfffe || v == 0xffff || v > 0x10ffff)) { 
                fatalError(reader, "Invalid Reference " + hex(v));
                return null;
            }
            entity = new Entity(null, Character.toString(v));       // takes codepoint from Java11
        } else if (isNameStartChar(c)) {
            int start = len;
            append(c);
            c = reader.read();
            while (c != ';' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            if (c != ';') {
                fatalError(reader, "Invalid EntityReference " + hex(c));
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
                    entity = new Entity(name, null);
                }
            }
        } else {
            fatalError(reader, "Invalid Reference " + hex(c));
        }
        return entity;
    }

    /**
     * PEReference ::= ...
     * Call after read returns '%'. Leaves cursor after ';'
     */
    private Entity readPEReference(final CPReader reader) throws SAXException, IOException {
        c = reader.read();
        if (isNameStartChar(c)) {
            int start = len;
            append(c);
            c = reader.read();
            while (c != ';' && isNameChar(c)) {
                append(c);
                c = reader.read();
            }
            if (c != ';') {
                fatalError(reader, "Invalid PEReference " + hex(c));
                return null;
            }
            String name = newString(start);
            len = start;
            Entity entity = dtd == null ? null : dtd.getEntity("%" + name);
            if (entity == null) {
                fatalError(reader, "Undefined parameter entity %" + name + ";");
                return null;
            }
            return entity;
        } else {
            fatalError(reader, "Invalid PEReference " + hex(c));
            return null;
        }
    }

    /**
     * EntityValue      ::= '"' ([^%&"] | PEReference | Reference)* '"' |  "'" ([^%&'] | PEReference | Reference)* "'"
     */
    private String readEntityValue(final CPReader reader, final int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"') {
            int start = len;
            // Expand parameters here (done in read) but not genreal entities
            // r- https://www.w3.org/TR/REC-xml/#intern-replacement
            while ((c = reader.read()) != quote) {
                if (c == '%') {
                    Entity entity = readPEReference(reader);
                    CPReader entityReader = entity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
                    if (entityReader == null) {
                        fatalError(reader, "Unresolved parameter entity %" + entity.getName() + ";");
                    }
                    int c;
                    if (lexicalHandler != null) {
                        lexicalHandler.startEntity("%" + entity.getName());
                    }
                    while ((c=entityReader.read()) >= 0) {
                        append(c);
                    }
                    entityReader.close();
                    if (lexicalHandler != null) {
                        lexicalHandler.endEntity("%" + entity.getName());
                    }
                } else if (c == '&') {
                    Entity entity = readReference(reader);
                    if (entity.isCharacter()) {
                        append(entity.getValue());
                    } else {
                        append('&');
                        append(entity.getName());
                        append(';');
                    }
                } else if (c < 0) {
                    return fatalError(reader, "EOF reading EntityValue");
                } else {
                    append(c);
                }
            }
            String s = newString(start);
            len = start;
            return s;
        } else {
            return fatalError(reader, "Invalid EntityValue " + hex(quote));
        }
    }

    /**
     * whitespace is collapsed to space; whitespace in entities is preserved.
     * entities must be expanded
     * Cursor left after final quote
     */
    private String readAttValue(final CPReader reader, final int quote) throws SAXException, IOException {
        if (quote == '\'' || quote == '"' || quote == -1) {
            int start = len;
            while ((c = reader.read()) != quote) {
                if (c == '&') {
                    Entity entity = readReference(reader);
                    if (entity.isInvalid()) {
                        // should warn
                        append('&');
                        append(entity.getName());
                        append(';');
                    } else if (entity.isExternal()) {
                        fatalError(reader, "Invalid external reference &" + entity.getName() + "; in AttValue");
                    } else if (entity.isMarkup()) {
                        if (entityStack.contains(entity)) {
                            fatalError(reader, "Self-referencing entity &" + entity.getName() + ";");
                        } else {
                            entityStack.add(entity);
                            CPReader entityReader = entity.getReader(null, null, reader.isXML11());
                            readAttValue(entityReader, -1); // recursive!
                            entityReader.close();
                            entityStack.remove(entityStack.size() - 1);
                        }
                    } else {
                        append(entity.getValue());
                    }
                } else if (c == '<') {
                    fatalError(reader, "Invalid AttributeValue " + hex(quote));
                } else if (c < 0) {
                    fatalError(reader, "EOF reading AttributeValue");
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
            return fatalError(reader, "Invalid AttributeValue " + hex(quote));
        }
    }

    /**
     * Call after read '<' and '-'. Cursor left after final '>'
     */
    private void readComment(final CPReader reader) throws SAXException, IOException {
        c = reader.read();
        if (c == '-') {
            int start = len;
            while ((c=reader.read()) >= 0) {
                if (c == '-') {
                    c = reader.read();
                    if (c == '-') {
                        c = reader.read();
                        if (c == '>') {
                            if (lexicalHandler != null) {
                                lexicalHandler.comment(buf, start, len - start);
                            }
                            len = start;
                            return;
                        } else {
                            fatalError(reader, "\"--\" not allowed in comment");
                        }
                    } else {
                        append('-');
                        append(c);
                    }
                } else {
                    append(c);
                }
            }
            fatalError(reader, "EOF in comment");
        } else {
            fatalError(reader, "Invalid token \"<!-\"");
        }
    }

    /**
     * Call after <[CDATA. Leave cursor after final ']]>'
     */
    private void readCDATA(final CPReader reader) throws SAXException, IOException {
        if (c != '[') {
            fatalError(reader, "Bad token <![CDATA " + hex(c));
        }
        if (lexicalHandler != null) {
            lexicalHandler.startCDATA();
        }
        int start = len;
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
                contentHandler.characters(buf, start, len - start);
                if (lexicalHandler != null) {
                    lexicalHandler.endCDATA();
                }
                len = start;
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
        fatalError(reader, "EOF in CDATA");
    }

    /**
     * Call after "<[INCLUDE". Leave cursor afte final ]]>
     * Return the CPReader to read
     */
    private CPReader readINCLUDE(final CPReader reader) throws SAXException, IOException {
        if (isS(c)) {
            readS(reader);
        }
        if (c != '[') {
            fatalError(reader, "Bad token <![INCLUDE " + hex(c));
        }
        int start = len;
        final int line = reader.getLineNumber();
        final int col = reader.getColumnNumber();
        while ((c=reader.read()) >= 0) {
            if (c == ']') {
                if ((c=reader.read()) == ']') {
                    if ((c=reader.read()) == '>') {
                        String input = newString(start);
                        len = start;
                        return CPReader.getReader(input, reader.getPublicId(), reader.getSystemId(), line, col, reader.isXML11());
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
        fatalError(reader, "EOF in INCLUDE");
        return null;
    }

    private void readIGNORE(final CPReader reader) throws SAXException, IOException {
        if (isS(c)) {
            readS(reader);
        }
        if (c != '[') {
            fatalError(reader, "Bad token <![IGNORE " + hex(c));
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
        fatalError(reader, "EOF in IGNORE");
    }

    /**
     * Call after "<!DOCTYPE", leave cursor after tailing ">"
     */
    private void readDOCTYPE(final CPReader reader) throws SAXException, IOException {
        readS(reader);
        final String name = readName(reader);
        String pubid = null, sysid = null;
        CPReader internalSubset = null;
        if (isS(c)) {
            readS(reader);
        }
        // Read ExternalID
        if (c == 'S') {
            String s = readName(reader);
            if (!s.equals("SYSTEM")) {
                fatalError(reader, "Invalid DOCTYPE " + fmt(name));
            }
            readS(reader);
            sysid = readSystemLiteral(reader, c);
            if (isS(c)) {
                readS(reader);
            }
        } else if (c == 'P') {
            String s = readName(reader);
            if (!s.equals("PUBLIC")) {
                fatalError(reader, "Invalid DOCTYPE " + fmt(name));
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
                            lexicalHandler = new DefaultHandler2() {
                                public void comment(char[] buf, int off, int len) {
                                    sb.append('-');
                                    sb.append(buf, off, len);
                                    sb.append("--");
                                }
                            };
                            readComment(reader);
                            lexicalHandler = lh;
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
            if (c != ']') {
                fatalError(reader, "Bad token " + hex(c));
            }
            internalSubset = CPReader.getReader(sb.toString(), reader.getPublicId(), reader.getSystemId(), line, col, reader.isXML11());
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
        }
        if (c != '>') {
            fatalError(reader, "Bad token " + hex(c));
        }
        dtd = new DTD(factory, pubid, reader.getSystemId(), sysid);
        Entity dtdentity = new Entity(factory, null, name, null, pubid, sysid, -1, -1);
        CPReader dtdreader = null;
        if (pubid == null && sysid == null) {
            // First, to match Xerces
            dtdreader = dtdentity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
        }
        if (lexicalHandler != null) {
            lexicalHandler.startDTD(name, pubid, sysid);
        }
        if (internalSubset != null) {
            CPReader tmp = curreader;
            curreader = internalSubset;
            readInternalSubset(internalSubset);
            curreader = tmp;
            internalSubset.close();
        }
        if (pubid != null || sysid != null) {
            dtdreader = dtdentity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
        }
        if (dtdreader != null) {
            if (lexicalHandler != null) {
                lexicalHandler.startEntity("[dtd]");
            }
            CPReader tmp = curreader;
            curreader = dtdreader;
            readExternalSubset(dtdreader, true);
            curreader = tmp;
            dtdreader.close();
            if (lexicalHandler != null) {
                lexicalHandler.endEntity("[dtd]");
            }
        }
        if (lexicalHandler != null) {
            lexicalHandler.endDTD();
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
                            fatalError(reader, "Bad token \"" + s + "\"");
                        }
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        fatalError(reader, "Bad processing instruction target \"<?xml\"");
                    } else {
                        readPI(reader, target, true);
                    }
                } else {
                    fatalError(reader, "Bad token < " + hex(c));
                }
            } else if (c == '%') {
                Entity entity = readPEReference(reader);
                if (lexicalHandler != null) {
                    lexicalHandler.startEntity("%" + entity.getName());
                }
                if (entityStack.contains(entity)) {
                    fatalError(reader, "Self-referencing entity &" + entity.getName() + ";");
                } else {
                    entityStack.add(entity);
                    CPReader entityReader = entity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
                    if (entity.isExternal()) {
                        readExternalSubset(entityReader, true);
                    } else {
                        readInternalSubset(entityReader);
                    }
                    entityReader.close();
                    entityStack.remove(entityStack.size() - 1);
                }
                if (lexicalHandler != null) {
                    lexicalHandler.endEntity("%" + entity.getName());
                }
            } else if (!isS(c)) {
                fatalError(reader, "Invalid internal subset token " + hex(c));
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
                            readExternalSubset(includeReader, false);
                            includeReader.close();
                        } else if ("IGNORE".equals(s)) {
                            readIGNORE(reader);
                        } else {
                            fatalError(reader, "Bad token \"<![" + s + "\"");
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
                            fatalError(reader, "Bad token \"<!" + s + "\"");
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
                            fatalError(reader, "Bad processing instruction target \"<?xml\"");
                        }
                    } else {
                        readPI(reader, target, true);
                    }
                } else {
                    fatalError(reader, "Bad token < " + hex(c));
                }
            } else if (c == '%') {
                Entity entity = readPEReference(reader);
                if (lexicalHandler != null) {
                    lexicalHandler.startEntity("%" + entity.getName());
                }
                if (entityStack.contains(entity)) {
                    fatalError(reader, "Self-referencing entity &" + entity.getName() + ";");
                } else {
                    entityStack.add(entity);
                    CPReader entityReader = entity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
                    readExternalSubset(entityReader, false);
                    entityReader.close();
                    entityStack.remove(entityStack.size() - 1);
                }
                if (lexicalHandler != null) {
                    lexicalHandler.endEntity("%" + entity.getName());
                }
            } else if (!isS(c)) {
                fatalError(reader, "Invalid internal subset token " + hex(c));
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
//                fatalError(reader, "Entity name " + fmt(name) + " cannot contain colon when parsing with namespaces");
            }
            readS(reader);
        }
        if (c == '"' || c == '\'') {
            int line = reader.getLineNumber();
            int col = reader.getColumnNumber();
            String value = readEntityValue(reader, c);
            if (declHandler != null) {
                declHandler.internalEntityDecl(name, value);
            }
            dtd.addEntity(new Entity(factory, dtd, name, value, reader.getPublicId(), reader.getSystemId(), line, col));
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            if (c != '>') {
                fatalError(reader, "Bad token " + hex(c));
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
                fatalError(reader, "Bad token \"" + name + "\"");
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
                    fatalError(reader, "Bad token \"" + s + "\"");
                }
            }
            if (c != '>') {
                fatalError(reader, "Bad token " + hex(c));
            }
            if (ndata != null) {
                if (dtdHandler != null) {
                    dtdHandler.unparsedEntityDecl(name, pubid, factory.resolve(reader.getSystemId(), sysid), ndata);
                }
            } else {
                String r = factory.resolve(reader.getSystemId(), sysid);
                if (declHandler != null) {
                    declHandler.externalEntityDecl(name, pubid, r);
                }
                Entity entity = new Entity(factory, dtd, name, null, pubid, sysid, -1, -1);
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
        reader = new PEReferenceExpandingCPReader(reader);
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
                    ((PEReferenceExpandingCPReader)reader).setExpanding(false);
                    defaultValue = readAttValue(reader, c);
                    ((PEReferenceExpandingCPReader)reader).setExpanding(true);
                    c = reader.read();
                } else if (attMode.equals("#IMPLIED") || attMode.equals("#REQUIRED")) {
                    defaultValue = null;
                } else {
                    fatalError(reader, "Bad token " + attMode);
                }
            } else {
                attMode = "#FIXED";
                ((PEReferenceExpandingCPReader)reader).setExpanding(false);
                defaultValue = readAttValue(reader, c);
                ((PEReferenceExpandingCPReader)reader).setExpanding(true);
                c = reader.read();
            }
            if (isS(c)) {
                readS(reader);
            }
//            System.out.println("name="+attName+" type="+attType+" mode="+attMode+" v="+fmt(defaultValue)+" c="+hex(c));
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
                fatalError(reader, e.getMessage());
            }
            if (declHandler != null) {
                declHandler.attributeDecl(name, attName, attType, attMode, defaultValue);
            }
        }
    }

    private void readELEMENT(CPReader reader) throws SAXException, IOException {
        reader = new PEReferenceExpandingCPReader(reader);
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
                fatalError(reader, e.getMessage());
            }
            if (declHandler != null) {
                declHandler.elementDecl(name, contentSpec);
            }
        } else {
            fatalError(reader, "EOF in ElementDecl: " + hex(c));
        }
    }

    private void readNOTATION(CPReader reader) throws SAXException, IOException {
        readS(reader);
        String name = readName(reader);
        readS(reader);
        String pubid = null, sysid = null;
        reader = new PEReferenceExpandingCPReader(reader);
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
            fatalError(reader, "Bad NotationDecl \"" + s + "\"");
        }
        if (c != '>') {
            fatalError(reader, "Bad NotationDecl " + hex(c));
        }
        if (dtdHandler != null) {
            dtdHandler.notationDecl(name, pubid, factory.resolve(reader.getSystemId(), sysid));
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
                fatalError(reader, "invalid prolog");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            version = readAttValue(reader, c);
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
                fatalError(reader, "invalid prolog");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            encoding = readAttValue(reader, c);
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
                fatalError(reader, "invalid prolog");
            }
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
            standalone = readAttValue(reader, c);
            c = reader.read();
            if (isS(c)) {
                readS(reader);
            }
        }
        if (document) {
            if (version == null) {
                fatalError(reader, "invalid prolog, no version");
            } else if ("1.0".equals(version)) {
                reader.setXML11(false);
            } else if ("1.1".equals(version)) {
                reader.setXML11(true);
            } else {
                fatalError(reader, "invalid prolog version \"" + version + "\"");
            }
            if (standalone == null) {
                this.standalone = false;
            } else if (standalone.equals("yes")) {
                this.standalone = true;
            } else if (standalone.equals("no")) {
                this.standalone = false;
            } else {
                fatalError(reader, "invalid prolog standalone \"" + standalone + "\"");
            }
        } else {
            if (encoding == null) {
                fatalError(reader, "invalid entity prolog, no encoding");
            }
            if (standalone != null) {
                fatalError(reader, "invalid entity prolog, found standalone \"" + standalone + "\"");
            }
        }
        if (c != '?') {
            fatalError(reader, "invalid prolog");
        }
        c = reader.read();
        if (c != '>') {
            fatalError(reader, "invalid prolog");
        }
    }

    private void readPI(final CPReader reader, final String target, final boolean indoctype) throws IOException, SAXException {
        if (target.indexOf(":") >= 0) {
//            fatalError(reader, "PI target " + fmt(target) + " cannot contain colon when parsing with namespaces");
        }
        if (isS(c)) {
            readS(reader);
            int start = len;
            boolean done = false;
            while (!done && c >= 0) {
                if (c == '?') {
                    if ((c=reader.read()) == '>') {
                        // PI in DTD seems ignored? eg xmlconf/ibm/xml-1.1/valid/P02/ibm02v01.xml
                        if (contentHandler != null && !indoctype) {
                            contentHandler.processingInstruction(target, newString(start));
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
                fatalError(reader, "EOF in ProcessingInstruction");
            }
            len = start;
        } else if (c == '?' && reader.read() == '>') {
            if (contentHandler != null && !indoctype) {
                contentHandler.processingInstruction(target, "");
            }
        } else {
            fatalError(reader, "Bad token " + hex(c));
        }
    }

    private void readETag(final CPReader reader) throws IOException, SAXException {
        c = reader.read();
        // endElement
        String qName = readName(reader);
        if (isS(c)) {
            readS(reader);
        }
        if (stack.isEmpty()) {
            fatalError(reader, "Unexpected end element tag \"</" + qName + ">\"");
        }
        Context ctx = stack.remove(stack.size() - 1);
        if (!qName.equals(ctx.name)) {
            fatalError(reader, "The element type \"" + ctx.name + "\" must be terminated by the matching end-tag \"</" + qName + ">\"");
        }
        if (c == '>') {
            int ix = qName.indexOf(':');
            String uri;
            if (ix > 0) {
                String prefix = qName.substring(0, ix);
                String localName = qName.substring(ix + 1);
                uri = ctx.namespace(prefix);
                if (contentHandler != null) {
                    contentHandler.endElement(uri, localName, qName);
                }
            } else {
                uri = ctx.namespace("");
                if (contentHandler != null) {
                    contentHandler.endElement(uri, qName, qName);
                }
            }
            if (contentHandler != null && ctx.prefixes != null) {
                for (String s : ctx.prefixes) {
                    contentHandler.endPrefixMapping(s);
                }
            }
        } else {
            fatalError(reader, "Bad endElement " + hex(c));
        }
    }

    private void readSTag(final CPReader reader) throws IOException, SAXException {
        // startElement
        final String qName = readName(reader);
        List<String> tmpatts = null;
        boolean selfClosing = false;
        if (isS(c)) {
            readS(reader);
            while (c != '>' && c != '/') {
                String attName = readName(reader);
                if (isS(c)) {
                    readS(reader);
                }
                if (c == '=') {
                    c = reader.read();
                    if (isS(c)) {
                        readS(reader);
                    }
                    String attValue = readAttValue(reader, c);
                    if (tmpatts == null) {
                        tmpatts = new ArrayList<String>();
                    }
                    for (int i=0;i<tmpatts.size();i+=2) {
                        if (tmpatts.get(i).equals(attName)) {
                            fatalError(reader, "Redefined attribute " + attName);
                        }
                    }
                    tmpatts.add(attName);
                    tmpatts.add(attValue);
                } else {
                    fatalError(reader, "Error parsing attribute value for \"" + attName + "\"");
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
            Context ctx = new Context(qName, element, stack.isEmpty() ? null : stack.get(stack.size() - 1));
            BFOAttributes atts = null;
            Map<String,Attribute> defaultAtts = element == null ? null : element.getAttributesWithDefaults();
            if (defaultAtts != null) {
                defaultAtts = new HashMap<String,Attribute>(defaultAtts);
            }
            if (tmpatts != null) {
                System.out.flush();
                for (int i=0;i<tmpatts.size();i+=2) {
                    String attQName = tmpatts.get(i);
                    if (attQName.equals("xmlns")) {
                        String attValue = tmpatts.get(i + 1);
                        if (contentHandler != null) {
                            contentHandler.startPrefixMapping("", attValue);
                            if (ctx.prefixes == null) {
                                ctx.prefixes = new ArrayList<String>();
                            }
                            ctx.prefixes.add("");
                        }
                        ctx.register("", attValue);
                        tmpatts.set(i, null);
                    } else if (attQName.startsWith("xmlns:")) {
                        String prefix = attQName.substring(6);
                        if (prefix.equals("xmlns") || prefix.equals("xml")) {
                            fatalError(reader, "Can't redefine " + fmt(prefix) + " prefix");
                        }
                        String attValue = tmpatts.get(i + 1);
                        if (contentHandler != null) {
                            contentHandler.startPrefixMapping(prefix, attValue);
                            if (ctx.prefixes == null) {
                                ctx.prefixes = new ArrayList<String>();
                            }
                            ctx.prefixes.add(prefix);
                        }
                        ctx.register(prefix, attValue);
                        tmpatts.set(i, null);
                    }
                }
                for (int i=0;i<tmpatts.size();) {
                    String attQName = tmpatts.get(i++);
                    if (attQName != null) {
                        if (atts == null) {
                            atts = new BFOAttributes();
                        }
                        if (defaultAtts != null) {
                            defaultAtts.remove(attQName);
                        }
                        String attValue = tmpatts.get(i++);
                        int ix = attQName.indexOf(":");
                        if (ix == 0 && factory.xercescompat && reader.isXML11()) {
                            fatalError(reader, "Attribute " + fmt(attQName) + " has zero-length prefix");
                        }
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

                        if (ix > 0) {
                            String prefix = attQName.substring(0, ix);
                            String localName = attQName.substring(ix + 1);
                            if (localName.indexOf(':') >= 0) {
                                fatalError(reader, "Attribute " + fmt(attQName) + " not a valid QName");
                            }
                            if (factory.xercescompat && localName.length() == 0 && reader.isXML11()) {
                                fatalError(reader, "Attribute " + fmt(attQName) + " has zero-length localName");
                            }
                            String uri = ctx.namespace(prefix);
                            if (uri == null) {
                                fatalError(reader, "The prefix " + fmt(prefix) + " for attribute " + fmt(attQName) + " associated with an element type " + fmt(qName) + " is not bound.");
                            }
                            atts.add(uri, localName, attQName, "CDATA", attValue);
                        } else {
                            atts.add("", attQName, attQName, "CDATA", attValue);
                        }
                    } else {
                        i++;
                    }
                }
            }
            if (defaultAtts != null && !defaultAtts.isEmpty()) {
                if (atts == null) {
                    atts = new BFOAttributes();
                }
                for (Map.Entry<String,Attribute> e : defaultAtts.entrySet()) {
                    String attQName = e.getKey();
                    String attValue = e.getValue().getDefault();
                    int ix = attQName.indexOf(":");
                    if (ix > 0) {
                        String prefix = attQName.substring(0, ix);
                        String localName = attQName.substring(ix + 1);
                        String uri = ctx.namespace(prefix);
                        if (uri == null) {
                            fatalError(reader, "The prefix " + fmt(prefix) + " for default attribute " + fmt(attQName) + " associated with an element type " + fmt(qName) + " is not bound.");
                        }
                        atts.add(uri, localName, attQName, "CDATA", attValue);
                    } else {
                        atts.add("", attQName, attQName, "CDATA", attValue);
                    }
                }
            }
            int ix = qName.indexOf(':');
            if (ix == 0 && factory.xercescompat && reader.isXML11()) {
                fatalError(reader, "Element " + fmt(qName) + " has zero-length prefix");
            }
            String uri;
            if (ix > 0) {
                String prefix = qName.substring(0, ix);
                String localName = qName.substring(ix + 1);
                if (localName.indexOf(':') >= 0) {
                    fatalError(reader, "Element " + fmt(qName) + " not a valid QName");
                }
                if (factory.xercescompat && localName.length() == 0 && reader.isXML11()) {
                    fatalError(reader, "Element " + fmt(qName) + " has zero-length localName");
                }
                uri = ctx.namespace(prefix);
                if (uri == null) {
                    fatalError(reader, "The prefix " + fmt(prefix) + " for element " + fmt(qName) + " is not bound.");
                }
                if (contentHandler != null) {
                    contentHandler.startElement(uri, localName, qName, atts != null ? atts : BFOAttributes.EMPTYATTS);
                    if (selfClosing) {
                        contentHandler.endElement(uri, localName, qName);
                    } else {
                        stack.add(ctx);
                    }
                }
            } else {
                uri = ctx.namespace("");
                if (contentHandler != null) {
                    contentHandler.startElement(uri, qName, qName, atts != null ? atts : BFOAttributes.EMPTYATTS);
                    if (selfClosing) {
                        contentHandler.endElement(uri, qName, qName);
                    } else {
                        stack.add(ctx);
                    }
                }
            }
            if (selfClosing && contentHandler != null && ctx.prefixes != null) {
                for (String s : ctx.prefixes) {
                    contentHandler.endPrefixMapping(s);
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
        boolean skip = false;
        if (c == '<') {
            c = reader.read();
            if (c == '?') {
                c = reader.read();
                String target = readName(reader);
                if (target.equalsIgnoreCase("xml")) {
                    readXMLPI(reader, true);
                } else {
                    readPI(reader, target, false);
                }
                c = reader.read();
            } else {
                skip = true;
            }
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
                            fatalError(reader, "Bad token \"" + s + "\"");
                        }
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        fatalError(reader, "Bad processing instruction target \"<?xml\"");
                    } else {
                        readPI(reader, target, false);
                    }
                } else {
                    // We are done with the prolog
                    break;
                }
            } else if (c < 0) {
                fatalError(reader, "EOF after prolog");
            } else if (!isS(c)) {
                fatalError(reader, "Invalid prolog " + hex(c));
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
                fatalError(reader, "The element type \"" + name + "\" must be terminated by the matching end-tag \"</" + name + ">\"");
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
                        fatalError(reader, "Bad processing instruction target \"<?xml\"");
                    } else {
                        readPI(reader, target, false);
                    }
                } else {
                    fatalError(reader, "Bad token < " + hex(c));
                }
            } else if (!isS(c)) {
                fatalError(reader, "Bad token " + hex(c));
            }
            c = reader.read();
        }
    }

    private void flush() throws IOException, SAXException {
        if (contentHandler != null) {
            boolean ignoreWhitespace = false;
            if (dtd != null) {
                Context ctx = stack.get(stack.size() - 1);
                String name = ctx.name;
                Element elt = ctx.element;
                if (elt != null) {
                    ignoreWhitespace = !elt.hasText();
                }
            }
            if (ignoreWhitespace) {
                for (int i=0;i<len;i++) {
                    if (!isS(buf[i])) {
                        ignoreWhitespace = false;
                        break;
                    }
                }
            }
            if (ignoreWhitespace) {
                contentHandler.ignorableWhitespace(buf, 0, len);
            } else {
                contentHandler.characters(buf, 0, len);
            }
        }
        len = 0;
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
        while (c > 0) {
            if (c == '<') {
                bba = 0;
                if (len > 0) {
                    flush();
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
                            fatalError(reader, "Bad token \"<![" + name + "\"");
                        }
                    } else {
                        fatalError(reader, "Bad token \"<! " + hex(c));
                    }
                } else if (c == '?') {
                    c = reader.read();
                    String target = readName(reader);
                    if (target.equalsIgnoreCase("xml")) {
                        if (allowProlog) {
                            readXMLPI(reader, false);
                            allowProlog = false;
                        } else {
                            fatalError(reader, "Bad processing instruction target \"<?xml\"");
                        }
                    } else {
                        readPI(reader, target, false);
                    }
                } else if (c == '/') {
                    readETag(reader);
                    if (stack.isEmpty()) {
                        break;
                    }
                } else {
                    readSTag(reader);
                }
            } else if (c == '&') {
                if (len > 0) {
                    flush();
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
                    fatalError(reader, "Unresolved entity &" + entity.getName() + ";");
                } else {
                    if (lexicalHandler != null) {
                        lexicalHandler.startEntity(entity.getName());
                    }
                    if (entity.isMarkup()) {
                        if (entityStack.contains(entity)) {
                            fatalError(reader, "Self-referencing entity &" + entity.getName() + ";");
                        } else {
                            entityStack.add(entity);
                            CPReader entityReader = entity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
                            readContent(entityReader, true);
                            entityReader.close();
                            entityStack.remove(entityStack.size() - 1);
                        }
                    } else {
                        append(entity.getValue());
                    }
                    if (lexicalHandler != null) {
                        lexicalHandler.endEntity(entity.getName());
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
                        fatalError(reader, "The character sequence ]]> must not appear unescaped in character data");
                        bba = 2;
                    } else {
                        bba = 0;
                    }
                } else {
                    bba = 0;
                }
            }
            c = reader.read();
        }
        if (len > 0) {
            flush();
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

    /**
     * A PEReference expanding reader
     */
    private class PEReferenceExpandingCPReader extends CPReader {
        private final CPReader freader;
        private CPReader reader;
        private List<CPReader> readerStack;
        private List<Entity> entityStack;
        private boolean expanding = true;

        PEReferenceExpandingCPReader(CPReader r) {
            this.freader = r;
            this.reader = freader;
        }

        void setExpanding(boolean expanding) {
            this.expanding = expanding;
        }

        @Override public int read() throws SAXException, IOException {
            int c = reader.read();
            while (c == '%' && expanding) {
                Entity entity = readPEReference(reader);
                if (entityStack == null) {
                    entityStack = new ArrayList<Entity>();
                    readerStack = new ArrayList<CPReader>();
                }
                if (entityStack.contains(entity)) {
                    fatalError(reader, "Self-referencing entity &" + entity.getName() + ";");
                } else {
                    reader = entity.getReader(entityResolver, reader.getSystemId(), reader.isXML11());
                    entityStack.add(entity);
                    readerStack.add(reader);
                    c = reader.read();
                }
            }
            if (c < 0 && reader != freader) {
                reader.close();
                while (c < 0 && !readerStack.isEmpty()) {
                    entityStack.remove(entityStack.size() - 1);
                    readerStack.remove(readerStack.size() - 1);
                    reader = readerStack.isEmpty() ? freader : readerStack.get(readerStack.size() - 1);
                    c = reader.read();
                }
            }
            return c;
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
    }

}
