package com.bfo.sax;

import java.security.MessageDigest;
import java.net.URL;
import java.util.*;
import org.xml.sax.ext.*;

class DTD {

    private final BFOSAXParserFactory factory;
    private final String baseurl, publicid, systemid;
    private final Map<String,Entity> entities = new HashMap<String,Entity>();
    private final Map<String,Element> elements = new HashMap<String,Element>();

    DTD(BFOSAXParserFactory factory, String publicid, String baseurl, String systemid) {
        if (baseurl != null && systemid != null) {
            try {
                baseurl = new URL(new URL(baseurl), systemid).toString();
            } catch (Exception e) {}
        } else if (systemid != null) {
            baseurl = systemid;
        }
        this.factory = factory;
        this.publicid = publicid;
        this.baseurl = baseurl;         // for any resources referenced from here
        this.systemid = systemid;
    }

    public int hashCode() {
        int v = 0;
        if (publicid != null) {
            v ^= publicid.hashCode();
        }
        if (systemid != null) {
            v ^= systemid.hashCode();
        }
        return v;
    }

    public boolean equals(Object o) {
        if (o instanceof DTD) {
            DTD dtd = (DTD)o;
            return publicid == null ? dtd.publicid == null : publicid.equals(dtd.publicid) && 
                   systemid == null ? dtd.systemid == null : systemid.equals(dtd.systemid);
        }
        return false;
    }

    public String getPublicId() {
        return publicid;
    }

    public String getSystemId() {
        return systemid;
    }

    void attributeDecl(String eName, String aName, String type, String mode, String value) {
//        System.out.println("attributeDecl(" + fmt(eName)+", "+fmt(aName)+", "+fmt(type)+", "+fmt(mode)+", "+fmt(value) + ")");
        Element elt = getElement(eName);
        if (elt != null) {
            // Not an error if redefined
            elt.attributeDecl(aName, type, mode, value);
        }
    }

    void addElement(Element element) {
        if (elements.put(element.getName(), element) != null) {
            throw new IllegalArgumentException("Element \"" + element.getName() + "\" already declared");
        }
    }

    void addEntity(Entity entity) {
        if (entity.isInvalid() || entity.isCharacter()) {
            throw new IllegalArgumentException();
        }
        if (!entities.containsKey(entity.getName())) {
            entities.put(entity.getName(), entity);
        }
    }

    Entity getEntity(String name) {
        return entities.get(name);
    }

    Element getElement(String name) {
        return elements.get(name);
    }

}
