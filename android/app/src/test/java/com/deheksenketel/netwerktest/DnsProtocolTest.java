package com.deheksenketel.netwerktest;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class DnsProtocolTest {
    private byte[] response(int flags, int type, byte[] value) throws Exception {
        byte[] question = Diagnostics.question(1234, "www.deheksenketel.com", type);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(1234); out.writeShort(flags); out.writeShort(1);
        out.writeShort(value == null ? 0 : 1); out.writeInt(0);
        out.write(question, 12, question.length - 12);
        if (value != null) {
            out.writeShort(0xc00c); out.writeShort(type); out.writeShort(1);
            out.writeInt(60); out.writeShort(value.length); out.write(value);
        }
        return bytes.toByteArray();
    }
    @Test public void compressedIpv4Answer() throws Exception {
        var reply = Diagnostics.parse(response(0x8180, 1, new byte[]{(byte)192, 0, 2, 1}), 1234, Diagnostics.HOST, 1);
        assertEquals("NOERROR", reply.status());
        assertEquals("192.0.2.1", reply.records().get(0).getString("value"));
        assertEquals(Diagnostics.HOST, reply.records().get(0).getString("name"));
    }
    @Test public void ipv6Answer() throws Exception {
        byte[] address = new byte[16]; address[0] = 0x20; address[1] = 1; address[15] = 1;
        var reply = Diagnostics.parse(response(0x8180, 28, address), 1234, Diagnostics.HOST, 28);
        assertEquals(28, reply.records().get(0).getInt("type"));
        assertTrue(reply.records().get(0).getString("value").contains(":"));
    }
    @Test public void nxdomain() throws Exception {
        assertEquals("NXDOMAIN", Diagnostics.parse(response(0x8183, 1, null), 1234, Diagnostics.HOST, 1).status());
    }
    @Test(expected = IOException.class) public void wrongTransaction() throws Exception {
        Diagnostics.parse(response(0x8180, 1, null), 999, Diagnostics.HOST, 1);
    }
    @Test(expected = IOException.class) public void wrongHost() throws Exception {
        Diagnostics.parse(response(0x8180, 1, null), 1234, "other.example", 1);
    }
    @Test(expected = IOException.class) public void truncatedTcpAnswer() throws Exception {
        Diagnostics.parse(response(0x8380, 1, null), 1234, Diagnostics.HOST, 1);
    }
    @Test(expected = IOException.class) public void compressionLoop() throws Exception {
        Diagnostics.name(new byte[]{(byte)0xc0, 0}, new int[]{0});
    }
}
