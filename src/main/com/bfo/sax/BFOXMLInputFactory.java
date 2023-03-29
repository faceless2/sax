package com.bfo.sax;

import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import javax.xml.stream.*;
import javax.xml.stream.util.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.xml.sax.*;

public class BFOXMLInputFactory extends XMLInputFactory {

    private final BFOSAXParserFactory factory;
    private final Map<String,Object> properties;

    public BFOXMLInputFactory() {
        try {
            factory = new BFOSAXParserFactory();
            factory.setNamespaceAware(true);
            factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        } catch (SAXNotRecognizedException e) {
            throw new RuntimeException(e);
        } catch (SAXNotSupportedException e) {
            throw new RuntimeException(e);
        }
        properties = new HashMap<String,Object>();
        setProperty(IS_NAMESPACE_AWARE, Boolean.TRUE);
        setProperty(IS_VALIDATING, Boolean.FALSE);
        setProperty(IS_COALESCING, Boolean.FALSE);
        setProperty(IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        setProperty(IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.TRUE);
        setProperty(SUPPORT_DTD, Boolean.TRUE);
    }

    public XMLEventReader createXMLEventReader(InputStream stream) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(stream));
    }

    public XMLEventReader createXMLEventReader(InputStream stream, String encoding) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(stream, encoding));
    }

    public XMLEventReader createXMLEventReader(Reader reader) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(reader));
    }

    public XMLEventReader createXMLEventReader(String systemId, InputStream stream) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(systemId, stream));
    }

    public XMLEventReader createXMLEventReader(String systemId, Reader reader) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(systemId, reader));
    }

    public XMLEventReader createXMLEventReader(Source source) throws XMLStreamException {
        return createXMLEventReader(createXMLStreamReader(source));
    }

    public XMLEventReader createXMLEventReader(XMLStreamReader reader) throws XMLStreamException {
        return new BFOXMLEventReader(this, reader);
    }

    public XMLStreamReader createXMLStreamReader(InputStream stream) throws XMLStreamException {
        return createXMLStreamReader(null, stream, null);
    }

    public XMLStreamReader createXMLStreamReader(InputStream stream, String encoding) throws XMLStreamException {
        return createXMLStreamReader(null, stream, encoding);
    }

    public XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException {
        return createXMLStreamReader(null, reader);
    }

    public XMLStreamReader createXMLStreamReader(String systemId, InputStream stream) throws XMLStreamException {
        return createXMLStreamReader(systemId, stream, null);
    }

    public XMLStreamReader createXMLStreamReader(Source source) throws XMLStreamException {
        if (source instanceof StreamSource) {
            StreamSource s = (StreamSource)source;
            String systemId = s.getSystemId();
            String publicId = s.getPublicId();
            InputStream stream = s.getInputStream();
            Reader reader = s.getReader();
            if (stream != null) {
                return createXMLStreamReader(systemId, stream);
            } else if (reader != null) {
                return createXMLStreamReader(systemId, reader);
            }
        }
        throw new UnsupportedOperationException();
    }

    private XMLStreamReader createXMLStreamReader(String systemId, InputStream stream, String encoding) throws XMLStreamException {
        InputSource source = new InputSource(stream);
        source.setSystemId(systemId);
        source.setEncoding(encoding);
        BFOXMLReader r = null;
        try {
            BFOXMLStreamReader xmlreader = new BFOXMLStreamReader(this, properties);
            r = (BFOXMLReader)factory.newSAXParser().getXMLReader();
            r.setFeature("http://xml.org/sax/features/namespaces", getProperty(IS_NAMESPACE_AWARE).equals(Boolean.TRUE));
            r.parse(source, xmlreader);
            return xmlreader;
        } catch (Exception e) {
            throw new XMLStreamException(e.getMessage(), r, e);
        }
    }

    public XMLStreamReader createXMLStreamReader(String systemId, Reader reader) throws XMLStreamException {
        InputSource source = new InputSource(reader);
        source.setSystemId(systemId);
        BFOXMLReader r = null;
        try {
            BFOXMLStreamReader xmlreader = new BFOXMLStreamReader(this, properties);
            r = (BFOXMLReader)factory.newSAXParser().getXMLReader();
            r.setFeature("http://xml.org/sax/features/namespaces", getProperty(IS_NAMESPACE_AWARE).equals(Boolean.TRUE));
            r.parse(source, xmlreader);
            return xmlreader;
        } catch (Exception e) {
            throw new XMLStreamException(e.getMessage(), r, e);
        }
    }

    public XMLEventAllocator getEventAllocator() {
        return (XMLEventAllocator)getProperty(ALLOCATOR);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public XMLReporter getXMLReporter() {
        return (XMLReporter)getProperty(REPORTER);
    }

    public XMLResolver getXMLResolver() {
        return (XMLResolver)getProperty(RESOLVER);
    }

    public boolean isPropertySupported(String name) {
        switch (name) {
            case ALLOCATOR:
            case IS_COALESCING:
            case IS_NAMESPACE_AWARE:
            case IS_REPLACING_ENTITY_REFERENCES:
            case IS_SUPPORTING_EXTERNAL_ENTITIES:
            case IS_VALIDATING:
            case REPORTER:
            case RESOLVER:
            case SUPPORT_DTD:
                return true;
            default:
                return false;
        }
    }

    public void setEventAllocator(XMLEventAllocator allocator) {
        setProperty(ALLOCATOR, allocator);
    }

    public void setProperty(String name, Object value) {
        if (ALLOCATOR.equals(name)) {
            if (value != null && !(value instanceof XMLEventAllocator)) {
                throw new IllegalArgumentException("Not an XMLEventAllocator");
            }
        } else if (IS_COALESCING.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            }
        } else if (IS_NAMESPACE_AWARE.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            }
        } else if (IS_REPLACING_ENTITY_REFERENCES.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            }
        } else if (IS_SUPPORTING_EXTERNAL_ENTITIES.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            }
        } else if (IS_VALIDATING.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            } else if (value.equals(Boolean.TRUE)) {
                throw new IllegalArgumentException("Only false supported");
            }
        } else if (REPORTER.equals(name)) {
            if (value != null && !(value instanceof XMLReporter)) {
                throw new IllegalArgumentException("Not an XMLReporter");
            }
        } else if (RESOLVER.equals(name)) {
            if (value != null && !(value instanceof XMLResolver)) {
                throw new IllegalArgumentException("Not an XMLResolver");
            }
        } else if (SUPPORT_DTD.equals(name)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Not a Boolean");
            }
        } else {
            throw new IllegalArgumentException("Unrecognised property \"" + name + "\"");
        }
        properties.put(name, value);
    }

    public void setXMLReporter(XMLReporter reporter) {
        setProperty(REPORTER, reporter);
    }

    public void setXMLResolver(XMLResolver resolver) {
        setProperty(RESOLVER, resolver);
    }

    // Last two methods unsupported

    public XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public XMLStreamReader createFilteredReader(XMLStreamReader reader, StreamFilter filter) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

}
