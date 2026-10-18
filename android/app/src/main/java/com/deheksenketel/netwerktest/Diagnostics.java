package com.deheksenketel.netwerktest;

import android.content.Context;
import android.net.*;
import android.telephony.TelephonyManager;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

final class Diagnostics {
    static final String HOST = "www.deheksenketel.com";
    static final int TIMEOUT = 3500;
    record Resolver(String name, String ip) {}

    static JSONObject run(Context context, Network network) throws Exception {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties links = cm.getLinkProperties(network);
        JSONObject info = new JSONObject();
        info.put("wifi", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        info.put("cellular", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        info.put("vpn", caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.put("private_dns_active", links != null && links.isPrivateDnsActive());
            info.put("private_dns_name", links == null ? JSONObject.NULL : links.getPrivateDnsServerName());
        }
        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            try { info.put("mobile_operator_hint", context.getSystemService(TelephonyManager.class).getNetworkOperatorName()); }
            catch (SecurityException ignored) { info.put("mobile_operator_hint", "Niet beschikbaar"); }
        }
        List<Resolver> resolvers = new ArrayList<>();
        if (links != null) for (InetAddress address : links.getDnsServers()) resolvers.add(new Resolver("DNS huidige aansluiting", address.getHostAddress()));
        resolvers.addAll(Arrays.asList(new Resolver("Proximus 1", "195.238.2.21"), new Resolver("Proximus 2", "195.238.2.22"),
            new Resolver("Telenet 1", "195.130.131.4"), new Resolver("Telenet 2", "195.130.130.4"),
            new Resolver("Cloudflare", "1.1.1.1"), new Resolver("Google", "8.8.8.8"), new Resolver("Quad9", "9.9.9.9")));
        JSONObject report = new JSONObject().put("schema", 1).put("id", UUID.randomUUID().toString())
            .put("target", HOST).put("timestamp", Instant.now().toString()).put("network", info);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<JSONObject>> jobs = new ArrayList<>();
            for (Resolver resolver : resolvers) for (int type : new int[]{1, 28}) jobs.add(pool.submit(() -> dns(network, resolver, type)));
            JSONArray answers = new JSONArray();
            for (Future<JSONObject> job : jobs) answers.put(job.get());
            report.put("dns", answers);
            JSONArray system = new JSONArray();
            try { for (InetAddress ip : network.getAllByName(HOST)) system.put(ip.getHostAddress()); }
            catch (Exception e) { report.put("system_dns_error", e.toString()); }
            report.put("system_addresses", system);
            // Een normale HTTPS-aanvraag gebruikt de resolver van het vastgelegde netwerk.
            // DNS-antwoorden van externe resolvers worden apart vastgelegd, niet als internetroute gebruikt.
            report.put("https", https(network));
            report.put("limitations", new JSONArray(Arrays.asList(
                "Geen automatische conclusie over blokkade of opzet.",
                "Externe providerresolvers kunnen uitsluitend eigen klanten toelaten; time-out/REFUSED bewijst geen blokkade.",
                "Alle verzoeken lopen via de huidige verbinding. Verschillende IP-adressen kunnen normaal CDN-gedrag zijn.",
                "UDP/TCP-DNS zonder DNSSEC-validatie; onderweg kan DNS worden gefilterd of onderschept.",
                "HTTPS gebruikt systeem-DNS van het vastgelegde netwerk, valideert certificaten en volgt redirects niet.",
                "Geen zoekmachinetest; geen vergelijking van volledige pagina-inhoud.",
                "Operatornaam is een aanwijzing en kan bij roaming, VPN en meerdere SIM-kaarten onvolledig zijn.",
                "Proximus/Telenet-adressen zijn een startlijst, geen volledige lijst van Belgische providers.")));
            return report;
        } finally { pool.shutdownNow(); }
    }

    static JSONObject https(Network network) throws Exception {
        JSONObject result = new JSONObject();
        HttpsURLConnection connection = null;
        long start = System.nanoTime();
        try {
            connection = (HttpsURLConnection) network.openConnection(new URL("https://" + HOST + "/"));
            connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Heksenketel-Netwerktest/1.0 Android");
            connection.setRequestProperty("Range", "bytes=0-4095");
            result.put("http_status", connection.getResponseCode());
            result.put("tls_verified", true);
            result.put("location", connection.getHeaderField("Location"));
        } catch (Exception e) { result.put("error", e.toString()); result.put("tls_verified", false); }
        finally { if (connection != null) connection.disconnect(); }
        return result.put("duration_ms", (System.nanoTime() - start) / 1_000_000);
    }

