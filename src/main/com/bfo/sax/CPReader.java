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

    public String getPublicId() {
        return null;
    }

    public String getSystemId() {
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

    static CPReader normalize(final CPReader reader, boolean fxml11) {
        return new CPReader() {
            private boolean xml11 = fxml11;
            private int hold = -1, line = 1, column = 1;

            @Override public void setXML11(boolean b) {
                xml11 = b;
            }

            @Override public boolean isXML11() {
                return xml11;
            }

            @Override public String getPublicId() {
                return reader.getPublicId();
            }

            @Override public String getSystemId() {
                return reader.getSystemId();
            }

            @Override public int read() throws SAXException, IOException {
                // xml11
                // * the two-character sequence #xD #xA
                // * the two-character sequence #xD #x85
                // * the single character #x85
                // * the single character #x2028
                // * any #xD character that is not immediately followed by #xA or #x85.
                // xml10
                // * the two-character sequence #xD #xA
                // * any #xD character that is not immediately followed by #xA
                int v;
                if (hold >= 0) {
                    v = hold;
                    hold = -1;
                } else {
                    v = reader.read();
                }
                int out;
                if (v < 0x20) {
                    if (v == '\r') {
                        v = reader.read();
                        if (v == '\n' || (xml11 && v == 0x85)) {
                            line++;
                            column = 1;
                            out = '\n';
                        } else {
                            hold = v;
                            line++;
                            column = 1;
                            out = '\n';
                        }
                    } else if (v == '\n') {
                        line++;
                        column = 1;
                        out = v;
                    } else if (v == '\t') {
                        column++;
                        out = v;
                    } else if (v < 0) {
                        out = v;
                    } else {
                        throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                } else if (v >= 0x7f && v <= 0x9f) {
                    if (xml11 && v == 0x85) {
                        line++;
                        column = 1;
                        out = '\n';
                    } else {
                        throw new SAXParseException("Invalid character &#x" + Integer.toHexString(v) + ";", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                } else if (xml11 && v == 0x2028) {
                    line++;
                    column = 1;
                    out = '\n';
                } else {
                    column++;
                    out = v;
                }
                return out;
            }

            @Override public String toString() {
                return "{normalize" + reader + "}";
            }

            @Override public void close() throws IOException {
                reader.close();
            }

            @Override public int getLineNumber() {
                return line;
            }

            @Override public int getColumnNumber() {
                return column;
            }
        };
    }

    /**
     * Return a new CPReader that reads all of a, then all of b. If gap is not null, call it when "a" is done.
     */
    static CPReader getReader(final CPReader a, final CPReader b, final Runnable gap) throws IOException {
        return new CPReader() {
            CPReader r = a;
            @Override public int read() throws SAXException, IOException {
                int v = r.read();
                if (v < 0 && r == a) {
                    if (gap != null) {
                        gap.run();
                    }
                    r = b;
                    v = r.read();
                }
                return v;
            }
            @Override public void close() throws IOException {
                a.close();
                b.close();
            }
            @Override public String getPublicId() {
                return r.getPublicId();
            }
            @Override public String getSystemId() {
                return r.getSystemId();
            }
            @Override public int getLineNumber() {
                return r.getLineNumber();
            }
            @Override public int getColumnNumber() {
                return r.getColumnNumber();
            }
            @Override public void setXML11(boolean xml11) {
                a.setXML11(xml11);
                b.setXML11(xml11);
            }
            @Override public boolean isXML11() {
                return a.isXML11() || b.isXML11();
            }
            @Override public String toString() {
                return "{" + a + " + " + b + "}";
            }
        };
    }

    /**
     * Return a new CPReader that reads from the CharSequence
     */
    static CPReader getReader(String s, final String publicId, final String systemId, final int lineNumber, final int column, final boolean xml11) {
        return new CPReader() {
            int line = lineNumber;
            int col = column;
            int i;
            @Override public int read() {
                if (i == s.length()) {
                    return -1;
                } else {
                    int c = s.charAt(i++);
                    if (c >= 0xd800 && c <= 0xdbff && i < s.length()) {
                        c = fromUTF16(c, s.charAt(i++));
                    }
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
            @Override public int getLineNumber() {
                return lineNumber >= 0 ? line : -1;
            }
            @Override public int getColumnNumber() {
                return lineNumber >=0 ? col : -1;
            }
            @Override public boolean isXML11() {
                return xml11;
            }
            @Override public String toString() {
                return "{string src="+systemId+"}";
            }
        };
    }

    /**
     * Return a new CPReader that reads from the InputSource
     */
    static CPReader getReader(InputSource in) throws IOException, SAXException {
        if (in.getCharacterStream() != null) {
            return getReader(in.getCharacterStream(), in.getPublicId(), in.getSystemId());
        } else {
            return getReader(in.getByteStream(), in.getPublicId(), in.getSystemId());
        }
    }

    /**
     * Return a new CPReader that reads from the Reader
     */
    static CPReader getReader(final Reader in, final String publicId, final String systemId) throws IOException, SAXException {
        if (in == null) {
            return null;
        }
        return new CPReader() {
            @Override public int read() throws IOException, SAXException {
                int v = in.read();
                if (v < 0xd800 || v >= 0xe000) {
                    return v;
                } else if (v <= 0xdbff) {
                    int v2 = in.read();
                    if (v2 >= 0xdc00 && v2 <= 0xdfff) {
                        return fromUTF16(v, v2);
                    } else {
                        throw new SAXParseException("longly high surrogate", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
                } else {
                    throw new SAXParseException("lonely low surrogate", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                }
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
            @Override public String toString() {
                if (in instanceof InputStreamReader) {
                    return "{reader+"+((InputStreamReader)in).getEncoding()+" src=" + systemId+"}";
                } else {
                    return "{reader src=" + systemId+"}";
                }
            }
        };
    }

    /**
     * Return a new CPReader that reads from the InputStream, handling BOM and (if xml), XML encoding
     */
    static CPReader getReader(final InputStream in, final String publicId, final String systemId) throws IOException, SAXException {
        final boolean xml = true;
        byte[] b = new byte[xml ? 80 : 4];
        int len = 0;
        int c;
        while (len < b.length && (c=in.read()) >= 0) {
            b[len++] = (byte)c;
            if (xml && c == '>') {
                break;
            }
        }
        String bomenc = null, xmlenc = null;
        int skip = 0;
        if (b[0] == (byte)0xfe && b[1] == (byte)0xff) {
            bomenc = "utf-16be";
            skip = 2;
        } else if (b[0] == (byte)0xff && b[1] == (byte)0xfe) {
            bomenc = "utf-16le";
            skip = 2;
        } else if (b[0] == (byte)0xef && b[1] == (byte)0xbb && b[2] == (byte)0xbf) {
            bomenc = "utf-8";
            skip = 3;
        } else if (xml && b[0] == 0 && b[1] == (byte)0x3c && b[2] == 0 && b[3] == (byte)0x3f) {
            bomenc = "utf-16be";
        } else if (xml && b[0] == (byte)0x3c && b[1] == 0 && b[2] == (byte)0x3f && b[3] == (byte)0) {
            bomenc = "utf-16le";
        }
        if (bomenc != null && bomenc.startsWith("utf-16") && (len&1) == 1) {
            b[len++] = (byte)in.read();
        }
        String prolog = new String(b, skip, len - skip, bomenc != null ? bomenc : "iso-8859-1");
        if (xml) {
            String s = prolog.replaceAll("\t\n\r", " ");
            if (s.length() > 0 && s.charAt(s.length() - 1) == '>' && s.charAt(s.length() - 2) == '?' && s.charAt(0) == '<' && s.charAt(1) == '?' && Character.toLowerCase(s.charAt(2)) == 'x' && Character.toLowerCase(s.charAt(3)) == 'm' && Character.toLowerCase(s.charAt(4)) == 'l') {
                s = s.substring(5, s.length() - 2).trim();
                if (s.startsWith("version") && s.indexOf(" ") > 0) {
                    s = s.substring(s.indexOf(" ")).trim();
                }
                if (s.startsWith("encoding=")) {
                    s = s.substring(9);
                    c = s.charAt(0);
                    int i;
                    if ((c == '\'' || c == '"') && (i=s.indexOf((char)c, 1)) > 0) {
                        xmlenc = s.substring(1, i).toLowerCase();
                    }
                }
            }
        }
        String enc;
        if (bomenc != null) {
            enc = bomenc;
        } else {
            enc = xmlenc != null ? xmlenc : "utf-8";
            if ("shift_jis".equals(enc)) {
                enc = "windows-31j";
            }
            boolean supported = false;
            try {
                supported = Charset.isSupported(enc);
            } catch (Exception e) {}
            if (!supported) {
                throw new SAXParseException("Unsupported encoding \"" + enc + "\"", publicId, systemId, -1, -1);
            }
            prolog = new String(prolog.getBytes("iso-8859-1"), enc);
        }
        CPReader r;
        if (enc.equals("utf-8") || enc.equals("utf8")) {
            r = new CPReader() {
                @Override public int read() throws IOException, SAXException {
                    int v = in.read();
                    if (v < 0x80) {
                        return v;
                    } else if (v <= 0xDF) {
                        int v2 = in.read();
                        if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        return ((v&0x1F)<<6) | (v2&0x3F);
                    } else if (v <= 0xEF) {
                        int v2 = in.read();
                        if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        int v3 = in.read();
                        if (v3 < 0 || (v3 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        return ((v&0x0F)<<12) | ((v2&0x3F)<<6) | (v3&0x3F);
                    } else if (v <= 0xF7) {
                        int v2 = in.read();
                        if (v2 < 0 || (v2 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        int v3 = in.read();
                        if (v3 < 0 || (v3 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        int v4 = in.read();
                        if (v4 < 0 || (v4 & 0xC0) != 0x80) {
                            throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                        }
                        v = ((v&0x07)<<18) | ((v2&0x3F)<<12) | ((v3&0x3F)<<6) | (v4&0x3F);
                        return v;
                    } else {
                        throw new SAXParseException("bad utf-8 sequence", getPublicId(), getSystemId(), getLineNumber(), getColumnNumber());
                    }
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
                @Override public String toString() {
                    return "{inputstream+utf8 src="+systemId+"}";
                }
            };
        } else if (enc.equals("iso-8859-1") || enc.equals("iso8859-1")) {
            r = new CPReader() {
                @Override public int read() throws IOException {
                    return in.read();
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
                @Override public String toString() {
                    return "{inputstream+8859 src="+systemId+"}";
                }
            };
        } else {
            r = getReader(new InputStreamReader(in, enc), publicId, systemId);
        }
        CPReader r1 = getReader(prolog, publicId, systemId, 1, 1, false);
        return getReader(r1, r, null);
    }

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

}
