package com.bfo.sax;

import org.xml.sax.*;
import org.xml.sax.ext.*;
import java.io.IOException;

class DirectQueue extends Queue {
    private Locator locator;

    DirectQueue(ContentHandler contentHandler, DeclHandler declHandler, DTDHandler dtdHandler, EntityResolver entityResolver, ErrorHandler errorHandler, LexicalHandler lexicalHandler) {
        super(contentHandler, declHandler, dtdHandler, entityResolver, errorHandler, lexicalHandler);
    }

    @Override boolean isBufSafe() {
        return true;
    }
    @Override public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        contentHandler.setDocumentLocator(locator);
    }
    @Override public String getPublicId() {
        return locator.getPublicId();
    }
    @Override public String getSystemId() {
        return locator.getSystemId();
    }
    @Override public int getLineNumber() {
        return locator.getLineNumber();
    }
    @Override public int getColumnNumber() {
        return locator.getColumnNumber();
    }
    @Override public void attributeDecl(String a1, String a2, String a3, String a4, String a5) throws SAXException {
        declHandler.attributeDecl(a1, a2, a3, a4, a5);
    }
    @Override public void comment(char[] a1, int a2, int a3) throws SAXException {
        lexicalHandler.comment(a1, a2, a3);
    }
    @Override public void elementDecl(String a1, String a2) throws SAXException {
        declHandler.elementDecl(a1, a2);
    }
    @Override public void endCDATA() throws SAXException {
        lexicalHandler.endCDATA();
    }
    @Override public void endDTD() throws SAXException {
        lexicalHandler.endDTD();
    }
    @Override public void endPrefixMapping(String a1) throws SAXException {
        contentHandler.endPrefixMapping(a1);
    }
    @Override public void endEntity(String a1) throws SAXException {
        lexicalHandler.endEntity(a1);
    }
    @Override public void externalEntityDecl(String a1, String a2, String a3) throws SAXException {
        declHandler.externalEntityDecl(a1, a2, a3);
    }
    @Override public void internalEntityDecl(String a1, String a2) throws SAXException {
        declHandler.internalEntityDecl(a1, a2);
    }
    @Override public void notationDecl(String a1, String a2, String a3) throws SAXException {
        dtdHandler.notationDecl(a1, a2, a3);
    }
    @Override public void startCDATA() throws SAXException {
        lexicalHandler.startCDATA();
    }
    @Override public void startDTD(String a1, String a2, String a3) throws SAXException {
        lexicalHandler.startDTD(a1, a2, a3);
    }
    @Override public void startEntity(String a1) throws SAXException {
        lexicalHandler.startEntity(a1);
    }
    @Override public void characters(char[] a1, int a2, int a3) throws SAXException {
        contentHandler.characters(a1, a2, a3);
    }
    @Override public void startDocument() throws SAXException {
        contentHandler.startDocument();
    }
    @Override public void endDocument() throws SAXException {
        contentHandler.endDocument();
    }
    @Override public void endElement(String a1, String a2, String a3) throws SAXException {
        contentHandler.endElement(a1, a2, a3);
    }
    @Override public void ignorableWhitespace(char[] a1, int a2, int a3) throws SAXException {
        contentHandler.ignorableWhitespace(a1, a2, a3);
    }
    @Override public void processingInstruction(String a1, String a2) throws SAXException {
        contentHandler.processingInstruction(a1, a2);
    }
    @Override public void skippedEntity(String a1) throws SAXException {
        contentHandler.skippedEntity(a1);
    }
    @Override public void startElement(String a1, String a2, String a3, Attributes atts) throws SAXException {
        contentHandler.startElement(a1, a2, a3, atts);
    }
    @Override public void startPrefixMapping(String a1, String a2) throws SAXException {
        contentHandler.startPrefixMapping(a1, a2);
    }
    @Override public void unparsedEntityDecl(String a1, String a2, String a3, String a4) throws SAXException {
        dtdHandler.unparsedEntityDecl(a1, a2, a3, a4);
    }
    @Override public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return entityResolver.resolveEntity(publicId, systemId);
    }
    @Override public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        return ((EntityResolver2)entityResolver).getExternalSubset(name, baseURI);
    }
    @Override public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        return ((EntityResolver2)entityResolver).resolveEntity(name, publicId, baseURI, systemId);
    }
    @Override public void error(SAXParseException exception) throws SAXException {
        errorHandler.error(exception);
    }
    @Override public void warning(SAXParseException exception) throws SAXException {
        errorHandler.warning(exception);
    }
    @Override public void fatalError(SAXParseException exception) throws SAXException {
        errorHandler.fatalError(exception);
    }
}