    static JSONObject dns(Network network, Resolver resolver, int type) throws Exception {
        JSONObject result = new JSONObject().put("resolver", resolver.name()).put("server", resolver.ip())
            .put("type", type == 1 ? "A" : "AAAA");
        JSONArray records = new JSONArray();
        long start = System.nanoTime();
        try {
            String current = HOST;
            Set<String> visited = new HashSet<>();
            for (int step = 0; step < 8; step++) {
                if (!visited.add(current)) throw new IOException("CNAME-lus");
                int id = new java.security.SecureRandom().nextInt(65536);
                byte[] question = question(id, current, type);
                byte[] data;
                try (DatagramSocket socket = new DatagramSocket()) {
                    network.bindSocket(socket); socket.setSoTimeout(TIMEOUT);
                    socket.connect(InetAddress.getByName(resolver.ip()), 53);
                    socket.send(new DatagramPacket(question, question.length));
                    DatagramPacket reply = new DatagramPacket(new byte[65535], 65535);
                    socket.receive(reply); data = Arrays.copyOf(reply.getData(), reply.getLength());
                }
                String transport = "UDP";
                if (data.length >= 4 && (data[2] & 2) != 0) {
                    try (Socket socket = network.getSocketFactory().createSocket()) {
                        socket.connect(new InetSocketAddress(InetAddress.getByName(resolver.ip()), 53), TIMEOUT);
                        socket.setSoTimeout(TIMEOUT);
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        out.writeShort(question.length); out.write(question); out.flush();
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        data = new byte[in.readUnsignedShort()]; in.readFully(data); transport = "TCP";
                    }
                }
                Reply reply = parse(data, id, current, type);
                for (JSONObject record : reply.records) records.put(record);
                result.put("status", reply.status).put("transport", transport);
                if (!reply.status.equals("NOERROR")) break;
                String canonical = current;
                Set<String> chain = new HashSet<>();
                boolean changed;
                do {
                    changed = false;
                    for (JSONObject record : reply.records) if (record.getInt("type") == 5 && record.getString("name").equals(canonical)) {
                        if (!chain.add(canonical)) throw new IOException("CNAME-lus");
                        canonical = record.getString("value"); changed = true; break;
                    }
                } while (changed);
                boolean found = false;
                for (JSONObject record : reply.records) if (record.getInt("type") == type && record.getString("name").equals(canonical)) found = true;
                if (found) break;
                if (canonical.equals(current)) { result.put("status", "NODATA"); break; }
                current = canonical;
                if (step == 7) throw new IOException("CNAME-keten te lang");
            }
        } catch (Exception e) { result.put("status", e instanceof SocketTimeoutException ? "TIMEOUT" : "ERROR").put("error", e.toString()); }
        return result.put("records", records).put("duration_ms", (System.nanoTime() - start) / 1_000_000);
    }

    static byte[] question(int id, String host, int type) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        for (int n : new int[]{id, 0x0100, 1, 0, 0, 0}) out.writeShort(n);
        for (String label : host.split("\\.")) { byte[] value = label.getBytes(StandardCharsets.US_ASCII); out.writeByte(value.length); out.write(value); }
        out.writeByte(0); out.writeShort(type); out.writeShort(1);
        return bytes.toByteArray();
    }

    record Reply(String status, List<JSONObject> records) {}
    static int u16(byte[] data, int offset) throws IOException {
        if (offset < 0 || offset + 2 > data.length) throw new IOException("Afgebroken DNS-antwoord");
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }
    static String name(byte[] data, int[] cursor) throws IOException {
        StringBuilder name = new StringBuilder(); Set<Integer> seen = new HashSet<>();
        int offset = cursor[0], end = -1;
        while (true) {
            if (offset >= data.length || !seen.add(offset)) throw new IOException("Ongeldige DNS-naam");
            int length = data[offset] & 255;
            if ((length & 192) == 192) {
                if (end < 0) end = offset + 2;
                offset = u16(data, offset) & 0x3fff;
            } else {
                if ((length & 192) != 0) throw new IOException("Ongeldig label");
                offset++;
                if (length == 0) { cursor[0] = end < 0 ? offset : end; return name.toString().toLowerCase(Locale.ROOT); }
                if (offset + length > data.length) throw new IOException("Afgebroken label");
                if (name.length() > 0) name.append('.');
                name.append(new String(data, offset, length, StandardCharsets.US_ASCII)); offset += length;
            }
        }
    }
    static Reply parse(byte[] data, int id, String host, int type) throws Exception {
        if (data.length < 12 || u16(data, 0) != id || (u16(data, 2) & 0xf800) != 0x8000 || u16(data, 4) != 1) throw new IOException("Onverwacht DNS-antwoord");
        int flags = u16(data, 2);
        if ((flags & 0x200) != 0) throw new IOException("DNS-antwoord ook via TCP afgekapt");
        int[] cursor = {12};
        if (!name(data, cursor).equals(host) || u16(data, cursor[0]) != type || u16(data, cursor[0] + 2) != 1) throw new IOException("Onverwachte DNS-vraag");
        cursor[0] += 4;
        List<JSONObject> records = new ArrayList<>();
        for (int i = 0; i < u16(data, 6); i++) {
            String owner = name(data, cursor); int pos = cursor[0];
            int recordType = u16(data, pos), cls = u16(data, pos + 2), size = u16(data, pos + 8);
            long ttl = ((long)u16(data, pos + 4) << 16) | u16(data, pos + 6);
            pos += 10;
            if (pos + size > data.length) throw new IOException("Afgebroken record");
            String value = null;
            if (cls == 1 && ((recordType == 1 && size == 4) || (recordType == 28 && size == 16))) value = InetAddress.getByAddress(Arrays.copyOfRange(data, pos, pos + size)).getHostAddress();
            if (cls == 1 && recordType == 5) value = name(data, new int[]{pos});
            if (value != null) records.add(new JSONObject().put("name", owner).put("type", recordType).put("value", value).put("ttl", ttl));
            cursor[0] = pos + size;
        }
        String status = switch (flags & 15) { case 0 -> "NOERROR"; case 1 -> "FORMERR"; case 2 -> "SERVFAIL"; case 3 -> "NXDOMAIN"; case 5 -> "REFUSED"; default -> "RCODE_" + (flags & 15); };
        return new Reply(status, records);
    }
}
