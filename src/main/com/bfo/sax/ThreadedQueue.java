package com.bfo.sax;

import org.xml.sax.*;
import org.xml.sax.ext.*;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.atomic.AtomicReference;

class ThreadedQueue extends Queue {
    private String publicId, systemId;
    private int line, column;
    private final Rec[] q;
    private final ReentrantLock lock;
    private final Condition notEmpty, maybeEmpty;
    private final AtomicReference<Object> reply;
    private int takeIndex, putIndex, count;
    private Locator locator;

    ThreadedQueue(ContentHandler contentHandler, DeclHandler declHandler, DTDHandler dtdHandler, EntityResolver entityResolver, ErrorHandler errorHandler, LexicalHandler lexicalHandler) {
        super(contentHandler, declHandler, dtdHandler, entityResolver, errorHandler, lexicalHandler);
        q = new Rec[256];
        for (int i=0;i<q.length;i++) {
            q[i] = new Rec();
        }
        lock = new ReentrantLock(false);
        notEmpty = lock.newCondition();
        maybeEmpty =  lock.newCondition();
        reply = new AtomicReference<Object>();
    }

    @Override public boolean isBufSafe() {
        return false;
    }
    @Override public String getPublicId() {
        return publicId;
    }
    @Override public String getSystemId() {
        return systemId;
    }
    @Override public int getLineNumber() {
        return line;
    }
    @Override public int getColumnNumber() {
        return column;
    }
    private void testFail() {
        if (reply.get() instanceof Exception ) {
            throw (IllegalStateException)new IllegalStateException("other thread failed").initCause((Exception)reply.get());
        }
    }
    @Override public void attributeDecl(String a1, String a2, String a3, String a4, String a5) throws SAXException {
        add(MsgType.attributeDecl, a1, a2, a3, a4, a5);
    }
    @Override public void comment(char[] a1, int a2, int a3) throws SAXException {
        add(MsgType.comment, a1, a2, a3);
    }
    @Override public void elementDecl(String a1, String a2) throws SAXException {
        add(MsgType.elementDecl, a1, a2);
    }
    @Override public void endCDATA() throws SAXException {
        add(MsgType.endCDATA);
    }
    @Override public void endDTD() throws SAXException {
        add(MsgType.endDTD);
    }
    @Override public void endEntity(String a1) throws SAXException {
        add(MsgType.endEntity, a1);
    }
    @Override public void endPrefixMapping(String a1) throws SAXException {
        add(MsgType.endPrefixMapping, a1);
    }
    @Override public void externalEntityDecl(String a1, String a2, String a3) throws SAXException {
        add(MsgType.externalEntityDecl, a1, a2, a3);
    }
    @Override public void internalEntityDecl(String a1, String a2) throws SAXException {
        add(MsgType.internalEntityDecl, a1, a2);
    }
    @Override public void startCDATA() throws SAXException {
        add(MsgType.startCDATA);
    }
    @Override public void startDocument() throws SAXException {
        add(MsgType.startDocument);
    }
    @Override public void startDTD(String a1, String a2, String a3) throws SAXException {
        add(MsgType.startDTD, a1, a2, a3);
    }
    @Override public void startEntity(String a1) throws SAXException {
        add(MsgType.startEntity, a1);
    }
    @Override public void characters(char[] a1, int a2, int a3) throws SAXException {
        add(MsgType.characters, a1, a2, a3);
    }
    @Override public void endDocument() throws SAXException {
        add(MsgType.endDocument);
    }
    @Override public void endElement(String a1, String a2, String a3) throws SAXException {
        add(MsgType.endElement, a1, a2, a3);
    }
    @Override public void ignorableWhitespace(char[] a1, int a2, int a3) throws SAXException {
        add(MsgType.ignorableWhitespace, a1, a2, a3);
    }
    @Override public void processingInstruction(String a1, String a2) throws SAXException {
        add(MsgType.processingInstruction, a1, a2);
    }
    @Override public void skippedEntity(String a1) throws SAXException {
        add(MsgType.skippedEntity, a1);
    }
    @Override public void startElement(String a1, String a2, String a3, Attributes atts) throws SAXException {
        add(MsgType.startElement, a1, a2, a3, atts);
    }
    @Override public void startPrefixMapping(String a1, String a2) throws SAXException {
        add(MsgType.startPrefixMapping, a1, a2);
    }
    @Override public void unparsedEntityDecl(String a1, String a2, String a3, String a4) throws SAXException {
        add(MsgType.unparsedEntityDecl, a1, a2, a3, a4);
    }
    @Override public void notationDecl(String a1, String a2, String a3) throws SAXException {
        add(MsgType.notationDecl, a1, a2, a3);
    }
    @Override public InputSource resolveEntity(String a1, String a2) throws SAXException, IOException {

        return (InputSource)now(MsgType.resolveEntity, a1, a2);
    }
    @Override public InputSource getExternalSubset(String a1, String a2) throws SAXException, IOException {
        return (InputSource)now(MsgType.getExternalSubset, a1, a2);
    }
    @Override public InputSource resolveEntity(String a1, String a2, String a3, String a4) throws SAXException, IOException {
        return (InputSource)now(MsgType.resolveEntity2, a1, a2, a3, a4);
    }
    @Override public void error(SAXParseException a1) throws SAXException {
        now(MsgType.error, a1);
    }
    @Override public void warning(SAXParseException a1) throws SAXException {
        now(MsgType.warning, a1);
    }
    @Override public void fatalError(SAXParseException a1) throws SAXException {
        now(MsgType.fatalError, a1);
        throw a1;       // Won't get this far, because fail will be set.
    }
    @Override public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        publicId = locator.getPublicId();
        systemId = locator.getSystemId();
        line = locator.getLineNumber();
        column = locator.getColumnNumber();
        add(MsgType.setDocumentLocator, this);
    }
    void close() {
        add(MsgType.close);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{count:"+count+", list:[");
        for (int i=0;i<count;i++) {
            Object o = q[(takeIndex + i) % q.length];
            if (i > 0) {
                sb.append(",");
            }
            if (o instanceof String) {
                sb.append(BFOXMLReader.fmt((String)o));
            } else if (o instanceof char[]) {
                sb.append("buf="+((char[])o).length);
            } else {
                sb.append(o);
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Like "add" but waits for the queue to clear,
     * add a message, then waits for and returns he response
     */
    private Object now(MsgType type, Object... o) {
        testFail();
        // Wait for queue to be empty
        try {
            lock.lockInterruptibly();
            while (count != 0) {
                maybeEmpty.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
        // queue is empty
        testFail();
        add(type, o);
        Object out;
        while ((out=reply.get()) == null) {
            synchronized(reply) {
                try {
                    reply.wait(100);
                } catch (InterruptedException e) { }
            }
        }
        synchronized(reply) {
            testFail();
            reply.set(null);    // Only set in this thread to clear the response
        }
        return out == reply ? null : out;       // reply=reply: magic value meaning null
    }

    /**
     * Add a message to the queue and return
     */
    private void add(MsgType type, Object... o) {
        testFail();
        try {
            lock.lockInterruptibly();
            if (o.length != type.c) {
                throw new IllegalStateException(type + " expected " + type.c  + " got " +o.length);
            }
            while (count == q.length) {
                maybeEmpty.await();
            }
            // Rec is reused; message exchange is zero allocation
            Rec r = q[putIndex];
            r.type = type;
            r.publicId = locator.getPublicId();
            r.systemId = locator.getSystemId();
            r.line = locator.getLineNumber();
            r.column = locator.getColumnNumber();
            System.arraycopy(o, 0, r.o, 0, o.length);
            if (++putIndex == q.length) {
                putIndex = 0;
            }
            count++;
            notEmpty.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Wait for a message on the queue, and return it
     * once it exists, leaving the queue state unchanged
     */
    private MsgType peek(Object[] out) {
        try {
            lock.lockInterruptibly();
            while (count == 0) {
                notEmpty.await();
            }
            Rec rec = q[takeIndex];
            MsgType type = rec.type;
            publicId = rec.publicId;
            systemId = rec.systemId;
            line = rec.line;
            column = rec.column;
            for (int i=type.c-1;i>=0;i--) {
                out[i] = rec.o[i];
            }
            return type;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove the message from the queue, waiting for it
     * if necessary.
     */
    private void remove() {
        try {
            lock.lockInterruptibly();
            while (count == 0) {
                notEmpty.await();
            }
            // Rec is reused; message exchange is zero allocation
            Rec rec = q[takeIndex];
            for (int i=rec.type.c-1;i>=0;i--) {
                rec.o[i] = null;
            }
            rec.type = null;
            if (++takeIndex == q.length) {
                takeIndex = 0;
            }
            count--;
            maybeEmpty.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void run() throws SAXException, IOException {
        Object[] o = new Object[8];
        boolean active = true;
        while (active) {
            MsgType type = peek(o);
            boolean exchange = false;   // Set if message expects a reply
            Object output = null;       // if exchange is true, the reply
            try {
                switch (type) {
                    case attributeDecl: 
                        declHandler.attributeDecl((String)o[0], (String)o[1], (String)o[2], (String)o[3], (String)o[4]);
                        break;
                    case comment:
                        lexicalHandler.comment((char[])o[0], (Integer)o[1], (Integer)o[2]);
                        break;
                    case elementDecl:
                        declHandler.elementDecl((String)o[0], (String)o[1]);
                        break;
                    case endCDATA:
                        lexicalHandler.endCDATA();
                        break;
                    case endDTD:
                        lexicalHandler.endDTD();
                        break;
                    case endEntity:
                        lexicalHandler.endEntity((String)o[0]);
                        break;
                    case externalEntityDecl:
                        declHandler.externalEntityDecl((String)o[0], (String)o[1], (String)o[2]);
                        break;
                    case internalEntityDecl:
                        declHandler.internalEntityDecl((String)o[0], (String)o[1]);
                        break;
                    case startCDATA:
                        lexicalHandler.startCDATA();
                        break;
                    case startDTD:
                        lexicalHandler.startDTD((String)o[0], (String)o[1], (String)o[2]);
                        break;
                    case startEntity:
                        lexicalHandler.startEntity((String)o[0]);
                        break;
                    case characters:
                        contentHandler.characters((char[])o[0], (Integer)o[1], (Integer)o[2]);
                        break;
                    case endDocument:
                        contentHandler.endDocument();
                        break;
                    case endElement:
                        contentHandler.endElement((String)o[0], (String)o[1], (String)o[2]);
                        break;
                    case ignorableWhitespace:
                        contentHandler.ignorableWhitespace((char[])o[0], (Integer)o[1], (Integer)o[2]);
                        break;
                    case notationDecl:
                        dtdHandler.notationDecl((String)o[0], (String)o[1], (String)o[2]);
                        break;
                    case processingInstruction:
                        contentHandler.processingInstruction((String)o[0], (String)o[1]);
                        break;
                    case skippedEntity:
                        contentHandler.skippedEntity((String)o[0]);
                        break;
                    case startElement:
                        contentHandler.startElement((String)o[0], (String)o[1], (String)o[2], (Attributes)o[3]);
                        break;
                    case startDocument:
                        contentHandler.startDocument();
                        break;
                    case setDocumentLocator:
                        contentHandler.setDocumentLocator((Locator)o[0]);
                        break;
                    case startPrefixMapping:
                        contentHandler.startPrefixMapping((String)o[0], (String)o[1]);
                        break;
                    case endPrefixMapping:
                        contentHandler.endPrefixMapping((String)o[0]);
                        break;
                    case unparsedEntityDecl:
                        dtdHandler.unparsedEntityDecl((String)o[0], (String)o[1], (String)o[2], (String)o[3]);
                        break;
                    case warning:
                        exchange = true;        // reply is null but this ensures other thread waits for any exception
                        errorHandler.warning((SAXParseException)o[0]);
                        break;
                    case error:
                        exchange = true;        // reply is null but this ensures other thread waits for any exception
                        errorHandler.error((SAXParseException)o[0]);
                        break;
                    case fatalError:
                        exchange = true;        // reply is null but this ensures other thread waits for any exception
                        errorHandler.fatalError((SAXParseException)o[0]);
                        throw (SAXParseException)o[0];  // fatalError must fail
                    case getExternalSubset:
                        exchange = true;
                        output = ((EntityResolver2)entityResolver).getExternalSubset((String)o[0], (String)o[1]);
                        break;
                    case resolveEntity:
                        exchange = true;
                        output = entityResolver.resolveEntity((String)o[0], (String)o[1]);
                        break;
                    case resolveEntity2:
                        exchange = true;
                        output = ((EntityResolver2)entityResolver).resolveEntity((String)o[0], (String)o[1], (String)o[2], (String)o[3]);
                        break;
                    case close:
                        active = false;         // heard after endDocument on clean exit
                        break;
                    default:
                        throw new IllegalStateException("Unhandled type " + type);
                }
                if (exchange) {
                    synchronized(reply) {
                        reply.set(output == null ? reply : output); // reply=reply: magic value meaning null
                        reply.notify();
                    }
                }
            } catch (Throwable e) {
                synchronized(reply) {
                    reply.set(e);
                    reply.notify();
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException)e;
                } else if (e instanceof SAXException) {
                    throw (SAXException)e;
                } else if (e instanceof IOException) {
                    throw (IOException)e;
                } else if (e instanceof Error) {
                    throw (Error)e;
                } else if (e != null) {
                    throw (SAXException)new SAXException().initCause(e);
                }
            } finally {
                remove();
            }
        }
        synchronized(reply) {   // Last ditch
            reply.notify();
        }
    }

    private static enum MsgType {
        attributeDecl(5), comment(3), elementDecl(2), endCDATA(0), endDTD(0), endEntity(1), endPrefixMapping(1),
        externalEntityDecl(3), internalEntityDecl(2), startCDATA(0), startDTD(3), startEntity(1),
        characters(3), endDocument(0), endElement(3), ignorableWhitespace(3), notationDecl(3),
        processingInstruction(2), skippedEntity(1), startDocument(0), startElement(4), startPrefixMapping(2), unparsedEntityDecl(4),
        error(1), warning(1), fatalError(1),
        resolveEntity(2), resolveEntity2(4), getExternalSubset(2),
        setDocumentLocator(1),
        close(0);
        final int c;
        MsgType(int c) {
            this.c = c;
        }
    }

    private static class Rec {
        MsgType type;
        String publicId, systemId;
        int line, column;
        final Object[] o = new Object[5];
    }
}

