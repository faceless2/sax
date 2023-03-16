package com.bfo.sax;

import java.security.MessageDigest;
import java.net.URL;
import java.util.*;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.ext.*;

class Entity {

    static final Entity LT = createSimple("lt", "<");
    static final Entity GT = createSimple("gt", ">");
    static final Entity AMP = createSimple("amp", "&");
    static final Entity APOS = createSimple("apos", "'");
    static final Entity QUOT = createSimple("quot", "\"");

    private final BFOSAXParserFactory factory;
    private final String name, value, systemId, publicId;
    private final int line, column;

    /**
     * Create a "character" entity, ie &#xa;
     * @param codepoint the codepoint
     */
    static Entity createCharacter(int codepoint) {
        return new Entity(null, null, Character.toString(codepoint), null, null, -1, -1);
    }

    /**
     * Create an invalid entity reference, which has a name but can't be resolved
     * @param name the name, which is required and not empty
     */
    static Entity createInvalid(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }
        return new Entity(null, name, null, null, null, -1, -1);
    }

    /**
     * Create an "simple" entity reference, which will be evaluated as text, eg "&amp";
     * @param name the name, which is required and not empty
     * @param value the value, which is required and not empty
     */
    static Entity createSimple(String name, String value) {
        if (name == null || name.length() == 0 || value == null) {
            throw new IllegalArgumentException();
        }
        return new Entity(null, name, value, null, null, -1, -1);
    }

    /**
     * Create an entity reference to a DTD
     * @param factory the factory
     * @param name the name of the DTD
     * @param publicId the publicID of this DTD
     * @param systemId the systemID of this DTD
     */
    static Entity createDTD(BFOSAXParserFactory factory, String name, String publicId, String systemId) {
        return new Entity(factory, name, null, publicId, systemId, -999, -999);
    }

    /**
     * Create an external entity reference, for a general or parameter entity
     * @param factory the factory
     * @param name the name of the entity, eg "nbsp" or "%param" - required and not empty
     * @param publicId the publicID of this Entity
     * @param systemId the systemID of this Entity
     */
    static Entity createExternal(BFOSAXParserFactory factory, String name, String publicId, String systemId) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }
        return new Entity(factory, name, null, publicId, systemId, -1, -1);
    }

    /**
     * Create an internal entity reference, for a general or parameter entity
     * @param factory the factory
     * @param name the name of the entity, eg "nbsp" or "%param" - required and not empty
     * @param value the value of the entity - required
     * @param publicId the publicID of the document where this Entity was defined
     * @param publicId the systemID of the document where this Entity was defined
     * @param line the line-number in the document where this Entity was defined
     * @param column the column-number in the document where this Entity was defined
     */
    static Entity createInternal(BFOSAXParserFactory factory, String name, String value, String publicId, String systemId, int line, int column) {
        if (name == null || name.length() == 0 || value == null) {
            throw new IllegalArgumentException();
        }
        return new Entity(factory, name, value, publicId, systemId, line, column);
    }

    private Entity(BFOSAXParserFactory factory, String name, String value, String publicId, String systemId, int line, int column) {
        this.factory = factory;
        this.name = name;
        this.value = value;
        this.publicId = publicId;
        this.systemId = systemId;
        this.line = line;
        this.column = column;
    }

    public String toString() {
        if (isInvalid()) {
            return "{entity-invalid: name=" + BFOXMLReader.fmt(name) + "}";
        } else if (isCharacter()) {
            return "{entity-char: value=" + BFOXMLReader.fmt(value) + "}";
        } else if (isSimple()) {
            return "{entity-simple: name=" + BFOXMLReader.fmt(name) + " value=" + BFOXMLReader.fmt(value) + "}";
        } else if (isDTD()) {
            return "{entity-external-dtd: name=" + BFOXMLReader.fmt(value) + " publicid=" + BFOXMLReader.fmt(publicId) + " system=" + BFOXMLReader.fmt(systemId) + "}";
        } else if (isExternal()) {
            return "{entity-external: name=" + BFOXMLReader.fmt(name) + " publicid=" + BFOXMLReader.fmt(publicId) + " system=" + BFOXMLReader.fmt(systemId) + "}";
        } else {
            return "{entity-internal: name=" + BFOXMLReader.fmt(name) + " value=" + BFOXMLReader.fmt(value) + "}";
        }
    }

    /**
     * Return true if the entity is invalid - it has a name only, which was the original reference
     */
    boolean isInvalid() {
        return factory == null && name != null && value == null;
    }

    /**
     * Return true if the entity is a simple Character entity, eg &#10;
     */
    boolean isCharacter() {
        return name == null;
    }

    /**
     * Return true if the value should be parsed as a simple string (eg &amp), not as markup
     */
    boolean isSimple() {
        return factory == null && name != null && value != null;
    }

    /**
     * Return true if the value is an external entity
     */
    boolean isExternal() {
        return factory != null && name != null && value == null;
    }

    /**
     * Return true if the value is an internal entity
     */
    boolean isInternal() {
        return factory != null && name != null && value != null;
    }

    /**
     * Return true if this is a DTD entity
     */
    boolean isDTD() {
        return line == -999;
    }

    /**
     * Return true if this is a general entity
     */
    boolean isGeneral() {
        return !isDTD() && factory != null && name != null && name.charAt(0) != '%';
    }

    /**
     * Return true if this is a parameter entity
     */
    boolean isParameter() {
        return !isDTD() && factory != null && name != null && name.charAt(0) == '%';
    }

    //-------------------------------------------------------------------------------------

    /**
     * For character, internal or simple entities, return the value, otherwise return null
     */
    String getValue() {
        return value;
    }

    /**
     * For simple, invalid, dtd, internal or external entities, return the name, otherwise return null
     */
    String getName() {
        return name;
    }

    /**
     * For dtd or external entities, return the public id, otherwise return null
     */
    String getPublicId() {
        return publicId;
    }

    /**
     * For dtd or external entities, return the system id, otherwise return null
     */
    String getSystemId() {
        return systemId;
    }

    CPReader getReader(EntityResolver resolver, BFOXMLReader xml, CPReader reader) throws IOException, SAXException {
        String parentSystemId = reader.getSystemId();
        boolean xml11 = reader.isXML11();
        boolean er2 = resolver instanceof Queue ? ((Queue)resolver).isEntityResolver2() : resolver instanceof EntityResolver2;
        if (value != null) {
            return CPReader.getReader(value, publicId, systemId, line, column, xml11);
        } else {
            if (isDTD()) {
                // DTD, ok
            } else if (isParameter() && !xml.getFeature("http://xml.org/sax/features/external-parameter-entities")) {
                xml.error(reader, "External parameter entity " + this + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            } else if (isGeneral() && !xml.getFeature("http://xml.org/sax/features/external-general-entities")) {
                xml.error(reader, "External general entity " + this + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            }
            InputSource source = null;
            if (getPublicId() == null && getSystemId() == null) {
                if (isDTD() && er2) {
                    source = ((EntityResolver2)resolver).getExternalSubset(getName(), parentSystemId);
                }
            } else {
                String name = getName();
                if (factory.xercescompat) {
                    name = null;
                }
                String systemId = getSystemId();
                String resolvedSystemId = factory.resolve(parentSystemId, systemId);
                if (er2) {
                    if (systemId == null) {
                        systemId = "";
                    }
                    source = ((EntityResolver2)resolver).resolveEntity(name, getPublicId(), parentSystemId, systemId);
                } else if (resolver != null) {
                    if (resolvedSystemId == null) {
                        resolvedSystemId = "";
                    }
                    source = resolver.resolveEntity(getPublicId(), resolvedSystemId);
                }
                if (source == null) {
                    source = factory.resolveEntity(getPublicId(), resolvedSystemId);
                }
            }
            if (source != null) {
                return CPReader.normalize(CPReader.getReader(source), xml11);
            }
            return null;
        }
    }

}
