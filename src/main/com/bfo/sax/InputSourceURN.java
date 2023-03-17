package com.bfo.sax;

import java.io.*;
import java.util.*;
import org.xml.sax.InputSource;

/**
 * An InputSource with a URN
 */
class InputSourceURN extends InputSource {

    private static final boolean JAVA9;
    static {
        boolean b = false;
        try {
            new ByteArrayInputStream(new byte[0]).readAllBytes();
            b = true;
        } catch (Throwable e) { }
        JAVA9 = b;
    }

    private String urn;

    InputSourceURN() {
    }

    InputSourceURN(InputSource source, String uri) {
        setSystemId(source.getSystemId());
        setPublicId(source.getPublicId());
        setCharacterStream(source.getCharacterStream());
        setByteStream(source.getByteStream());
        setEncoding(source.getEncoding());
        this.urn = urn;
    }

    /**
     * Return the URN which is globally unique for this source.
     * Two InputSources with the same URN are identical
     */
    String getURN() {
        return urn;
    }

    /**
     * Set the URN which is globally unique for this source.
     * Two InputSources with the same URN are identical
     */
    void setURN(String urn) {
        this.urn = urn;
    }

    /**
     * Set the URN to a globally unique value for this source,
     * calculated from the content. The content is unchanged
     * (technically, it's read into memory, digested, but no
     * change externally)
     */
    String setURNFromContent() throws IOException {
        try {
            MurmurHash3 hash = new MurmurHash3();
            Reader r = getCharacterStream();
            // Do things directly to avoid copying buffer we don't have tos
            if (r != null) {
                char[] buf = new char[4096];
                int len = 0, l;
                while ((l=r.read(buf, len, buf.length - len)) >= 0) {
                    len += l;
                    if (buf.length - len < 4096 ) {
                        buf = Arrays.copyOf(buf, buf.length + (buf.length >> 1));
                    }
                }
                r.close();
                // exact hash process is arbitrary, so long as less bytes is better
                for (int i=0;i<len;i++) {
                    char c = buf[i];
                    if (c <= 0xff) {
                        hash.update(c);
                    } else {
                        hash.update(c>>16);
                        hash.update(c&0xff);
                    }
                }
                setURN("urn:murmur3:" + hash.getValue128().toString(16));
                setCharacterStream(new CharArrayReader(buf, 0, len));
            } else {
                InputStream in = getByteStream();
                if (in == null) {
                    throw new IllegalStateException("No stream");
                }
                byte[] buf;
                int len;
                if (JAVA9) {
                    buf = in.readAllBytes();
                    len = buf.length;
                } else {
                    if (!in.markSupported()) {
                        in = new BufferedInputStream(in);
                    }
                    len = 0;
                    int l;
                    buf = new byte[Math.min(4096, in.available())];
                    while ((l=in.read(buf, len, buf.length - len)) >= 0) {
                        len += l;
                        if (buf.length - len < 4096 ) {
                            buf = Arrays.copyOf(buf, buf.length + (buf.length >> 1));
                        }
                    }
                }
                in.close();
                hash.update(buf, 0, len);
                setURN("urn:murmur3:" + hash.getValue128().toString(16));
                setByteStream(new ByteArrayInputStream(buf, 0, len));
            }
            return getURN();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            }
            throw e;
        }
    }

}
