package com.bfo.sax;

import java.util.*;
import java.security.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.atomic.*;
import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.namespace.*;
import org.xml.sax.*;
import org.xml.sax.ext.*;
import org.xml.sax.helpers.*;
import com.github.difflib.*;
import com.github.difflib.patch.*;

public class Test {

    private static SAXParserFactory newfactory, oldfactory;
    private static XMLInputFactory newstaxfactory, oldstaxfactory;

    private boolean newparser, oldparser, decl, lexical, entity, dtd, speed, quiet, large, number, progress, stax;
    private Boolean valid;              // expectation or null for none

    @SuppressWarnings("unchecked")
    private void run(String file) {
        // newparser!=null && oldparser!=null && large
        //   set digest, run in two threads, compare results at end
        // newparser!=null && oldparser!=null
        //   set log, run in two threads, whenever log is notified compare the most recent records and halt on difference
        // large
        //   set digest, run, print result
        // normal
        //   set nether, run
        if (newparser && oldparser) {
            MyHandler handlerold, handlernew;
            Callback callback;
            List<String> oldlist = new ArrayList<String>();
            List<String> newlist = new ArrayList<String>();
            if (large) {
                callback = new Callback() {
                    MessageDigest digestold, digestnew;
                    public void msg(String msg, MyHandler handler) {
                        try {
                            if (digestold == null) {
                                digestold = MessageDigest.getInstance("SHA-256");
                                digestnew = MessageDigest.getInstance("SHA-256");
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        boolean old = handler.toString().equals("old");
                        MessageDigest digest = old ? digestold : digestnew;
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
                        if (msg.equals("endDocument")) {
                            if (old) {
                                oldlist.add(fmt(digestold.digest()));
                            } else {
                                newlist.add(fmt(digestnew.digest()));
                            }
                        }
                    }
                };
            } else {
                callback = new Callback() {
                    SAXException e = null;
                    int removed = 0;
                    public synchronized void msg(String msg, MyHandler handler) throws SAXException {
                        boolean old = handler.toString().equals("old");
                        if (msg.startsWith("fatalError: org.xml.sax.SAXParseException;")) {
                            msg = "fatalError: org.xml.sax.SAXParseException;";
                        }
                        if (old) {
                            oldlist.add(msg);
                        } else {
                            newlist.add(msg);
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
                            if (stax) {
                                parseSTAX(file, (old?oldstaxfactory:newstaxfactory), (old?handlerold:handlernew));
                            } else {
                                parseSAX(file, old?oldfactory:newfactory, (old?handlerold:handlernew));
                            }
                        } catch (Exception e) {
                            List<String> list = old ? oldlist : newlist;
                            boolean fail = !list.isEmpty() && list.get(list.size() - 1).startsWith("fatalError");
                            if (!fail) {
                                StringWriter w = new StringWriter();
                                e.printStackTrace(new PrintWriter(w));
                                List<String> l = new ArrayList<String>(Arrays.asList(w.toString().split("\n")));
                                list.addAll(l);
                                list.add("fatalError: " + e.getClass().getName());
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
            String msg = null;
            boolean newFail = !newlist.isEmpty() && newlist.get(newlist.size() - 1).startsWith("fatalError");
            boolean oldFail = !oldlist.isEmpty() && oldlist.get(oldlist.size() - 1).startsWith("fatalError");
            boolean diff = true;
            if (oldlist.equals(newlist)) {
                if (valid == null) {
                    msg = "GOOD";
                } else if (!valid.booleanValue() && newFail) {
                    msg = "GOOD (failed)";
                } else if (!valid.booleanValue() && !newFail) {
                    msg = "SO-SO (suceeded identically, expected failure)";
                } else if (valid.booleanValue() && newFail) {
                    msg = "SO-SO (failed identically, expected success)";
                } else if (valid.booleanValue() && !newFail) {
                    msg = "GOOD";
                }
            } else {
                if (valid == null) {
                    if (newFail && oldFail) {
                        msg = "BAD (both failed differently)";
                    } else if (newFail) {
                        msg = "BAD (BFO failed, system succeded)";
                    } else if (oldFail) {
                        msg = "BAD (BFO succeeded, system failed)";
                    } else {
                        msg = "BAD (differences)";
                    }
                } else if (valid.booleanValue()) {
                    if (newFail && oldFail) {
                        msg = "BAD (both failed differently, success expected)";
                    } else if (newFail) {
                        msg = "BAD (BFO failed, system succeded, success expected)";
                    } else if (oldFail) {
                        msg = "GOOD (BFO succeeded, system failed, success expected)";
                        diff = false;
                    } else {
                        msg = "BAD (expected success, with differences)";
                    }
                } else {
                    if (newFail && oldFail) {
                        msg = "SO-SO (both failed differently, failure expected)";
                    } else if (newFail) {
                        msg = "GOOD (BFO failed, system succeded, failure expected)";
                        diff = false;
                    } else if (oldFail) {
                        msg = "BAD (BFO succeeded, system failed, failure expected)";
                    } else {
                        msg = "BAD (expected failure, both succeededwith differences)";
                    }
                }
            }
            System.out.println("### " + file + ":   " + msg);
            if (!quiet && diff) {
                Patch patch = DiffUtils.diff(oldlist, newlist);
                List<String> out = UnifiedDiffUtils.generateUnifiedDiff("xerces/" + file, "bfo/" + file, oldlist, patch, 2);
                for (String s : out) {
                    System.out.println(s);
                }
            }
        } else {
            long start = System.currentTimeMillis();
            try {
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
                if (stax) {
                    parseSTAX(file, (newparser?newstaxfactory:oldstaxfactory), new MyHandler(callback));
                } else {
                    parseSAX(file, (newparser?newfactory:oldfactory), new MyHandler(callback));
                }
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

    private void parseSTAX(String file, XMLInputFactory factory, MyHandler handler) throws XMLStreamException, SAXException, IOException {
        XMLStreamReader r = factory.createXMLStreamReader(file, new BufferedInputStream(new FileInputStream(file)));
        handler.stax(r);
    }

    private void parseSAX(String file, SAXParserFactory factory, MyHandler handler) throws ParserConfigurationException, SAXException, IOException {
        XMLReader r = factory.newSAXParser().getXMLReader();
        r.setContentHandler(handler);
        if (decl) {
            r.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
        }
        if (lexical) {
            r.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        }
        r.setErrorHandler(handler);
        if (dtd) {
            r.setDTDHandler(handler);
        }
        if (entity) {
            r.setEntityResolver(handler);
        }
        InputSource in = new InputSource(new BufferedInputStream(new FileInputStream(file)));
        in.setSystemId(file);
        r.parse(in);
    }

    private static interface Callback {
        public void msg(String msg, MyHandler handler) throws SAXException;
    }

    private class MyHandler extends DefaultHandler2 {
        private StringBuilder chars = new StringBuilder(), ignorable = new StringBuilder(), comment = new StringBuilder();
        private final Callback callback;
        private final long start = System.currentTimeMillis();
        private int count = 0;
        MyHandler(Callback callback) {
            this.callback = callback;
        }
        private void flushChars() throws SAXException {
            if (chars.length() > 0) {
                msg("characters(" + fmt(chars.toString()) + ")");
                chars.setLength(0);
            }
        }
        private void flushComment() throws SAXException {
            if (comment.length() > 0) {
                msg("comment(" + fmt(comment.toString()) + ")");
                comment.setLength(0);
            }
        }
        private void flushIgnorable() throws SAXException {
            if (ignorable.length() > 0) {
                msg("ignorable(" + fmt(ignorable.toString()) + ")");
                ignorable.setLength(0);
            }
        }
        private void flush() throws SAXException {
            flushChars();
            flushComment();
            flushIgnorable();
        }
        private void msg(String s) throws SAXException {
            callback.msg(s, this);
            if (progress && (++count % 1000000) == 0) {
                System.out.println(count + " records at " + (System.currentTimeMillis() - start) + "ms");
            }
        }
        public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
            flush();
            msg("attributeDecl(" + fmt(eName)+", "+fmt(aName)+", "+fmt(type)+", "+fmt(mode)+", "+fmt(value) + ")");
        }
        public void comment(char[] ch, int start, int length) throws SAXException {
            flushChars();
            flushIgnorable();
            comment.append(ch, start, length);
        }
        public void elementDecl(String name, String model) throws SAXException {
            flush();
            msg("elementDecl(" + fmt(name)+", "+fmt(model)+")");
        }
        public void endCDATA() throws SAXException {
            flush();
            msg("endCDATA");
        }
        public void endDTD() throws SAXException {
            flush();
            msg("endDTD");
        }
        public void endEntity(String name) throws SAXException {
            flush();
            msg("endEntity(" + fmt(name) + ")");
        }
        public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
            flush();
            msg("externalEntityDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(fixPath(systemId))+")");
        }
        public InputSource getExternalSubset(String name, String baseURI) throws SAXException {
            flush();
            msg("getExternalSubset(" + fmt(name)+", "+fmt(fixPath(baseURI))+")");
            return null;
        }
        public void internalEntityDecl(String name, String value) throws SAXException {
            flush();
            msg("internalEntityDecl(" + fmt(name)+", "+fmt(value)+")");
        }
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            flush();
            msg("resolveEntity(" + fmt(publicId)+", "+fmt(fixPath(systemId))+")");
            return null;
        }
        public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException {
            flush();
            msg("resolveEntity(" + fmt(name)+", "+fmt(publicId)+", "+fmt(fixPath(baseURI))+", "+fmt(fixPath(systemId))+")");
            return null;
        }
        public void startCDATA() throws SAXException {
            flush();
            msg("startCDATA");
        }
        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            flush();
            msg("startDTD(" + fmt(name)+", "+fmt(publicId)+", "+fmt(fixPath(systemId))+")");
        }
        public void startEntity(String name) throws SAXException {
            flush();
            msg("startEntity(" + fmt(name)+")");
        }
        public void characters(char[] ch, int start, int length) throws SAXException {
            flushComment();
            flushIgnorable();
            chars.append(ch, start, length);
        }
        public void endDocument() throws SAXException {
            flush();
            msg("endDocument");
        }
        public void endElement(String uri, String localName, String qName) throws SAXException {
            flush();
            msg("endElement(" + fmt(uri)+", "+fmt(localName)+", "+fmt(qName)+")");
        }
        public void endPrefixMapping(String prefix) throws SAXException {
            flush();
            msg("endPrefixMapping(" + fmt(prefix)+")");
        }
        public void error(SAXParseException e) throws SAXException {
            flush();
            msg("error: " + e);
        }
        public void fatalError(SAXParseException e) throws SAXException {
            flush();
            msg("fatalError: " + e);
        }
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            flushChars();
            flushComment();
            ignorable.append(ch, start, length);
        }
        public void notationDecl(String name, String publicId, String systemId) throws SAXException {
            flush();
            msg("notationDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(fixPath(systemId))+")");
        }
        public void processingInstruction(String target, String data) throws SAXException {
            flush();
            msg("processingInstruction(" + fmt(target)+", "+fmt(data)+")");
        }
        public void setDocumentLocator(Locator locator) {
            try {
                flush();
                msg("setDocumentLocator(" + fmt(locator) + ")");
            } catch (SAXException e) {
                throw new RuntimeException(e);
            }
        }
        public void skippedEntity(String name) throws SAXException {
            flush();
            msg("skippedEntity(" + fmt(name) + ")");
        }
        public void startDocument() throws SAXException {
            flush();
            msg("startDocument()");
        }
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            flush();
            msg("startElement(" + fmt(uri)+", "+fmt(localName)+", "+fmt(qName)+", "+fmt(attributes)+")");
        }
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            flush();
            msg("startPrefixMapping(" + fmt(prefix)+", "+fmt(uri) + ")");
        }
        public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
            flush();
            msg("unparsedEntityDecl(" + fmt(name)+", "+fmt(publicId)+", "+fmt(fixPath(systemId))+", "+fmt(notationName)+")");
        }
        public void warning(SAXParseException e) throws SAXException {
            flush();
            msg("warning: " + e);
        }

        void stax(XMLStreamReader r) throws XMLStreamException, SAXException {
            StringBuilder sb = new StringBuilder();
            while (r.hasNext()) {
                int type = r.next();
                switch (type) {
                    case XMLStreamConstants.START_DOCUMENT:
                        msg("startDocument: enc=" + fmt(r.getEncoding()) + " version=" + fmt(r.getVersion()) + " standalone=" + r.isStandalone() + "/" + r.standaloneSet() + " charset=" + fmt(r.getCharacterEncodingScheme()));
                        break;
                    case XMLStreamConstants.ATTRIBUTE:
                        sb.setLength(0);
                        sb.append("attribute: att=[");
                        for (int i=0;i<r.getAttributeCount();i++) {
                            sb.append((i==0?"":", ") + fmt(r.getAttributeName(i))+"="+fmt(r.getAttributeValue(i)));
                        }
                        msg(sb.toString());
                        break;
                    case XMLStreamConstants.START_ELEMENT:
                        sb.setLength(0);
                        sb.append("startEement: " + r.getName());
                        sb.append(" att=[");
                        for (int i=0;i<r.getAttributeCount();i++) {
                            sb.append((i==0?"":", ") + fmt(r.getAttributeName(i))+"="+fmt(r.getAttributeValue(i)));
                        }
                        sb.append("] ");
                        sb.append("ns=[");
                        for (int i=0;i<r.getNamespaceCount();i++) {
                            sb.append((i==0?"":", ") + fmt(r.getNamespacePrefix(i))+"="+fmt(r.getNamespaceURI(i)));
                        }
                        sb.append("]");
                        msg(sb.toString());
                        break;
                    case XMLStreamConstants.NAMESPACE:
                        sb.setLength(0);
                        sb.append("namespace: ns=[");
                        for (int i=0;i<r.getNamespaceCount();i++) {
                            sb.append((i==0?"":", ") + fmt(r.getNamespacePrefix(i))+"="+fmt(r.getNamespaceURI(i)));
                        }
                        sb.append("]");
                        msg(sb.toString());
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        sb.setLength(0);
                        sb.append("endElement: " + r.getName() + " ns=[");
                        for (int i=0;i<r.getNamespaceCount();i++) {
                            sb.append((i==0?"":", ") + fmt(r.getNamespacePrefix(i))+"="+fmt(r.getNamespaceURI(i)));
                        }
                        sb.append("]");
                        msg(sb.toString());
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        msg("characters: " + fmt(r.getText()));
                        break;
                    case XMLStreamConstants.CDATA:
                        msg("cdata: " + fmt(r.getText()));
                        break;
                    case XMLStreamConstants.COMMENT:
                        msg("comment: " + fmt(r.getText()));
                        break;
                    case XMLStreamConstants.SPACE:
                        msg("space: " + fmt(r.getText()));
                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        msg("endDocument");
                        break;
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        msg("pi " + fmt(r.getPITarget()) + " " + fmt(r.getPIData()));
                        break;
                    case XMLStreamConstants.ENTITY_REFERENCE:
                        msg("entity " + fmt(r.getLocalName()) + " " + fmt(r.getText()));
                        break;
                    case XMLStreamConstants.DTD:
                        msg("dtd");
                        break;
                }
            }
            r.close();
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

    private static String fixPath(String s) {
        if (s != null && s.startsWith("file:///")) {
            s = "file:/" + s.substring(8);
        }
        return s;
    }

    private static String fmt(QName name) {
        return "{" + name.getNamespaceURI() + "}" + name.getPrefix() + ":" + name.getLocalPart();
    }

    private static String fmt(Attributes atts) {
        StringBuilder sb = new StringBuilder();
        List<Integer> index = new ArrayList<Integer>();
        for (int i=0;i<atts.getLength();i++) {
            index.add(i);
        }
        Collections.<Integer>sort(index, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return atts.getQName(a).compareTo(atts.getQName(b));
            }
        });
        sb.append("{");
        for (Integer i : index) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            String uri = atts.getURI(i);
            if (uri.equals("")) {
                sb.append(fmt(atts.getQName(i)) + "=" + fmt(atts.getValue(i)));
            } else {
                String u = fmt(atts.getURI(i));
                String q = fmt(atts.getQName(i));
                q = q.substring(1, q.length() - 1);
                u = u.substring(1, u.length() - 1);
                sb.append("\"{" + uri + "}" + q + "\"=" + fmt(atts.getValue(i)));
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
            sb.append("system=" + fmt(fixPath(locator.getSystemId())));
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

    public static void main(String[] argsa) throws Exception {
        newfactory = new BFOSAXParserFactory();
        newfactory.setNamespaceAware(true);
        newstaxfactory = new BFOXMLInputFactory();

        List<String> args = new ArrayList<String>(Arrays.asList(argsa));

        boolean filearg = false;
        for (int i=0;i<args.size();i++) {
            String s = args.get(i);
            if (!s.startsWith("--")) {
                filearg = true;
            } else if (s.equals("--old-class")) {
                i++;
            }
        }
        if (!filearg) {
            File f = new File("samples/xmlconf/xmlconf.xml");
            List<Map<String,String>> tests = new ArrayList<Map<String,String>>();
            SAXParserFactory.newDefaultInstance().newSAXParser().parse(f, new DefaultHandler() {
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
            String validity = "--novalidity";
            for (Map<String,String> test : tests) {
                String v = test.get("type");
                if (v == null) {
                    v = "--novalidity";
                } else if ("valid".equals(v)) {
                    v = "--valid";
                } else {
                    v = "--invalid";
                }
                if (!v.equals(validity)) {
                    l.add(v);
                    validity = v;
                }
                String uri = "samples/xmlconf/" + test.get("uri");
                l.add(uri.replaceAll("//*", "/"));
            }
            args.addAll(l);
        }

        boolean newparser = true, oldparser = false, decl = false, lexical = false, speed = false, quiet = false, number = false, large = false, progress = false, dtd = false, entity = false, stax = false;
        Boolean valid = null;

        final long start = System.currentTimeMillis();
        for (int i=0;i<args.size();i++) {
            String arg = args.get(i);
            if (arg.equals("--old")) {
                newparser = false;
                oldparser = true;
            } else if (arg.equals("--new")) {
                newparser = true;
                oldparser = false;
            } else if (arg.equals("--both")) {
                newparser = true;
                oldparser = true;
            } else if (arg.equals("--old-class")) {
                oldfactory = SAXParserFactory.newInstance(args.get(++i), null);
                oldfactory.setNamespaceAware(true);
            } else if (arg.equals("--decl")) {
                decl = true;
            } else if (arg.equals("--lexical")) {
                lexical = true;
            } else if (arg.equals("--dtd")) {
                dtd = true;
            } else if (arg.equals("--entity")) {
                entity = true;
            } else if (arg.equals("--speed")) {
                speed = true;
            } else if (arg.equals("--progress")) {
                progress = true;
            } else if (arg.equals("--digest")) {
                large = true;
            } else if (arg.equals("--number")) {
                number = true;
            } else if (arg.equals("--valid")) {
                valid = true;
            } else if (arg.equals("--invalid")) {
                valid = false;
            } else if (arg.equals("--novalidity")) {
                valid = null;
            } else if (arg.equals("--quiet")) {
                quiet = true;
            } else if (arg.equals("--stax")) {
                stax = true;
            } else if (arg.equals("--cache")) {
                newfactory.setFeature("http://bfo.com/sax/features/cache", true);
            } else if (arg.equals("--nocache")) {
                newfactory.setFeature("http://bfo.com/sax/features/cache", false);
            } else if (arg.equals("--cache-publicid")) {
                newfactory.setFeature("http://bfo.com/sax/features/cache-publicid", true);
            } else if (arg.equals("--nocache-publicid")) {
                newfactory.setFeature("http://bfo.com/sax/features/cache-publicid", false);
            } else if (arg.equals("--threads")) {
                newfactory.setFeature("http://bfo.com/sax/features/threads", true);
            } else if (arg.equals("--nothreads")) {
                newfactory.setFeature("http://bfo.com/sax/features/threads", false);
            } else {
                if (oldfactory == null) {
                    oldfactory = SAXParserFactory.newDefaultInstance();
                    oldfactory.setNamespaceAware(true);
                    oldstaxfactory = XMLInputFactory.newDefaultFactory();
                }
                newfactory.setFeature("http://bfo.com/sax/features/threads", false);
                Test test = new Test();
                test.newparser = newparser;
                test.oldparser = oldparser;
                test.decl = decl;
                test.lexical = lexical;
                test.entity = entity;
                test.dtd = dtd;
                test.lexical = lexical;
                test.speed = speed;
                test.progress = progress;
                test.quiet = quiet;
                test.speed = speed;
                test.number = number;
                test.large = large;
                test.stax = stax;
                test.valid = valid;
                test.run(arg);
            }
        }
        System.out.println("Done in "+(System.currentTimeMillis() - start)+"ms");
    }

}
