package com.bfo.sax;

import java.io.*;
import java.nio.charset.*;
import org.xml.sax.*;

// CodePoint reader - returns codepoints or -1 on EOF
abstract class CPReader {

    public int getLineNumber() {
        return -1;
    }

    public int getColumnNumber() {
        return -1;
    }

    public int getCharacterOffset() {
        return -1;
    }

    public String getPublicId() {
        return null;
    }

    public String getSystemId() {
        return null;
    }

    public String getEncoding() {
        return null;
    }

    public void setXML11(boolean b) {
    }

    public boolean isXML11() {
        return false;
    }

    public void close() throws IOException {
    }

    public abstract int read() throws SAXException, IOException;

    public String asString() throws SAXException, IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c=read()) >= 0) {
            if (c > 0xFFFF) {
                c = toUTF16(c);
                sb.append((char)(c>>16));
                sb.append((char)c);
            } else {
                sb.append((char)c);
            }
        }
        return sb.toString();
    }

    //------------------------------------

    /**
     * Return a new CPReader that reads from the String
     * @param in the input
     * @param encoding the encoding to return from getEncoding() - arbitrary
     */
    static CPReader getReader(final String in, final String publicId, final String systemId, final String encoding, final int lineNumber, final int column, final int charOffset, final boolean xml11) {
        CPReader r = new CPReader() {
            int line = lineNumber;
            int col = column;
            int offset = charOffset;
            int i;
            @Override public int read() {
                if (i == in.length()) {
                    return -1;
                } else {
                    int c = in.charAt(i++);
                    if (c >= 0xd800 && c <= 0xdbff && i < in.length()) {
                        c = fromUTF16(c, in.charAt(i++));
                    }
                    offset++;
                    if (c == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    return c;
                }
            }
            @Override public String getPublicId() {
                return publicId;
            }
            @Override public String getSystemId() {
                return systemId;
            }
            @Override public String getEncoding() {
                return encoding;
            }
            @Override public int getLineNumber() {
                return lineNumber >= 0 ? line : -1;
            }
            @Override public int getColumnNumber() {
                return lineNumber >=0 ? col : -1;
            }
            @Override public int getCharacterOffset() {
                return charOffset >=0 ? offset : -1;
            }
            @Override public boolean isXML11() {
                return xml11;
            }
            @Override public String toString() {
                return "{string val="+BFOXMLReader.fmt(in)+" src="+systemId+" line="+getLineNumber()+" col="+getColumnNumber()+" enc="+encoding+"}";
            }
        };
        return r;
    }

    /**
     * Return a new CPReader that reads from the InputSource
     *
     */
    static CPReader getReader(InputSource source, boolean xml, Boolean xml11) throws IOException, SAXException {
        final String publicid = source.getPublicId();
        final String systemid = source.getSystemId();
        final String sourceenc = source.getEncoding();
        CPReader reader;
        if (source.getCharacterStream() != null) {
            reader = new CharStreamReader(source.getCharacterStream(), publicid, systemid, sourceenc, Boolean.TRUE.equals(xml11), "");
        } else if (source.getByteStream() != null) {
            final InputStream in = source.getByteStream();
            byte[] b = new byte[1024];
            int c, len = 0;
            // First read the first N bytes, or stop at '>'
            while (len < b.length && (c=in.read()) >= 0) {
                b[len++] = (byte)c;
                if (c == '>') {
                    break;
                }
            }
            // Check for a BOM
            String bomenc = null, xmlenc = null;
            int skip = 0;
            if (b[0] == (byte)0xfe && b[1] == (byte)0xff) {
                bomenc = "UTF-16BE";
                skip = 2;
            } else if (b[0] == (byte)0xff && b[1] == (byte)0xfe) {
                bomenc = "UTF-16LE";
                skip = 2;
            } else if (b[0] == (byte)0xef && b[1] == (byte)0xbb && b[2] == (byte)0xbf) {
                bomenc = "UTF-8";
                skip = 3;
            } else if (b[0] == 0 && b[1] == (byte)0x3c && b[2] == 0 && b[3] == (byte)0x3f) {
                bomenc = "UTF-16BE";
            } else if (b[0] == (byte)0x3c && b[1] == 0 && b[2] == (byte)0x3f && b[3] == (byte)0) {
                bomenc = "UTF-16LE";
            }
            if (bomenc != null && bomenc.startsWith("UTF-16") && (len&1) == 1) {
                b[len++] = (byte)in.read();
            }
            String prolog = new String(b, skip, len - skip, bomenc != null ? bomenc : "ISO-8859-1");
            if (!xml) {
                for (int i=0;i<prolog.length();i++) {
                    c = prolog.charAt(i);
                    if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                        if (c == '<') {
                            xml = true;
                        }
                        break;
                    }
                }
            }
            // Now try and identify if its XML (it might not be - could be an included text entity
            // This is very, very hacky but the idea is if we hit any character that would prevent
            // it being XML, its text. It matters because the default encoding for text is ISO8859-1
            if (xml) {
                String s = prolog.replaceAll("\t\n\r", " ");
                if (s.length() > 0 && s.charAt(s.length() - 1) == '>' && s.charAt(s.length() - 2) == '?' && s.charAt(0) == '<' && s.charAt(1) == '?' && Character.toLowerCase(s.charAt(2)) == 'x' && Character.toLowerCase(s.charAt(3)) == 'm' && Character.toLowerCase(s.charAt(4)) == 'l') {
                    s = s.substring(5, s.length() - 2).trim();
                    if (s.startsWith("version") && s.indexOf(" ") > 0) {
                        if (xml11 == null) {
                            xml11 = s.startsWith("version=\"1.1\"") || s.startsWith("version='1.1'");
                        }
                        s = s.substring(s.indexOf(" ")).trim();
                    }
                    if (s.startsWith("encoding=")) {
                        s = s.substring(9);
                        if (s.length() > 0) {
                            c = s.charAt(0);
                            int i;
                            if ((c == '\'' || c == '"') && (i=s.indexOf((char)c, 1)) > 0) {
                                xmlenc = s.substring(1, i);
                            }
                        }
                    }
                }
            }

            String enc = null, publicenc = null;
            if (bomenc != null) {
                enc = publicenc = bomenc;
            } else {
                // If it's XML, use the encoding attribute if specified (fail if invalid), or utf-8
                // If it's anything else, use the source encoding if specified, or utf-8
                if (xmlenc != null) {
                    enc = "shift-jis".equalsIgnoreCase(xmlenc) || "shift_jis".equalsIgnoreCase(xmlenc) ? "windows-31j" : xmlenc;
                    try {
                        enc = Charset.forName(enc).name();
                        publicenc = xmlenc;
                    } catch (Exception e) {
                        throw new SAXParseException("Unsupported encoding \"" + xmlenc + "\"", publicid, systemid, -1, -1);
                    }
                } else if (xml) {
                    enc = publicenc = "UTF-8";
                } else if (sourceenc != null) {
                    enc = "shift-jis".equalsIgnoreCase(sourceenc) || "shift_jis".equalsIgnoreCase(sourceenc) ? "windows-31j" : sourceenc;
                    boolean supported = false;
                    try {
                        enc = Charset.forName(enc).name();
                        publicenc = sourceenc;
                    } catch (Exception e) {
                        enc = "UTF-8";
                    }
                } else {
                    // Try UTF-8, if that fails, use ISO8859-1
                    try {
                        new String(b, skip, len - skip, "UTF-8");
                        enc = "UTF-8";
                    } catch (Exception e) {
                        enc = "ISO-8859-1";
                    }
                }
            }
            // System.out.println("IN: xml="+xml+" bomenc="+bomenc+" publicenc="+publicenc+" sourceenc="+sourceenc+" enc="+enc+" skip="+skip+" len="+len);
            if (xml11 == null) {
                xml11 = false;
            }

            if (enc.equals("UTF-8")) {
                reader = new UTF8CPReader(in, publicid, systemid, xml11, b, skip, len - skip);
            } else if (enc.equals("ISO-8859-1")) {
                reader = new ISO88591CPReader(in, publicid, systemid, xml11, b, skip, len - skip);
            } else {
                prolog = new String(b, skip, len - skip, enc);
                Reader r = new InputStreamReader(in, enc);
                reader = new CharStreamReader(r, publicid, systemid, publicenc, xml11, prolog);
            }
        } else {
            throw new SAXException("InputSource has no streams");
        }
        return reader;
    }

    /**
     * Return a new CPReader that reads from the InputStream, handling BOM and (if xml), XML encoding
     * @param sourceenc the encoding, as specified externall. Priority is BOM, XML encoding (if specified) and then this
     */

    static int toUTF16(int i) {
        if (i >= 0 && i < 0x10000) {
            return i;
        } else {
            i -= 0x10000;
            int h = (i>>10) | 0xd800;
            int l = (i&0x3ff) | 0xdc00;
            return (h<<16) | l;
        }
    }

    static final int fromUTF16(int hi, int lo) {
        return ((hi - 0xd800) << 10) + (lo - 0xdc00) + 0x10000;
    }

    //--------------------------------------------------------------------

    private static class UTF8CPReader extends CPReader {
        private final byte[] buf;
        private int pos, len, column;
        private boolean xml11;
        private int line, bufoffset;
        private final InputStream in;
        private final String publicId, systemId;
        UTF8CPReader(InputStream in, String publicId, String systemId, boolean xml11, byte[] buf, int off, int len) {
            this.in = in;
            this.publicId = publicId;
            this.systemId = systemId;
            this.buf = new byte[8192];
            this.xml11 = xml11;
            System.arraycopy(buf, off, this.buf, 0, len);
            this.len = len;
            this.line = 1;
            this.column = 1;
        }
        @Override public int read() throws IOException, SAXException {
            if (pos >= len) {
                if (pos < 0) {
                    return -1;
                }
                bufoffset += pos;
                len = in.read(buf);
                pos = 0;
                if (len <= 0) {
                    pos = len = -1;
                    return -1;
                }
            }
            int v = buf[pos++] & 0xFF;
            column++;
            if (v < 0x20) {
                // xml10
                // * the two-character sequence #xD #xA
                // * any #xD character that is not immediately followed by #xA
                // xml11
                // * the two-character sequence #xD #xA
                // * the two-character sequence #xD #x85
                // * the single character #x85
                // * the single character #x2028
                // * any #xD character that is not immediately followed by #xA or #x85.
                if (v == '\n') {
                    line++;
                    column = 1;
                } else if (v == '\r') {
                    v = pos == len ? in.read() : buf[pos] & 0xFF;
                    pos++;
                    if (xml11 && v == 0xC2) {        // 0xC2 0x85 is UTF-8 encoding for 0x84
                        v = pos == len ? in.read() : buf[pos] & 0xFF;
                        pos++;
                        if (v == 0x85) {
                            // noop
                        } else if (pos <= len) {
                            pos -= 2;
                        } else {
                            buf[0] = (byte)0xC2;
                            buf[1] = (byte)v;
                            bufoffset += 2;
                            len = 2;
                            pos = 0;
                        }
                        v = '\n';
                    } else if (v < 0) {
                        pos--;
                        v = '\n';
                    } else if (v != '\n') {
                        pos--;
                        if (pos == len) {
                            buf[0] = (byte)v;
                            bufoffset++;
                            len = 1;
                            pos = 0;
                        }
                        v = '\n';
                    }
                    line++;
                    column = 1;
                } else if (v != '\t' && v >= 0) {
                    throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());              
                }
            } else if (v < 0x7f) {
                // By far the most common branch
            } else if (v == 0x7f) {
                if (xml11) {
                    throw new SAXParseException("Invalid XML 1.1 character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
            } else if (v <= 0xDF) {
                int v2 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                v = ((v&0x1F)<<6) | (v2&0x3F);
                if (v < 0x80) {
                    throw new SAXParseException("overlong UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                } else if (xml11) {
                    if (v == 0x85) {
                        v = '\n';
                        line++;
                        column = 1;
                    } else if (v >= 0x7f && v <= 0x9f) {
                        throw new SAXParseException("Invalid XML 1.1 character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                }
            } else if (v <= 0xEF) {
                int v2 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                int v3 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v3 < 0 || (v3 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                v = ((v&0x0F)<<12) | ((v2&0x3F)<<6) | (v3&0x3F);
                if (v < 0x800) {
                    throw new SAXParseException("overlong UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                } else if (xml11 && v == 0x2028) {
                    v = '\n';
                    line++;
                    column = 1;
                }
                if (v >= 0xd800 && (v <= 0xdfff || v == 0xfffe || v == 0xffff)) {
                    throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
            } else if (v <= 0xF7) {
                int v2 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                int v3 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v3 < 0 || (v3 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                int v4 = pos < len ? buf[pos++] & 0xFF : in.read();
                if (v4 < 0 || (v4 & 0xC0) != 0x80) {
                    throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
                v = ((v&0x07)<<18) | ((v2&0x3F)<<12) | ((v3&0x3F)<<6) | (v4&0x3F);
                if (v < 0x8000) {
                    throw new SAXParseException("overlong UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
            } else {
                throw new SAXParseException("bad UTF-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
            }
            return v;
        }
        @Override public void close() throws IOException {
            in.close();
        }
        @Override public String getPublicId() {
            return publicId;
        }
        @Override public String getSystemId() {
            return systemId;
        }
        @Override public String getEncoding() {
            return "UTF-8";
        }
        @Override public int getLineNumber() {
            return line;
        }
        @Override public int getColumnNumber() {
            return column;
        }
        @Override public int getCharacterOffset() {
            return bufoffset + pos - 1;
        }
        @Override public void setXML11(boolean xml11) {
            this.xml11 = xml11;
        }
        @Override public boolean isXML11() {
            return xml11;
        }
        @Override public String toString() {
            return "{inputstream-utf8 src="+BFOXMLReader.fmt(systemId)+"}";
//            return "{utf8-inputstream xml11="+xml11+" src="+systemId+" pos="+pos+"}"; //  buf="+BFOXMLReader.fmt(new String(buf, 0, len, StandardCharsets.UTF_8))+"}";
        }
    }

    private static class ISO88591CPReader extends CPReader {
        private final byte[] buf;
        private int pos, len, line, column, bufoffset, lineoffset;
        private boolean xml11;
        private final InputStream in;
        private final String publicId, systemId;
        ISO88591CPReader(InputStream in, String publicId, String systemId, boolean xml11, byte[] buf, int off, int len) {
            this.in = in;
            this.publicId = publicId;
            this.systemId = systemId;
            this.buf = new byte[8192];
            this.xml11 = xml11;
            System.arraycopy(buf, off, this.buf, 0, len);
            this.len = len;
            this.line = 1;
            this.column = 1;
        }
        @Override public int read() throws IOException, SAXException {
            if (pos == len) {
                if (pos < 0) {
                    return -1;
                }
                bufoffset += pos;
                len = in.read(buf);
                if (len <= 0) {
                    pos = len = -1;
                    return -1;
                }
                pos = 0;
            }
            int v = buf[pos++] & 0xFF;
            column++;
            if (v < 0x20) {
                // xml10
                // * the two-character sequence #xD #xA
                // * any #xD character that is not immediately followed by #xA
                // xml11
                // * the two-character sequence #xD #xA
                // * the two-character sequence #xD #x85
                // * the single character #x85
                // * the single character #x2028
                // * any #xD character that is not immediately followed by #xA or #x85.
                if (v == '\n') {
                    line++;
                    column = 1;
                } else if (v == '\r') {
                    v = pos == len ? in.read() : buf[pos] & 0xFF;
                    pos++;
                    if (xml11 && v == 0x85) {
                        v = '\n';
                    } else if (v < 0) {
                        pos--;
                        v = '\n';
                    } else if (v != '\n') {
                        pos--;
                        if (pos == len) {
                            bufoffset++;
                            buf[0] = (byte)v;
                            len = 1;
                            pos = 0;
                        }
                        v = '\n';
                    }
                    line++;
                    column = 1;
                } else if (v != '\t' && v >= 0) {
                    throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());              
                }
            } else if (v >= 0x7f) {
                if (xml11) {
                    if (v == 0x85) {
                        v = '\n';
                        line++;
                        column = 1;
                    } else if (v >= 0x7f && v <= 0x9f) {
                        throw new SAXParseException("Invalid XML 1.1 character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                }
            }
            return v;
        }
        @Override public void close() throws IOException {
            in.close();
        }
        @Override public String getPublicId() {
            return publicId;
        }
        @Override public String getSystemId() {
            return systemId;
        }
        @Override public String getEncoding() {
            return "ISO-8859-1";
        }
        @Override public int getLineNumber() {
            return line;
        }
        @Override public int getColumnNumber() {
            return column;
        }
        @Override public int getCharacterOffset() {
            return bufoffset + pos - 1;
        }
        @Override public void setXML11(boolean xml11) {
            this.xml11 = xml11;
        }
        @Override public boolean isXML11() {
            return xml11;
        }
        @Override public String toString() {
            return "{inputstream-iso-8859-1 src="+BFOXMLReader.fmt(systemId)+"}";
//            return "{iso88591-inputstream xml11="+xml11+" src="+systemId+"}";
        }
    }

    private static class CharStreamReader extends CPReader {
        final private char[] buf;
        private int pos, len, column;
        private boolean xml11;
        private int line, bufoffset;
        private final Reader in;
        private String publicid, systemid, encoding;

        CharStreamReader(Reader in, String publicid, String systemid, String encoding, boolean xml11, String prolog) {
            this.in = in;
            this.publicid = publicid;
            this.systemid = systemid;
            this.line = this.column = 1;
            this.buf = new char[8192];
            this.len = prolog.length();
            this.xml11 = xml11;
            System.arraycopy(prolog.toCharArray(), 0, buf, 0, len);
            if (in instanceof InputStreamReader && (encoding == null)) {
                encoding = ((InputStreamReader)in).getEncoding();
            }
            this.encoding = encoding;
        }

        @Override public int read() throws IOException, SAXException {
            if (pos == len) {
                if (pos < 0) {
                    return -1;
                }
                bufoffset += pos;
                len = in.read(buf);
                if (len <= 0) {
                    pos = len = -1;
                    return -1;
                }
                pos = 0;
            }
            int v = buf[pos++];
            column++;
            if (v >= 0x7f) {
                if (v >= 0xd800) {
                    if (v < 0xdbff) {
                        int v2 = pos < len ? buf[pos++] : in.read();
                        if (v2 >= 0xdc00 && v2 <= 0xdfff) {
                            v = fromUTF16(v, v2);
                        } else {
                            throw new SAXParseException("lonely high surrogate &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                    } else if (v == 0xfffe || v == 0xffff) {
                         throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    } else if (v >= 0xdc00 && v <= 0xdfff) {
                        throw new SAXParseException("lonely low surrogate &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                } else if (xml11) {
                    if (v == 0x2028) {
                        v = '\n';
                        line++;
                        column = 1;
                    } else if (v >= 0x7f && v <= 0x9f) {
                        throw new SAXParseException("Invalid XML 1.1 character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                }
            } else if (v < 0x20) {
                // xml11
                // * the two-character sequence #xD #xA
                // * the two-character sequence #xD #x85
                // * the single character #x85
                // * the single character #x2028
                // * any #xD character that is not immediately followed by #xA or #x85.
                // xml10
                // * the two-character sequence #xD #xA
                // * any #xD character that is not immediately followed by #xA
                if (v == '\n') {
                    line++;
                    column = 1;
                } else if (v == '\r') {
                    v = pos == len ? in.read() : buf[pos];
                    pos++;
                    if (xml11 && v == 0x85) {
                        v = '\n';
                    } else if (v < 0) {
                        v = '\n';
                        pos--;
                    } else if (v != '\n') {
                        pos--;
                        if (pos == len) {
                            bufoffset++;
                            buf[0] = (char)v;
                            len = 1;
                            pos = 0;
                        }
                        v = '\n';
                    }
                    line++;
                    column = 1;
                } else if (v != '\t' && v >= 0) {
                    throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());              
                }
            }
            return v;
        }
        @Override public void close() throws IOException {
            in.close();
        }
        @Override public String getPublicId() {
            return publicid;
        }
        @Override public String getSystemId() {
            return systemid;
        }
        @Override public int getLineNumber() {
            return line;
        }
        @Override public int getColumnNumber() {
            return column;
        }
        @Override public int getCharacterOffset() {
            return bufoffset + pos - 1;
        }
        @Override public void setXML11(boolean xml11) {
            this.xml11 = xml11;
        }
        @Override public boolean isXML11() {
            return xml11;
        }
        @Override public String getEncoding() {
            return encoding;
        }
        @Override public String toString() {
            return "{reader"+(encoding==null?"":"("+encoding+")")+" src=" + BFOXMLReader.fmt(systemid) + "}";
//            return "{reader"+(encoding==null?"":"("+encoding+")")+" xml11="+xml11+" src=" + systemid+" buf="+BFOXMLReader.fmt(new String(buf, 0, len))+"}";
        }
    }

}
