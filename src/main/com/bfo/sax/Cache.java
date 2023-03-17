package com.bfo.sax;

import java.util.*;

class Cache {

    private Map<String,DTD> dtdcache = new HashMap<String,DTD>();

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

}
