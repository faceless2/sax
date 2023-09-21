package com.bfo.sax;

import java.util.zip.*;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MurmurHash3-128-x64
 * https://github.com/sangupta/murmur
 * http://murmurhash.shorelabs.com
 * 64-bit value derived from xor of 128bit value
 */
class MurmurHash3 implements Checksum {

    private static final int X86_32_C1 = 0xcc9e2d51;
    private static final int X86_32_C2 = 0x1b873593;
    private static long X64_128_C1 = 0x87c37b91114253d5L;
    private static long X64_128_C2 = 0x4cf5ad432745937fL;

    private long h1, h2, seed;
    private int length, bc;
    private BigInteger hash;
    private byte[] hold = new byte[16];

    public MurmurHash3() {
        this(0);
    }

    public MurmurHash3(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = seed;
        reset();
    }

    @Override public void reset() {
        h1 = seed;
        h2 = seed;
        hash = null;
        bc = length = 0;
    }

    @Override public void update(int b) {
        hold[bc++] = (byte)b;
        length++;
        if (bc == 16) {
            bc = 0;
            update(hold, 0, 16);
            length -= 16;
        }
    }

    @Override public void update(byte[] data, int off, int len) {
        ByteBuffer buffer = ByteBuffer.wrap(data, off, len);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        while (bc > 0 && buffer.remaining() > 0) {
            update(buffer.get() & 0xFF);
        }
        while (buffer.remaining() >= 16) {
            long k1 = buffer.getLong();
            long k2 = buffer.getLong();
            h1 ^= mixK1(k1);
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;
            h2 ^= mixK2(k2);
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
            length += 16;
        }
        while (buffer.remaining() > 0) {
            update(buffer.get() & 0xFF);
        }
    }

    @Override public long getValue() {
        BigInteger b = getValue128();
        return b.shiftRight(64).longValue() ^ b.longValue();
    }

    public BigInteger getValue128() {
        if (hash == null) {
            if (bc != 0) {
                long k1 = 0;
                long k2 = 0;
                ByteBuffer buffer = ByteBuffer.wrap(hold, 0, bc);
                switch (bc) {
                    case 15: k2 ^= (long) (buffer.get(14) & 0xFF) << 48;
                    case 14: k2 ^= (long) (buffer.get(13) & 0xFF) << 40;
                    case 13: k2 ^= (long) (buffer.get(12) & 0xFF) << 32;
                    case 12: k2 ^= (long) (buffer.get(11) & 0xFF) << 24;
                    case 11: k2 ^= (long) (buffer.get(10) & 0xFF) << 16;
                    case 10: k2 ^= (long) (buffer.get(9) & 0xFF) << 8;
                    case 9: k2 ^= (long) (buffer.get(8) & 0xFF);
                    case 8: k1 ^= buffer.getLong();
                            break;
                    case 7: k1 ^= (long) (buffer.get(6) & 0xFF) << 48;
                    case 6: k1 ^= (long) (buffer.get(5) & 0xFF) << 40;
                    case 5: k1 ^= (long) (buffer.get(4) & 0xFF) << 32;
                    case 4: k1 ^= (long) (buffer.get(3) & 0xFF) << 24;
                    case 3: k1 ^= (long) (buffer.get(2) & 0xFF) << 16;
                    case 2: k1 ^= (long) (buffer.get(1) & 0xFF) << 8;
                    case 1: k1 ^= (long) (buffer.get(0) & 0xFF);
                            break;
                    default:
                            break;
                }
                // mix
                h1 ^= mixK1(k1);
                h2 ^= mixK2(k2);
            }
            h1 ^= length;
            h2 ^= length;
            h1 += h2;
            h2 += h1;
            h1 = fmix64(h1);
            h2 = fmix64(h2);
            h1 += h2;
            h2 += h1;
            ByteBuffer b = ByteBuffer.wrap(hold, 0, 16);
            b.putLong(h1);
            b.putLong(h2);
            hash = new BigInteger(1, hold);
        }
        return hash;
    }

    private static long mixK1(long k1) {
        k1 *= X64_128_C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= X64_128_C2;
        return k1;
    }

    private static long mixK2(long k2) {
        k2 *= X64_128_C2;
	k2 = Long.rotateLeft(k2,  33);
	k2 *= X64_128_C1;
        return k2;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    public MessageDigest getMessageDigest() {
        return new MessageDigest("murmurhash3") {
            protected byte[] engineDigest() {
                return getValue128().toByteArray();
            }
            protected int engineGetDigestLength() {
                return 16;
            }
            protected void engineReset() {
                MurmurHash3.this.reset();
            }
            protected void engineUpdate(byte input) {
                engineUpdate(new byte[] { input }, 0, 1);
            }
            protected void engineUpdate(byte[] input, int offset, int len) {
                MurmurHash3.this.update(input, offset, len);
            }
        };
    }

    /*
    public static void main(String[] args) throws Exception {
        for (String s : args) {
            byte[] b = s.getBytes("ISO-8859-1");
            MurmurHash3 m = new MurmurHash3();
            m.update(b, 0, b.length);
            String v0 = m.getValue128().toString();
            System.out.printf("new=%s\n", v0);
//            long v1 = hash64(b, b.length, m.seed);
        }
    }
    */
}
