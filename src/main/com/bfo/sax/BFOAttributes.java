package com.bfo.sax;

import java.util.*;
import org.xml.sax.*;

class BFOAttributes implements Attributes {
    private static final int NUMFIELDS = 6;
    private static final String SPECIFIED = "specified";
    List<String> l = new ArrayList<String>();       // uri, lname, qname, type, value
    void add(String uri, String localName, String qName, String type, String value, boolean specified) {
        l.add(uri);
        l.add(localName);
        l.add(qName);
        l.add(type);
        l.add(value);
        l.add(specified ? SPECIFIED : null);
    }
    @Override public int getIndex(String qName) {
        for (int i=0;i<l.size();i+=NUMFIELDS) {
            if (l.get(i + 2).equals(qName)) {
                return i / NUMFIELDS;
            }
        }
        return -1;
    }
    @Override public int getIndex(String uri, String localName) {
        for (int i=0;i<l.size();i+=NUMFIELDS) {
            if (l.get(i + 1).equals(localName) && l.get(i).equals(uri)) {
                return i / NUMFIELDS;
            }
        }
        return -1;
    }
    @Override public int getLength() {
        return l.size() / NUMFIELDS;
    }
    @Override public String getLocalName(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * NUMFIELDS + 1);
    }
    @Override public String getQName(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * NUMFIELDS + 2);
    }
    @Override public String getType(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * 4 + 3);
    }
    @Override public String getType(String qName) {
        int v = getIndex(qName);
        return v < 0 ? null : getType(v);
    }
    @Override public String getType(String uri, String localName) {
        int v = getIndex(uri, localName);
        return v < 0 ? null : getType(v);
    }
    @Override public String getURI(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * NUMFIELDS);
    }
    @Override public String getValue(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * NUMFIELDS + 4);
    }
    public boolean isSpecified(int index) {
        return index < 0 || index >= getLength() ? null : l.get(index * NUMFIELDS + 5) == SPECIFIED;
    }
    @Override public String getValue(String qName) {
        int v = getIndex(qName);
        return v < 0 ? null : getValue(v);
    }
    @Override public String getValue(String uri, String localName) {
        int v = getIndex(uri, localName);
        return v < 0 ? null : getValue(v);
    }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i=0;i<getLength();i++) {
            if (i > 0) {
               sb.append(", ");
            }
            String uri = getURI(i);
            if (uri.equals("")) {
                sb.append(getQName(i) + "=\"" + getValue(i) + "\"");
            } else {
                sb.append("{" + uri + "}" + getQName(i) + "=\"" + getValue(i) + "\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    static final Attributes EMPTYATTS = new Attributes() {
        @Override public int getIndex(String qName) {
            return -1;
        }
        @Override public int getIndex(String uri, String localName) {
            return -1;
        }
        @Override public int getLength() {
            return 0;
        }
        @Override public String getLocalName(int index) {
            return null;
        }
        @Override public String getQName(int index) {
            return null;
        }
        @Override public String getType(int index) {
            return null;
        }
        @Override public String getType(String qName) {
            return null;
        }
        @Override public String getType(String uri, String localName) {
            return null;
        }
        @Override public String getURI(int index) {
            return null;
        }
        @Override public String getValue(int index) {
            return null;
        }
        @Override public String getValue(String qName) {
            return null;
        }
        @Override public String getValue(String uri, String localName) {
            return null;
        }
        @Override public String toString() {
            return "{}";
        }
    };
}
