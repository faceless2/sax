package com.bfo.sax;

import java.util.*;
import java.security.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.atomic.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.ext.*;
import org.xml.sax.helpers.*;

public class Test {

    private static SAXParserFactory newfactory, oldfactory;

    private boolean newparser, oldparser, decl, lexical, speed, quiet, large, number;

    private void run(String file) {
        if (!quiet) {
            System.out.println(file);
        }
        // newparser!=null && oldparser!=null && large
        //   set digest, run in two threads, compare results at end
        // newparser!=null && oldparser!=null
        //   set log, run in two threads, whenever log is notified compare the most recent records and halt on difference
        // large
        //   set digest, run, print result
        // normal
        //   set nether, run
        if (newparser && oldparser) {
            MessageDigest digestold = null;
            MessageDigest digestnew = null;
            MyHandler handlerold, handlernew;
            Callback callback;
            if (large) {
                try {
                    digestold = MessageDigest.getInstance("SHA-256");
                    digestnew = MessageDigest.getInstance("SHA-256");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                final MessageDigest fdigestold = digestold, fdigestnew = digestnew;
                callback = new Callback() {
                    public void msg(String msg, MyHandler handler) {
                        boolean old = handler.toString().equals("old");
                        MessageDigest digest = old ? fdigestold : fdigestnew;
                        try {
                            if (msg.startsWith("fatalError: org.xml.sax.SAXParseException;")) {
                                msg = "fatalError: org.xml.sax.SAXParseException;";
                            }
                            digest.update(msg.getBytes("UTF-8"));
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            } else {
                callback = new Callback() {
                    SAXException e = null;
                    Deque<String> oldlist = new ArrayDeque<String>();
                    Deque<String> newlist = new ArrayDeque<String>();
                    int removed = 0;
                    public synchronized void msg(String msg, MyHandler handler) throws SAXException {
                        boolean old = handler.toString().equals("old");
                        if (e != null) {
                            throw e;
                        }
                        if (msg.startsWith("fatalError: org.xml.sax.SAXParseException;")) {
                            msg = "fatalError: org.xml.sax.SAXParseException;";
                        }
                        if (old) {
                            oldlist.addLast(msg);
                        } else {
                            newlist.addLast(msg);
                        }
                        while (oldlist.size() > 0 && newlist.size() > 0) {
                            String oldmsg = oldlist.removeFirst();
                            String newmsg = newlist.removeFirst();
                            if (oldmsg.equals(newmsg)) {
                                removed++;
                            } else {
                                synchronized(this) {
                                    System.out.println("old=" + oldmsg);
                                    System.out.println("new=" + newmsg);
                                    e = new SAXException("MISMATCH at line " + removed);
    //                                e.printStackTrace(System.out);
                                }
                                throw e;
                            }
                        }
                    }
                };
            }
            handlerold = new MyHandler(callback) {
                public String toString() {
                    return "old";
                }
            };
            handlernew = new MyHandler(callback) {
                public String toString() {
                    return "new";
                }
            };
            AtomicInteger running = new AtomicInteger(2);
            for (int i=0;i<2;i++) {
                final boolean old = i == 0;
                new Thread() {
                    public void run() {
                        try {
                            InputStream in = new BufferedInputStream(new FileInputStream(file));
                            XMLReader r = (old?oldfactory:newfactory).newSAXParser().getXMLReader();
                            parse(file, r, (old?handlerold:handlernew));
                        } catch (SAXException e) {
                            // This will have been logged!
                        } catch (Exception e) {
                            synchronized(this) {
                                e.printStackTrace(System.out);
                            }
                        } finally {
                            synchronized(running) {
                                running.decrementAndGet();
                                running.notifyAll();
                            }
                        }
                    }
                }.start();
            }
            while (running.get() > 0) {
                synchronized(running) {
                    try { running.wait(100); } catch (InterruptedException e) {}
                }
            }
            if (large) {
                String vold = fmt(digestold.digest());
                String vnew = fmt(digestnew.digest());
                if (!vold.equals(vnew)) {
                    System.out.println(file + ": mismatch");
                } else if (!quiet) {
                    System.out.println(file + ": match");
                }
            }
        } else {
            long start = System.currentTimeMillis();
            try {
                XMLReader reader = (newparser?newfactory:oldfactory).newSAXParser().getXMLReader();
                Callback callback;
                final MessageDigest digest = large ? MessageDigest.getInstance("SHA-256") : null;
                if (large) {
                    callback = new Callback() {
                        public void msg(String msg, MyHandler handler) {
                            try {
                                digest.update(msg.getBytes("UTF-8"));
                            } catch (RuntimeException e) {
                                throw e;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                } else {
                    callback = new Callback() {
                        int count = 0;
                        public void msg(String msg, MyHandler handler) {
                            if (!quiet) {
                                if (number) {
                                    System.out.print(String.format("%08d: ", count++));
                                }
                                System.out.println(msg);
                            }
                        }
                    };
                }
                parse(file, reader, new MyHandler(callback));
                if (speed && digest != null) {
                    System.out.println("# success " + fmt(digest.digest()) + " in " + (System.currentTimeMillis() - start) + "ms");
                } else if (!quiet && digest != null) {
                    System.out.println("# success " + fmt(digest.digest()));
                } else if (speed) {
                    System.out.println("# success in " + (System.currentTimeMillis() - start) + "ms");
                } else if (!quiet) {
                    System.out.println("# success");
                }
            } catch (Exception e) {
                if (speed) {
                    System.out.println("# failed in " + (System.currentTimeMillis() - start) + "ms");
                }
                e.printStackTrace(System.out);
            }
        }
    }

    private void parse(String file, XMLReader r, DefaultHandler2 handler) throws SAXException, IOException {
        r.setContentHandler(handler);
        if (decl) {
            r.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        }
        if (lexical) {
            r.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        }
        r.setErrorHandler(handler);
        r.setDTDHandler(handler);
        r.setEntityResolver(handler);
        InputSource in = new InputSource(new BufferedInputStream(new FileInputStream(file)));
        in.setSystemId(file);
        r.parse(in);
    }

    private static interface Callback {
        public void msg(String msg, MyHandler handler) throws SAXException;
    }

    private static class MyHandler extends DefaultHandler2 {
        private StringBuilder chars = new StringBuilder(), ignorable = new StringBuilder();
        private final Callback callback;
        MyHandler(Callback callback) {
            this.callback = callback;
        }
        private void flush() throws SAXException {
            String c = chars.toString();
            String i = ignorable.toString();
            chars.setLength(0);
            ignorable.setLength(0);
            if (c.length() > 0) {
                msg("characters(" + fmt(c) + ")");
            }
            if (i.length() > 0) {
                msg("ignorableWhitespace(" + fmt(i) + ")");
            }
        }
        private void msg(String s) throws SAXException {
            if (chars.length() > 0 || ignorable.length() > 0) {
                flush();
            }
            callback.msg(s, this);
        }
        public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
            msg("attributeDecl(" + fmt(eName)+", "+fmt(aName)+", "+fmt(type)+", "+fmt(mode)+", "+fmt(value) + ")");
        }
        public void comment(char[] ch, int start, int length) throws SAXException {
            msg("comment(" + fmt(new String(ch, start, length)) + ")");
        }
        public void elementDecl(String name, String model) throws SAXException {
            msg("elementDecl(" + fmt(name)+", "+fmt(model)+")");
        }
        public void endCDATA() throws SAXException {
            msg("endCDATA");
        }
        public void endDTD() throws SAXException {
            msg("endDTD");
        }
        public void endEntity(String name) throws SAXException {
            msg("endEntity(" + fmt(name) + ")");
        }
        public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
            msg("externalEntityDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(systemId)+")");
        }
        public InputSource getExternalSubset(String name, String baseURI) throws SAXException {
            msg("getExternalSubset(" + fmt(name)+", "+fmt(baseURI)+")");
            return null;
        }
        public void internalEntityDecl(String name, String value) throws SAXException {
            msg("internalEntityDecl(" + fmt(name)+", "+fmt(value)+")");
        }
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            msg("resolveEntity(" + fmt(publicId)+", "+fmt(systemId)+")");
            return null;
        }
        public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException {
            msg("resolveEntity(" + fmt(name)+", "+fmt(publicId)+", "+fmt(baseURI)+", "+fmt(systemId)+")");
            return null;
        }
        public void startCDATA() throws SAXException {
            msg("startCDATA");
        }
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            msg("startDTD(" + fmt(name)+", "+fmt(publicId)+", "+fmt(systemId)+")");
        }
        public void startEntity(String name) throws SAXException {
            msg("startEntity(" + fmt(name)+")");
        }
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (ignorable.length() > 0) {
                flush();
            }
            chars.append(ch, start, length);
        }
        public void endDocument() throws SAXException {
            msg("endDocument");
        }
        public void endElement(String uri, String localName, String qName) throws SAXException {
            msg("endElement(" + fmt(uri)+", "+fmt(localName)+", "+fmt(qName)+")");
        }
        public void endPrefixMapping(String prefix) throws SAXException {
            msg("endPrefixMapping(" + fmt(prefix)+")");
        }
        public void error(SAXParseException e) throws SAXException {
            msg("error: " + e);
        }
        public void fatalError(SAXParseException e) throws SAXException {
            msg("fatalError: " + e);
        }
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            if (chars.length() > 0) {
                flush();
            }
            ignorable.append(ch, start, length);
        }
        public void notationDecl(String name, String publicId, String systemId) throws SAXException {
            msg("notationDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(systemId)+")");
        }
        public void processingInstruction(String target, String data) throws SAXException {
            msg("processingInstruction(" + fmt(target)+", "+fmt(data)+")");
        }
        public void setDocumentLocator(Locator locator) {
            try {
                msg("setDocumentLocator(" + fmt(locator) + ")");
            } catch (SAXException e) {
                throw new RuntimeException(e);
            }
        }
        public void skippedEntity(String name) throws SAXException {
            msg("skippedEntity(" + fmt(name) + ")");
        }
        public void startDocument() throws SAXException {
            msg("startDocument()");
        }
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            msg("startElement(" + fmt(uri)+", "+fmt(localName)+", "+fmt(qName)+", "+fmt(attributes)+")");
        }
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            msg("startPrefixMapping(" + fmt(prefix)+", "+fmt(uri) + ")");
        }
        public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
            msg("unparsedEntityDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(systemId)+", "+fmt(notationName)+")");
        }
        public void warning(SAXParseException e) throws SAXException {
            msg("warning: " + e);
        }
    };

    private static String fmt(String s) {
        return BFOXMLReader.fmt(s);
    }

    private static String fmt(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        sb.append('[');
        for (int i=0;i<b.length;i++) {
            int v = b[i] & 0xFF;
            if (v < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String fmt(Attributes a) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i=0;i<a.getLength();i++) {
            if (i > 0) {
               sb.append(", ");
            }
            String uri = a.getURI(i);
            if (uri.equals("")) {
                sb.append(fmt(a.getQName(i)) + "=" + fmt(a.getValue(i)));
            } else {
                String u = fmt(a.getURI(i));
                String q = fmt(a.getQName(i));
                q = q.substring(1, q.length() - 1);
                u = u.substring(1, u.length() - 1);
                sb.append("\"{" + uri + "}" + q + "\"=" + fmt(a.getValue(i)));
            }
        }
        sb.append("}");
        return sb.toString();
    }

    static String fmt(Locator locator) {
        if (locator == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (locator.getPublicId() != null) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append("public=" + fmt(locator.getPublicId()));
        }
        if (locator.getSystemId() != null) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append("system=" + fmt(locator.getSystemId()));
        }
        if (locator.getLineNumber() > 0) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append("line=" + locator.getLineNumber());
        }
        if (locator.getColumnNumber() > 0) {
            if (sb.length() > 1) {
                sb.append(",");
            }
            sb.append("column=" + locator.getColumnNumber());
        }
        sb.append("}");
        return sb.toString();
    }

    //-----------------------------------------------

    public static void main(String[] args) throws Exception {
        newfactory = new BFOSAXParserFactory();
        oldfactory = javax.xml.parsers.SAXParserFactory.newInstance();
        newfactory.setNamespaceAware(true);
        oldfactory.setNamespaceAware(true);

        if (args.length == 0) {
            File f = new File("samples/xmlconf/xmlconf.xml");
            List<Map<String,String>> tests = new ArrayList<Map<String,String>>();
            oldfactory.newSAXParser().parse(f, new DefaultHandler() {
                List<String> baselist = new ArrayList<String>();
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    String base = attributes.getValue("xml:base");
                    baselist.add(base);
                    if (qName.equals("TEST")) {
                        for (int i=baselist.size()-1;i>=0;i--) {
                            base = baselist.get(i);
                            if (base != null) {
                                break;
                            }
                        }
                        Map<String,String> m = new LinkedHashMap<String,String>();
                        m.put("sections", attributes.getValue("SECTIONS"));
                        m.put("uri", base + "/" + attributes.getValue("URI"));
                        m.put("id", attributes.getValue("ID"));
                        m.put("type", attributes.getValue("TYPE"));
                        m.put("entities", attributes.getValue("ENTITIES"));
                        m.put("rec", attributes.getValue("RECOMMENDATION"));
                        m.put("ns", attributes.getValue("NAMESPACE"));
                        tests.add(m);
                    }
                }
            });
            Collections.sort(tests, new Comparator<Map<String,String>>() {
                public int compare(Map<String,String> a, Map<String,String> b) {
                    String va = a.get("type");
                    String vb = b.get("type");
                    if (va.equals("valid") && !vb.equals("valid")) {
                        return -1;
                    } else if (!va.equals("valid") && vb.equals("valid")) {
                        return 1;
                    } else {
                        va = a.get("id");
                        vb = b.get("id");
                        return va.compareTo(vb);
                    }
                }
            });
            List<String> l = new ArrayList<String>();
            l.add("--both");
            for (Map<String,String> test : tests) {
                String uri = "samples/xmlconf/" + test.get("uri");
                l.add(uri.replaceAll("//*", "/"));
            }
            args = l.toArray(new String[0]);
        }

        boolean newparser = true, oldparser = false, decl = false, lexical = false, speed = false, quiet = false, number = false, large = false;

        for (int i=0;i<args.length;i++) {
            if (args[i].equals("--old")) {
                newparser = false;
                oldparser = true;
            } else if (args[i].equals("--new")) {
                newparser = true;
                oldparser = false;
            } else if (args[i].equals("--both")) {
                newparser = true;
                oldparser = true;
            } else if (args[i].equals("--decl")) {
                decl = true;
            } else if (args[i].equals("--lexical")) {
                lexical = true;
            } else if (args[i].equals("--speed")) {
                speed = true;
            } else if (args[i].equals("--digest")) {
                large = true;
            } else if (args[i].equals("--number")) {
                number = true;
            } else if (args[i].equals("--quiet")) {
                quiet = true;
            } else {
                Test test = new Test();
                test.newparser = newparser;
                test.oldparser = oldparser;
                test.decl = decl;
                test.lexical = lexical;
                test.speed = speed;
                test.quiet = quiet;
                test.speed = speed;
                test.number = number;
                test.large = large;
                test.run(args[i]);
            }
        }
    }

}
