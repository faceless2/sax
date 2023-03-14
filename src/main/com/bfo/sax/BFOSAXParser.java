package com.bfo.sax;

import javax.xml.parsers.*;
import javax.xml.validation.Schema;
import org.xml.sax.*;

class BFOSAXParser extends SAXParser {
    final BFOXMLReader reader;
    BFOSAXParser(BFOSAXParserFactory factory) {
        this.reader = new BFOXMLReader(factory);
    }
    @Override public @SuppressWarnings("deprecation") Parser getParser() {
        throw new UnsupportedOperationException("Parser interface not supported");
    }
    @Override public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        getXMLReader().setProperty(name, value);
    }
    @Override public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return getXMLReader().getProperty(name);
    }
    @Override public Schema getSchema() {
        return null;
    }
    @Override public XMLReader getXMLReader() {
        return reader;
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
}
