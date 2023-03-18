package com.bfo.sax;

import java.util.*;

class Cache {

    private final BFOSAXParserFactory factory;
    private final Map<String,DTD> dtdcache = new LinkedHashMap<String,DTD>(50, 0.7f, true) {
        protected boolean removeEldestEntry(Map.Entry<String,DTD> eldest) {
            return size() > factory.dtdCacheSize;
        }
    };
    private final Map<String,String> entitycache = new LinkedHashMap<String,String>(50, 0.7f, true) {
        protected boolean removeEldestEntry(Map.Entry<String,String> eldest) {
            return size() > factory.entityCacheSize;
        }
    };

    Cache(BFOSAXParserFactory factory) {
        this.factory = factory;
    }

    DTD getDTD(String urn, boolean xml11) {
        synchronized(dtdcache) {
            urn = urn + (xml11 ? ";xml11" : ";xml10");
            return dtdcache.get(urn);
        }
    }

    void putDTD(String urn, boolean xml11, DTD dtd) {
        if (dtd == null || !dtd.isClosed()) {
            throw new IllegalStateException("dtd not closed");
        }
        synchronized(dtdcache) {
            urn = urn + (xml11 ? ";xml11" : ";xml10");
            dtdcache.put(urn, dtd);
        }
    }

    String getEntity(String urn, boolean xml11) {
        urn = urn + (xml11 ? ";xml11" : ";xml10");
        synchronized(entitycache) {
            return entitycache.get(urn);
        }
    }

    void putEntity(String urn, boolean xml11, String entity) {
        urn = urn + (xml11 ? ";xml11" : ";xml10");
        synchronized(entitycache) {
            entitycache.put(urn, entity);
        }
    }

}
