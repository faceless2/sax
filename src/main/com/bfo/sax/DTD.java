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
    private Map<Entity,String> dependencies;
    private Map<Entity,InputSourceURN> workingDependencies;

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
        this.workingDependencies = new HashMap<Entity,InputSourceURN>();
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
        if (isClosed()) {
            throw new IllegalStateException("closed");
        }
        if (elements.put(element.getName(), element) != null) {
            throw new IllegalArgumentException("Element \"" + element.getName() + "\" already declared");
        }
    }

    void addEntity(Entity entity) {
        if (isClosed()) {
            throw new IllegalStateException("closed");
        }
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

    boolean isClosed() {
        return workingDependencies == null;
    }

    Map<Entity,InputSourceURN> getWorkingDependencies() {
        return workingDependencies;
    }

    void close() {
        for (Element elt : elements.values()) {
            elt.close();
        }
        dependencies = new HashMap<Entity,String>();
        for (Map.Entry<Entity,InputSourceURN> e : workingDependencies.entrySet()) {
            if (e.getKey().isExternal()) {
                String urn = e.getValue().getURN();
                if (urn == null) {
                    throw new IllegalStateException("URN not calculated for " + e.getKey());
                }
                dependencies.put(e.getKey(), urn);
            }
        }
        workingDependencies = null;
        dependencies = Collections.<Entity,String>unmodifiableMap(dependencies);
    }

    Map<Entity,String> getDependencies() {
        return dependencies;
    }

}
