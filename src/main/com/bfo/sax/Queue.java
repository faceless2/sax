package com.bfo.sax;

import org.xml.sax.*;
import org.xml.sax.ext.*;
import javax.xml.stream.Location;
import java.io.IOException;

abstract class Queue implements ContentHandler, LexicalHandler, DTDHandler, ErrorHandler, EntityResolver2, DeclHandler, Locator2, Location {
    final ContentHandler contentHandler;
    final DTDHandler dtdHandler;
    final DeclHandler declHandler;
    final EntityResolver entityResolver;
    final ErrorHandler errorHandler;
    final LexicalHandler lexicalHandler;

    Queue(ContentHandler contentHandler, DeclHandler declHandler, DTDHandler dtdHandler, EntityResolver entityResolver, ErrorHandler errorHandler, LexicalHandler lexicalHandler) {
        this.contentHandler = contentHandler;
        this.dtdHandler = dtdHandler;
        this.declHandler = declHandler;
        this.entityResolver = entityResolver;
        this.errorHandler = errorHandler;
        this.lexicalHandler = lexicalHandler;
    }
    final boolean isContentHandler() {
       return contentHandler != null;
    }
    final boolean isDTDHandler() {
       return dtdHandler != null;
    }
    final boolean isDeclHandler() {
       return declHandler != null;
    }
    final boolean isErrorHandler() {
       return errorHandler != null;
    }
    final boolean isLexicalHandler() {
       return lexicalHandler != null;
    }
    final boolean isEntityResolver() {
       return entityResolver != null;
    }
    final boolean isEntityResolver2() {
       return entityResolver instanceof EntityResolver2;
    }
    abstract void fatalError2(Exception e) throws IOException, SAXException;

    abstract void xmlpi(String charset, String encoding, String standalone, String version) throws IOException, SAXException;

    /**
     * Is is safe to reuse buffers?
     */
    abstract boolean isBufSafe();
}
