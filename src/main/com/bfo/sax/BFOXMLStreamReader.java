package com.bfo.sax;

import javax.xml.stream.*;
import javax.xml.namespace.*;
import javax.xml.XMLConstants;
import org.xml.sax.*;
import java.io.*;
import java.util.*;

class BFOXMLStreamReader implements XMLStreamReader {

    private final XMLReporter reporter;
    private final XMLResolver resolver;
    private ThreadedQueue q;
    private BFOXMLReader xmlreader;
    private boolean active;
    private int state;
    private Attributes atts;
    private Map<String,Object> properties;
    private String version, charset, encoding, standalone;
    private String uri, localName, prefix, piTarget, piData;
    private char[] text;
    private int textOffset, textLength;
    private List<Map<String,String>> prefixes;
    private Map<String,String> prefixMap;
    private Location location;

    BFOXMLStreamReader(BFOXMLInputFactory factory, Map<String,Object> properties) {
        this.reporter = factory.getXMLReporter();
        this.resolver = factory.getXMLResolver();
        this.active = true;
        this.prefixes = new ArrayList<Map<String,String>>();
        this.properties = properties != null ? properties : Collections.<String,Object>emptyMap();
    }

    void init(BFOXMLReader xmlreader, ThreadedQueue q) {
        this.xmlreader = xmlreader;
        this.q = q;
    }

    /**
     * Get next parsing event
     */
    public int next() throws XMLStreamException {
        state = -1;
        boolean halt = false, indtd = false;
        final Object[] o = new Object[8];
        while (active && !halt) {
            ThreadedQueue.MsgType type = q.peek(o);
            String qName;
            int ix;
            // ATTRIBUTE, CDATA, CHARACTERS, COMMENT, DTD, END_DOCUMENT, END_ELEMENT, ENTITY_DECLARATION, ENTITY_REFERENCE, NAMESPACE, NOTATION_DECLARATION, PROCESSING_INSTRUCTION, SPACE, START_DOCUMENT, START_ELEMENT
            try {
                switch (type) {
                    case startDTD:
                        indtd = true;
                        break;
                    case attributeDecl:
                    case elementDecl:
                    case externalEntityDecl:
                    case notationDecl:
                    case unparsedEntityDecl:
                    case resolveEntity:
                    case endEntity:
                    case internalEntityDecl:
                    case startDocument:
                    case endPrefixMapping:
                        break;
                    case comment:
                        state = COMMENT;
                        text = (char[])o[0];
                        textOffset = (Integer)o[1];
                        textLength = (Integer)o[2];
                        halt = true;
                        break;
                    case endCDATA:
                        halt = true;
                        break;
                    case endDTD:
                        indtd = false;
                        state = DTD;
                        halt = true;
                        break;
                    case startCDATA:
                        state = CDATA;
                        text = new char[0];
                        textOffset = textLength = 0;
                        break;
                    case startEntity:
                        localName = (String)o[0];
                        state = ENTITY_REFERENCE;
                        text = new char[0];
                        textOffset = textLength = 0;
                        break;
                    case characters:
                        text = (char[])o[0];
                        textOffset = (Integer)o[1];
                        textLength = (Integer)o[2];
                        if (state < 0 && textLength > 0) {
                            state = CHARACTERS;
                            halt = true;
                        }
                        break;
                    case ignorableWhitespace:
                        text = (char[])o[0];
                        textOffset = (Integer)o[1];
                        textLength = (Integer)o[2];
                        if (state < 0 && textLength > 0) {
                            state = SPACE;
                            halt = true;
                        }
                        break;
                    case endDocument:
                        state = END_DOCUMENT;
                        halt = true;
                        break;
                    case endElement:
                        state = END_ELEMENT;
                        uri = (String)o[0];
                        qName = (String)o[1];
                        localName = (String)o[2];
                        ix = qName.indexOf(":");
                        prefix = ix > 0 && uri.length() > 0 ? qName.substring(0, ix) : "";
                        prefixMap = prefixes.remove(prefixes.size() - 1);
                        halt = true;
                        break;
                    case processingInstruction:
                        piTarget = (String)o[0];
                        piData = (String)o[1];
                        state = PROCESSING_INSTRUCTION;
                        halt = true;
                        break;
                    case skippedEntity:
                        localName = (String)o[0];
                        state = ENTITY_REFERENCE;
                        text = new char[0];
                        textOffset = textLength = 0;
                        break;
                    case startElement:
                        state = START_ELEMENT;
                        uri = (String)o[0];
                        qName = (String)o[1];
                        localName = (String)o[2];
                        atts = (Attributes)o[3];
                        ix = qName.indexOf(":");
                        prefix = ix > 0 && uri.length() > 0 ? qName.substring(0, ix) : "";
                        if (prefixMap == null) {
                            prefixMap = new HashMap<String,String>();
                        }
                        prefixes.add(prefixMap);
                        prefixMap = null;
                        halt = true;
                        break;
                    case setDocumentLocator:
                        location = (Location)o[0];
                        break;
                    case startPrefixMapping:
                        if (prefixMap == null) {
                            prefixMap = new HashMap<String,String>();
                        }
                        prefix = (String)o[0];
                        uri = (String)o[1];
                        prefixMap.put(prefix, uri);
//                        state = NAMESPACE;
//                        halt = true;
                        break;
                    case warning:
                    case error:
                        if (reporter != null) {
                            Exception e = (Exception)o[0];
                            if (e instanceof SAXParseException) {
                                reporter.report(e.getMessage(), type == ThreadedQueue.MsgType.warning ? "warning" : "error", null, location);
                            } else {
                                reporter.report(e.getMessage(), type == ThreadedQueue.MsgType.warning ? "warning" : "error", e, location);
                            }
                        }
                        q.reply(null);
                        break;
                    case fatalError:
                        q.reply(null);
                        active = false;
                        Exception e = (Exception)o[0];
                        if (e instanceof SAXParseException) {
                            throw new XMLStreamException(e.getMessage(), location);
                        } else {
                            throw new XMLStreamException(e.getMessage(), location, e);
                        }
                    case getExternalSubset:
                        q.reply(null);
                        break;
                    case resolveEntity2:
                        if (resolver != null) {
                            q.reply(toInputSource((String)o[0], (String)o[1], (String)o[2]));
                        } else {
                            q.reply(null);
                        }
                        break;
                    case xmlpi:
                        charset = (String)o[0];
                        encoding = (String)o[1];
                        standalone = (String)o[2];
                        version = (String)o[3];
                        if (version == null) {
                            version = "1.0";
                        }
                        state = START_DOCUMENT;
                        halt = true;
                        break;
                    case close:
                        active = false;         // heard after endDocument on clean exit
                        halt = true;
                        break;
                    default:
                        throw new IllegalStateException("Unhandled type " + type);
                }
            } finally {
                q.remove();
            }
            if (halt && indtd) {
                halt = false;
            }
        }
        return state;
    }

