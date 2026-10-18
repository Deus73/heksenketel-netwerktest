package com.deheksenketel.netwerktest;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.IBinder;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;

public class TestService extends Service {
    private static final String REPORT_ENDPOINT = "https://www.deheksenketel.com/dns-diagnose/report.php";
    static volatile boolean running;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    static boolean hasPending(Context context) {
        File[] files = new File(context.getFilesDir(), "pending").listFiles((dir, name) -> name.endsWith(".json"));
        return files != null && files.length > 0;
    }
    private void status(String value) { getSharedPreferences("test", MODE_PRIVATE).edit().putString("status", value).apply(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("diagnostic", "Netwerktest", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "diagnostic").setSmallIcon(com.deheksenketel.netwerktest.R.drawable.ic_test)
            .setContentTitle("Heksenketel Netwerktest").setContentText("Verbinding onderzoeken / rapport versturen")
            .setContentIntent(open).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        running = true;
        boolean retry = intent != null && intent.getBooleanExtra("retry", false);
        worker.execute(() -> {
            try {
                ConnectivityManager cm = getSystemService(ConnectivityManager.class);
                Network network = cm.getActiveNetwork();
                if (network == null) throw new IOException("Geen actieve internetverbinding. Verbind met wifi of mobiele data.");
                if (!retry) {
                    status("DNS en HTTPS onderzoeken… Dit duurt meestal minder dan een minuut.");
                    JSONObject report = Diagnostics.run(this, network);
                    byte[] bytes = report.toString(2).getBytes(StandardCharsets.UTF_8);
                    File pending = new File(getFilesDir(), "pending");
                    if (!pending.isDirectory() && !pending.mkdirs()) throw new IOException("Rapportmap kan niet worden gemaakt");
                    atomicWrite(new File(pending, report.getString("id") + ".json"), bytes);
                    atomicWrite(new File(getFilesDir(), "latest.json"), bytes);
                }
                File[] files = new File(getFilesDir(), "pending").listFiles((dir, name) -> name.endsWith(".json"));
                int accepted = 0;
                if (files != null) for (File file : files) {
                    boolean success = false; Exception failure = null;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        status("Rapport automatisch aanbieden voor e-mail… poging " + (attempt + 1));
                        try { upload(network, REPORT_ENDPOINT, Files.readAllBytes(file.toPath())); success = true; break; }
                        catch (Exception e) { failure = e; if (attempt < 2) Thread.sleep((attempt + 1) * 3000L); }
                    }
                    if (!success) throw new IOException("Rapport lokaal bewaard. Versturen mislukt: " + failure.getMessage());
                    if (!file.delete()) throw new IOException("Aangenomen voor verzending, maar lokaal wachtrijbestand kon niet worden verwijderd");
                    accepted++;
                }
                status(accepted + " rapport(en) door de server aangenomen voor verzending naar info@deheksenketel.com. Dit bevestigt nog niet de aflevering in de mailbox.\n\nLaatste rapport is ook lokaal beschikbaar via de deelknop.");
            } catch (Exception e) { status(e.getMessage() + "\n\nControleer je internetverbinding. Blijft verzenden mislukken, neem dan contact op met De Heksenketel. Gebruik ‘Opgeslagen rapporten opnieuw versturen’ om het opnieuw te proberen."); }
            finally { running = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        });
        return START_NOT_STICKY;
    }
    static void atomicWrite(File target, byte[] bytes) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.write(temp.toPath(), bytes);
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private void upload(Network network, String endpoint, byte[] bytes) throws Exception {
        URL url = new URL(endpoint);
        if (!url.getProtocol().equals("https") || url.getUserInfo() != null) throw new IOException("HTTPS-ontvangstpunt vereist");
        HttpsURLConnection connection = (HttpsURLConnection) network.openConnection(url);
        try {
            connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Ontvangstpunt antwoordt HTTP " + code);
            byte[] response;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] part = new byte[1024]; int size;
                while ((size = in.read(part)) != -1) {
                    if (buffer.size() + size > 8192) throw new IOException("Antwoord ontvangstpunt te groot");
                    buffer.write(part, 0, size);
                }
                response = buffer.toByteArray();
            }
            JSONObject result = new JSONObject(new String(response, StandardCharsets.UTF_8));
            if (!result.optBoolean("accepted", false)) throw new IOException("Geen geldige verzendbevestiging ontvangen");
        } finally { connection.disconnect(); }
    }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
