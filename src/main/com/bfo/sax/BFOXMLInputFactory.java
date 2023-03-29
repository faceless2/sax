package com.bfo.sax;

import java.io.*;
import java.util.*;
import javax.xml.stream.*;
import javax.xml.stream.util.*;
import javax.xml.transform.Source;
import org.xml.sax.*;

public class BFOXMLInputFactory extends XMLInputFactory {

    private final BFOSAXParserFactory factory;
    private Map<String,Object> properties = new HashMap<String,Object>();
    private XMLResolver resolver;
    private XMLReporter reporter;

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
    }

    public XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter) {
        throw new UnsupportedOperationException();
    }
    public XMLStreamReader createFilteredReader(XMLStreamReader reader, StreamFilter filter) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(InputStream stream) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(InputStream stream, String encoding) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(Reader reader) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(String systemId, InputStream stream) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(String systemId, Reader reader) {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(XMLStreamReader reader) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }
    public XMLEventReader createXMLEventReader(Source source) throws XMLStreamException {
        throw new UnsupportedOperationException();
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

    private XMLStreamReader createXMLStreamReader(String systemId, InputStream stream, String encoding) throws XMLStreamException {
        InputSource source = new InputSource(stream);
        source.setSystemId(systemId);
        source.setEncoding(encoding);
        BFOXMLReader r = null;
        try {
            BFOXMLStreamReader xmlreader = new BFOXMLStreamReader(this, properties);
            r = (BFOXMLReader)factory.newSAXParser().getXMLReader();
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
            r.parse(source, xmlreader);
            return xmlreader;
        } catch (Exception e) {
            throw new XMLStreamException(e.getMessage(), r, e);
        }
    }

    public XMLStreamReader createXMLStreamReader(Source source) {
        throw new UnsupportedOperationException();
    }
    public XMLEventAllocator getEventAllocator() {
        throw new UnsupportedOperationException();
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public XMLReporter getXMLReporter() {
        return reporter;
    }

    public XMLResolver getXMLResolver() {
        return resolver;
    }

    public boolean isPropertySupported(String name) {
        return false;
    }

    public void setEventAllocator(XMLEventAllocator allocator) {
        throw new UnsupportedOperationException();
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public void setXMLReporter(XMLReporter reporter) {
        this.reporter = reporter;
    }

    public void setXMLResolver(XMLResolver resolver) {
        this.resolver = resolver;
    }

}