    private static String type(int ix) {
        switch (ix) {
            case ATTRIBUTE: return "ATTRIBUTE";
            case CDATA: return "CDATA";
            case CHARACTERS: return "CHARACTERS";
            case COMMENT: return "COMMENT";
            case DTD: return "DTD";
            case END_DOCUMENT: return "END_DOCUMENT";
            case END_ELEMENT: return "END_ELEMENT";
            case ENTITY_DECLARATION: return "ENTITY_DECLARATION";
            case ENTITY_REFERENCE: return "ENTITY_REFERENCE";
            case NAMESPACE: return "NAMESPACE";
            case NOTATION_DECLARATION: return "NOTATION_DECLARATION";
            case PROCESSING_INSTRUCTION: return "PROCESSING_INSTRUCTION";
            case SPACE: return "SPACE";
            case START_DOCUMENT: return "START_DOCUMENT";
            case START_ELEMENT: return "START_ELEMENT";
            default: return "Unknown-" + ix;
        }
    }

    private InputSource toInputSource(String publicId, String systemId, String baseuri) throws XMLStreamException {
        InputSource source = null;
        Object o = resolver == null ? null : resolver.resolveEntity(publicId, systemId, baseuri, "");
        if (o instanceof InputStream) {
            source = new InputSource((InputStream)o);
        } else if (o != null) {
            throw new UnsupportedOperationException(o.toString());
        }
        if (source != null) {
            source.setPublicId(publicId);
            source.setSystemId(systemId);
        }
        return source;
    }

    // Frees any resources associated with this Reader.
    @Override public void close() throws XMLStreamException {
        xmlreader.postParse();
    }

