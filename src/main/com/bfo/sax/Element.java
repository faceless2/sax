package com.bfo.sax;

import java.util.*;

class Element {

    private final DTD dtd;
    private final String name, model;
    private final boolean hasText;
    private final Map<String,Attribute> atts = new HashMap<String,Attribute>();
    private Map<String,Attribute> defaultatts;
    private String idattr;

    Element(DTD dtd, String name, String model) {
        this.dtd = dtd;
        this.name = name;
        this.model = model;
        this.hasText = model.startsWith("(#PCDATA") || "EMPTY".equals(model) || "ANY".equals(model);
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return "{name="+name+" model="+model+" hasText="+hasText+"}";
    }

    boolean hasText() {
        return hasText;
    }

    void attributeDecl(String name, String type, String mode, String value) {
        if ("ID".equals(type)) {
            if (idattr == null) {
                idattr = name;
            } else if (!idattr.equals(name)) {
                throw new IllegalArgumentException("ID attr already set to \"" + idattr + "\", can't change to \"" + name + "\"");
            }
            if (!"#IMPLIED".equals(mode) && !"#REQUIRED".equals(mode)) {
                throw new IllegalArgumentException("ID attr \"" + name + "\" cannot be " + mode);
            }
        }
        Attribute a = new Attribute(type, mode, value);
        atts.put(name, a);
        if (value != null) {
            if (defaultatts == null) {
                defaultatts = new HashMap<String,Attribute>();
            }
            defaultatts.put(name, a);
        }
    }

    Map<String,Attribute> getAttributes() {
        return atts;
    }

    Map<String,Attribute> getAttributesWithDefaults() {
        return defaultatts;
    }

}
