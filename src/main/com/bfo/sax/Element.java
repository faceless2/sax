package com.bfo.sax;

import java.util.*;

class Element {

    private final DTD dtd;
    private final String name;
    private final Map<String,Attribute> atts = new HashMap<String,Attribute>();
    private String model;
    private boolean hasText;
    private Map<String,Attribute> defaultatts;
    private String idattr;

    Element(DTD dtd, String name, String model) {
        this.dtd = dtd;
        this.name = name;
        this.hasText = true;
        setModel(model);
    }

    void setModel(String model) {
        if (this.model != null) {
            throw new IllegalArgumentException("Element \"" + getName() + "\" already declared");
        } else if (model != null) {
            this.model = model;
            this.hasText = model == null || model.startsWith("(#PCDATA") || "EMPTY".equals(model) || "ANY".equals(model);
        }
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return "{name="+name+" model="+model+" hasText="+hasText+" atts="+atts.values()+"}";
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
        Attribute a = new Attribute(name, type, mode, value);
        if (!atts.containsKey(name)) {
            atts.put(name, a);
            if (value != null) {
                if (defaultatts == null) {
                    defaultatts = new HashMap<String,Attribute>();
                }
                defaultatts.put(name, a);
            }
        }
    }

    Map<String,Attribute> getAttributes() {
        return atts;
    }

    Map<String,Attribute> getAttributesWithDefaults() {
        return defaultatts;
    }

}