    // Returns the count of attributes on this START_ELEMENT, this method is only valid on a START_ELEMENT or ATTRIBUTE.
    @Override public int getAttributeCount() {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getLength();
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the localName of the attribute at the provided index
    @Override public String getAttributeLocalName(int index) {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getLocalName(index);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the qname of the attribute at the provided index
    @Override public QName getAttributeName(int index) {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            String uri = atts.getURI(index);
            if (uri.equals("")) {
                return new QName("", atts.getLocalName(index), "");
            } else {
                String qName = atts.getQName(index);
                int ix = qName.indexOf(":");
                return new QName(uri, qName.substring(ix + 1), qName.substring(0, ix));
            }
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the namespace of the attribute at the provided index
    @Override public String getAttributeNamespace(int index) {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getURI(index);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the prefix of this attribute at the provided index
    @Override public String getAttributePrefix(int index) {
        return getAttributeName(index).getPrefix();
    }

    // Returns the XML type of the attribute at the provided index
    @Override public String getAttributeType(int index) {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getType(index);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the value of the attribute at the index
    @Override public String getAttributeValue(int index)  {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getValue(index);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the normalized attribute value of the attribute with the namespace and localName If the namespaceURI is null the namespace is not checked for equality
    @Override public String getAttributeValue(String namespaceURI, String localName) {
        if (state == START_ELEMENT || state == ATTRIBUTE) {
            return atts.getValue(namespaceURI, localName);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the character encoding declared on the xml declaration Returns null if none was declared
    @Override public String getCharacterEncodingScheme() {
        if (state == START_DOCUMENT) {
            return charset;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Reads the content of a text-only element, an exception is thrown if this is not a text-only element.
    @Override public String getElementText() throws XMLStreamException {
        if (state != START_ELEMENT) {
            throw new IllegalStateException("Event type "+type(state));
        }
        int eventType = next();
        StringBuilder buf = new StringBuilder();
        while (eventType != XMLStreamConstants.END_ELEMENT) {
            if (eventType == XMLStreamConstants.CHARACTERS || eventType == XMLStreamConstants.CDATA || eventType == XMLStreamConstants.SPACE || eventType == XMLStreamConstants.ENTITY_REFERENCE) {
                buf.append(getText());
            } else if(eventType == XMLStreamConstants.PROCESSING_INSTRUCTION || eventType == XMLStreamConstants.COMMENT) {
                // skipping
            } else if(eventType == XMLStreamConstants.END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content", getLocation());
            } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT", getLocation());
            } else {
                throw new XMLStreamException("Unexpected event type "+eventType, getLocation());
            }
            eventType = next();
        }
        return buf.toString();
    }

    // Return input encoding if known or null if unknown.
    @Override public String getEncoding() {
        if (state == START_DOCUMENT) {
            return encoding;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns an integer code that indicates the type of the event the cursor is pointing to.
    @Override public int getEventType() {
        return state;
    }

    // Returns the (local) name of the current event.
    @Override public String getLocalName() {
        if (state == START_ELEMENT || state == END_ELEMENT || state == ENTITY_REFERENCE) {
            return localName;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Return the current location of the processor.
    @Override public Location getLocation() {
        return location;
    }

    // Returns a QName for the current START_ELEMENT or END_ELEMENT event
    @Override public QName getName() {
        if (state == START_ELEMENT || state == END_ELEMENT) {
            return new QName(uri, localName, prefix);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns a read only namespace context for the current position.
    @Override public NamespaceContext getNamespaceContext() {
        return new NamespaceContext() {
            @Override public String getNamespaceURI(String prefix) {
                if (prefix == null) {
                    throw new IllegalArgumentException();
                } else if (prefix.equals("xml")) {
                    return XMLConstants.XML_NS_URI;
                } else if (prefix.equals("xmlns")) {
                    return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
                } else {
                    for (int i=prefixes.size()-1;i>=0;i--) {
                        Map<String,String> m = prefixes.get(i);
                        String uri = m.get(prefix);
                        if (uri != null) {
                            return uri;
                        }
                    }
                    return null;
                }
            }
            @Override public String getPrefix(String uri) {
                if (uri == null) {
                    throw new IllegalArgumentException();
                } else if (uri.equals(XMLConstants.XML_NS_URI)) {
                    return "xml";
                } else if (uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    return "xmlns";
                } else {
                    for (int i=prefixes.size()-1;i>=0;i--) {
                        Map<String,String> m = prefixes.get(i);
                        for (Map.Entry<String,String> e : m.entrySet()) {
                            if (e.getValue().equals(uri)) {
                                return e.getKey();
                            }
                        }
                    }
                    return null;
                }
            }
            @Override public Iterator<String> getPrefixes(String uri) {
                if (uri == null) {
                    throw new IllegalArgumentException();
                } else if (uri.equals(XMLConstants.XML_NS_URI)) {
                    return Collections.<String>singleton("xml").iterator();
                } else if (uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    return Collections.<String>singleton("xmlns").iterator();
                } else if (prefixes.isEmpty()) {
                    return Collections.<String>emptyList().iterator();
                } else {
                    List<String> out = new ArrayList<String>();
                    Set<String> seen = new HashSet<String>();
                    for (int i=prefixes.size()-1;i>=0;i--) {
                        Map<String,String> m = prefixes.get(i);
                        for (Map.Entry<String,String> e : m.entrySet()) {
                            if (e.getValue().equals(uri) && !seen.contains(e.getKey())) {
                                out.add(e.getKey());
                            } else {
                                seen.add(e.getKey());
                            }
                        }
                    }
                    return out.iterator();
                }
            }
        };
    }

    // Returns the count of namespaces declared on this START_ELEMENT or END_ELEMENT, this method is only valid on a START_ELEMENT, END_ELEMENT or NAMESPACE.
    @Override public int getNamespaceCount() {
        if (state == NAMESPACE) {
            return 1;
        } else if (state == START_ELEMENT) {
            return prefixes.get(prefixes.size() - 1).size();
        } else if (state == END_ELEMENT) {
            return prefixMap.size();
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the prefix for the namespace declared at the index.
    @Override public String getNamespacePrefix(int index) {
        if (state == START_ELEMENT || state == END_ELEMENT || state == NAMESPACE) {
            Map<String,String> m = state == END_ELEMENT ? prefixMap : prefixes.isEmpty() ? Collections.<String,String>emptyMap() : prefixes.get(prefixes.size() - 1);
            for (Map.Entry<String,String> e : m.entrySet()) {
                if (index-- == 0) {
                    String prefix = e.getKey();
                    if ("".equals(prefix)) {
                        prefix = null;
                    }
                    return prefix;
                }
            }
            return null;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // If the current event is a START_ELEMENT or END_ELEMENT this method returns the URI of the prefix or the default namespace.
    @Override public String getNamespaceURI() {
        if (state == START_ELEMENT || state == END_ELEMENT || state == NAMESPACE) {
            return uri;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the uri for the namespace declared at the index.
    @Override public String getNamespaceURI(int index) {
        if (state == START_ELEMENT || state == END_ELEMENT || state == NAMESPACE) {
            Map<String,String> m = state == END_ELEMENT ? prefixMap : prefixes.isEmpty() ? Collections.<String,String>emptyMap() : prefixes.get(prefixes.size() - 1);
            for (Map.Entry<String,String> e : m.entrySet()) {
                if (index-- == 0) {
                    return e.getValue();
                }
            }
            return null;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Return the uri for the given prefix.
    @Override public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException();
        } else if (prefix.equals("xml")) {
            return XMLConstants.XML_NS_URI;
        } else if (prefix.equals("xmlns")) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        } else {
            for (int i=prefixes.size()-1;i>=0;i--) {
                Map<String,String> m = prefixes.get(i);
                String uri = m.get(prefix);
                if (uri != null) {
                    return uri;
                }
            }
            return null;
        }
    }

    // Get the data section of a processing instruction
    @Override public String getPIData() {
        if (state == PROCESSING_INSTRUCTION) {
            return piData;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Get the target of a processing instruction
    @Override public String getPITarget() {
        if (state == PROCESSING_INSTRUCTION) {
            return piTarget;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the prefix of the current event or null if the event does not have a prefix
    @Override public String getPrefix() {
        if (state == START_ELEMENT || state == END_ELEMENT) {
            return prefix;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Get the value of a feature/property from the underlying implementation
    @Override public Object getProperty(String name) {
        return properties.get(name);
    }

    // Returns the current value of the parse event as a string, this returns the string value of a CHARACTERS event, returns the value of a COMMENT, the replacement value for an ENTITY_REFERENCE, the string value of a CDATA section, the string value for a SPACE event, or the String value of the internal subset of the DTD.
    @Override public String getText() {
        return new String(getTextCharacters(), getTextStart(), getTextLength());
    }

    // Returns an array which contains the characters from this event.
    @Override public char[] getTextCharacters() {
        if (state == CHARACTERS || state == COMMENT || state == ENTITY_REFERENCE || state == CDATA || state == SPACE || state == DTD) {
            return text;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Gets the the text associated with a CHARACTERS, SPACE or CDATA event.
    @Override public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) {
        if (sourceStart < 0 || targetStart < 0 || targetStart + length > target.length || length < 0) {
            throw new IndexOutOfBoundsException();
        }
        length = Math.min(length, getTextLength() - sourceStart);
        System.arraycopy(getTextCharacters(), getTextStart() + sourceStart, target, targetStart, length);
        return length;
    }

    // Returns the length of the sequence of characters for this Text event within the text character array.
    @Override public int getTextLength() {
        if (state == CHARACTERS || state == COMMENT || state == ENTITY_REFERENCE || state == CDATA || state == SPACE || state == DTD) {
            return textLength;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns the offset into the text character array where the first character (of this text event) is stored.
    @Override public int getTextStart() {
        if (state == CHARACTERS || state == COMMENT || state == ENTITY_REFERENCE || state == CDATA || state == SPACE || state == DTD) {
            return textOffset;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Get the xml version declared on the xml declaration Returns null if none was declared
    @Override public String getVersion() {
        if (state == START_DOCUMENT) {
            return version;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // returns a boolean indicating whether the current event has a name (is a START_ELEMENT or END_ELEMENT).
    @Override public boolean hasName() {
        return state == START_ELEMENT || state == END_ELEMENT;
    }

    // Returns true if there are more parsing events and false if there are no more events.
    @Override public boolean hasNext() {
        return active;
    }

    // Return a boolean indicating whether the current event has text.
    @Override public boolean hasText() {
        return state == CHARACTERS || state == COMMENT || state == ENTITY_REFERENCE || state == CDATA || state == SPACE || state == DTD;
    }

    // Returns a boolean which indicates if this attribute was created by default
    @Override public boolean isAttributeSpecified(int index) {
        if (state == START_ELEMENT) {
            return atts instanceof BFOAttributes && ((BFOAttributes)atts).isSpecified(index);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns true if the cursor points to a character data event
    @Override public boolean isCharacters()  {
        return state == CHARACTERS;
    }

    // Returns true if the cursor points to an end tag (otherwise false)
    @Override public boolean isEndElement() {
        return state == END_ELEMENT;
    }

    // Get the standalone declaration from the xml declaration
    @Override public boolean isStandalone() {
        if (state == START_DOCUMENT) {
            return standalone != null && "yes".equals(standalone);
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

    // Returns true if the cursor points to a start tag (otherwise false)
    @Override public boolean isStartElement() {
        return state == START_ELEMENT;
    }

    // Returns true if the cursor points to a character data event that consists of all whitespace
    @Override public boolean isWhiteSpace() {
        if (isCharacters()) {
            for (int i=0;i<textLength;i++) {
                char c = text[textOffset + i];
                if (c != 0x20 && c != 0x0a && c != 0x0d || c != 0x09) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // Skips any white space (isWhiteSpace() returns true), COMMENT, or PROCESSING_INSTRUCTION, until a START_ELEMENT or END_ELEMENT is reached.
    @Override public int nextTag() throws XMLStreamException {
        int eventType = next();
        while ((eventType == XMLStreamConstants.CHARACTERS && isWhiteSpace()) // skip whitespace
            || (eventType == XMLStreamConstants.CDATA && isWhiteSpace()) // skip whitespace
            || eventType == XMLStreamConstants.SPACE
            || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
            || eventType == XMLStreamConstants.COMMENT
            ) {
            eventType = next();
        }
        if (eventType != START_ELEMENT && eventType != END_ELEMENT) {
            throw new XMLStreamException("expected start or end tag", getLocation());
        }
        return eventType;
    }


    // Test if the current event is of the given type and if the namespace and name match the current namespace and name of the current event.
    @Override public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
        if (type != state) {
            throw new XMLStreamException("Event type " + type(type) + " specified did not match with current parser event " + type(state));
        }
        if (namespaceURI != null && !namespaceURI.equals(getNamespaceURI())) {
            throw new XMLStreamException("Namespace URI " + namespaceURI + " specified did not match with current namespace URI");
        }
        if (localName != null && !localName.equals(getLocalName())) {
            throw new XMLStreamException("LocalName " + localName + " specified did not match with current local name");
        }
        return;
    }

    @Override public boolean standaloneSet() {
        if (state == START_DOCUMENT) {
            return standalone != null;
        } else {
            throw new IllegalStateException("Event type "+type(state));
        }
    }

}
