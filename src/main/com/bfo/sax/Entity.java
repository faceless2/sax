package com.bfo.sax;

import java.security.MessageDigest;
import java.net.URL;
import java.util.*;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.ext.*;

class Entity {

    static final Entity LT = new Entity(null, null, "lt", "<", null, null, 0, 0);
    static final Entity GT = new Entity(null, null, "gt", ">", null, null, 0, 0);
    static final Entity AMP = new Entity(null, null, "amp", "&", null, null, 0, 0);
    static final Entity APOS = new Entity(null, null, "apos", "'", null, null, 0, 0);
    static final Entity QUOT = new Entity(null, null, "quot", "\"", null, null, 0, 0);

    private final BFOSAXParserFactory factory;
    private final DTD dtd;
    private final int line, column;
    private final String systemId, publicId, name, value;

    Entity(BFOSAXParserFactory factory, DTD dtd, String name, String value, String publicId, String systemId, int line, int column) {
        this.factory = factory;
        this.dtd = dtd;
        this.name = name;
        this.value = value;
        this.publicId = publicId;
        this.systemId = systemId;
        this.line = line;
        this.column = column;
    }

    /**
     * @param name null for a char reference, not null for an invalid reference
     * @param value null for an invalid reference, not null for a char reference
     */
    Entity(String name, String value) {
        this(null, null, name, value, null, null, -1, -1);
    }

    public String toString() {
        if (isInvalid()) {
            return "{entity-invalid: name=\"" + name + "\"}";
        } else if (isCharacter()) {
            return "{entity-char: value=" + BFOXMLReader.fmt(value) + "}";
        } else if (isExternal()) {
            return "{entity-external: name=\"" + name + "\" publicid=" + BFOXMLReader.fmt(publicId) + " system=" + BFOXMLReader.fmt(systemId) + "}";
        } else {
            return "{entity-internal: name=\"" + name + "\" value=" + BFOXMLReader.fmt(value) + "}";
        }
    }

    /**
     * Return true if the entity is invalid - it has a name only, which was the original reference
     */
    boolean isInvalid() {
        return factory == null && value == null && name != null;
    }

    /**
     * Return true if the entity is a simple Character entity, eg &#10;
     */
    boolean isCharacter() {
        return name == null;
    }

    /**
     * Return true if the value is an external entity
     */
    boolean isExternal() {
        return value == null;
    }

    /**
     * Return true if the value should be parsed as markup, false if it's a simple string (eg &amp;)
     */
    boolean isMarkup() {
        return dtd != null;
    }

    String getValue() {
        return value;
    }

    String getName() {
        return name;
    }

    String getPublicId() {
        return publicId;
    }

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
            if (dtd == null) {
                // DTD, ok
            } else if (getName().startsWith("%") && !xml.getFeature("http://xml.org/sax/features/external-parameter-entities")) {
                xml.error(reader, "External parameter entity " + this + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            } else if (!getName().startsWith("%") && !xml.getFeature("http://xml.org/sax/features/external-general-entities")) {
                xml.error(reader, "External general entity " + this + " disallowed with \"http://xml.org/sax/features/external-parameter-entities\" feature");
            }
            InputSource source = null;
            if (dtd == null && getPublicId() == null && getSystemId() == null && er2) {
                source = ((EntityResolver2)resolver).getExternalSubset(getName(), parentSystemId);
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
