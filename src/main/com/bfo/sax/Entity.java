package com.bfo.sax;

class Entity {

    private static final int TYPE_DTD = -999;
    private static final int TYPE_EXTERNAL = -998;
    private static final int TYPE_SIMPLE = -997;
    private static final int TYPE_CHARACTER = -996;
    private static final int TYPE_INVALID = -995;

    static final Entity LT = createSimple("lt", "<");
    static final Entity GT = createSimple("gt", ">");
    static final Entity AMP = createSimple("amp", "&");
    static final Entity APOS = createSimple("apos", "'");
    static final Entity QUOT = createSimple("quot", "\"");

    private final String name, value, systemId, publicId;
    private final int line, column;

    /**
     * Create a "character" entity, ie &#xa;
     * @param codepoint the codepoint
     */
    static Entity createCharacter(int codepoint) {
        return new Entity(null, Character.toString(codepoint), null, null, TYPE_CHARACTER, TYPE_CHARACTER);
    }

    /**
     * Create an invalid entity reference, which has a name but can't be resolved
     * @param name the name, which is required and not empty
     */
    static Entity createInvalid(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }
        return new Entity(name, null, null, null, TYPE_INVALID, TYPE_INVALID);
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
        return new Entity(name, value, null, null, TYPE_SIMPLE, TYPE_SIMPLE);
    }

    /**
     * Create an entity reference to a DTD
     * @param name the name of the DTD
     * @param publicId the publicID of this DTD
     * @param systemId the systemID of this DTD
     */
    static Entity createDTD(String name, String publicId, String systemId) {
        return new Entity(name, null, publicId, systemId, TYPE_DTD, TYPE_DTD);
    }

    /**
     * Create an external entity reference, for a general or parameter entity
     * @param name the name of the entity, eg "nbsp" or "%param" - required and not empty
     * @param publicId the publicID of this Entity
     * @param systemId the systemID of this Entity
     */
    static Entity createExternal(String name, String publicId, String systemId) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException();
        }
        return new Entity(name, null, publicId, systemId, TYPE_EXTERNAL, TYPE_EXTERNAL);
    }

    /**
     * Create an internal entity reference, for a general or parameter entity
     * @param name the name of the entity, eg "nbsp" or "%param" - required and not empty
     * @param value the value of the entity - required
     * @param publicId the publicID of the document where this Entity was defined
     * @param publicId the systemID of the document where this Entity was defined
     * @param line the line-number in the document where this Entity was defined
     * @param column the column-number in the document where this Entity was defined
     */
    static Entity createInternal(String name, String value, String publicId, String systemId, int line, int column) {
        if (name == null || name.length() == 0 || value == null) {
            throw new IllegalArgumentException();
        }
        if (line < 0) {
            line = -1;
        }
        if (column < 0) {
            column = -1;
        }
        return new Entity(name, value, publicId, systemId, line, column);
    }

    private Entity(String name, String value, String publicId, String systemId, int line, int column) {
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
        } else if (isInternal()) {
            return "{entity-internal: name=" + BFOXMLReader.fmt(name) + " value=" + BFOXMLReader.fmt(value) + "}";
        } else {
            throw new Error();
        }
    }

    /**
     * Return true if the entity is a simple Character entity, eg &#10;
     */
    boolean isCharacter() {
        return column == TYPE_CHARACTER;
    }

    /**
     * Return true if the entity is invalid - it has a name only, which was the original reference
     */
    boolean isInvalid() {
        return column == TYPE_INVALID;
    }

    /**
     * Return true if the value should be parsed as a simple string (eg &amp), not as markup
     */
    boolean isSimple() {
        return column == TYPE_SIMPLE;
    }

    /**
     * Return true if the value is an external entity
     */
    boolean isExternal() {
        return column == TYPE_EXTERNAL;
    }

    /**
     * Return true if the value is an internal entity
     */
    boolean isInternal() {
        return column >= -1;
    }

    /**
     * Return true if this is a DTD entity
     */
    boolean isDTD() {
        return column == TYPE_DTD;
    }

    /**
     * Return true if this is a general entity, which can internal or external
     */
    boolean isGeneral() {
        return (isExternal() || isInternal()) && name.charAt(0) != '%';
    }

    /**
     * Return true if this is a parameter entity
     */
    boolean isParameter() {
        return (isExternal() || isInternal()) && name.charAt(0) == '%';
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

    /**
     * For internal entities, return the line number it was created at
     */
    int getLineNumber() {
        return line;
    }

    /**
     * For internal entities, return the column number it was created at
     */
    int getColumnNumber() {
        return column;
    }

}
