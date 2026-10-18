package com.deheksenketel.netwerktest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button start, retry;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            var prefs = getSharedPreferences("test", MODE_PRIVATE);
            status.setText(prefs.getString("status", "Klaar om je verbinding te testen."));
            start.setEnabled(!TestService.running);
            retry.setEnabled(!TestService.running && TestService.hasPending(MainActivity.this));
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getSharedPreferences("test", MODE_PRIVATE).edit().remove("endpoint").apply();
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);
        TextView title = text("Heksenketel\nNetwerktest · v" + BuildConfig.VERSION_NAME, 28);
        title.setTextColor(Color.rgb(18, 117, 99));
        layout.addView(title);
        layout.addView(text("Test www.deheksenketel.com via je huidige verbinding. De app vergelijkt systeem-DNS, Belgische provider-DNS en onafhankelijke DNS-diensten.", 17));
        layout.addView(text("Na de test wordt het technische rapport automatisch aangeboden voor verzending naar info@deheksenketel.com. Het bevat tijdstip, netwerksoort, beschikbare operatornaam, DNS-antwoorden en HTTPS-fouten. Geen locatie, contacten, telefoonnummer of browsegeschiedenis. Het ontvangstpunt ziet wel je internet-IP.", 15));
        start = new Button(this);
        start.setText("Test verbinding en verstuur rapport");
        start.setOnClickListener(v -> launch(false));
        layout.addView(start);
        retry = new Button(this);
        retry.setText("Opgeslagen rapporten opnieuw versturen");
        retry.setOnClickListener(v -> launch(true));
        layout.addView(retry);
        Button share = new Button(this);
        share.setText("Laatste rapport delen als tekst");
        share.setOnClickListener(v -> {
            try {
                String report = new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(), "latest.json").toPath()), java.nio.charset.StandardCharsets.UTF_8);
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Netwerktest De Heksenketel")
                    .putExtra(Intent.EXTRA_TEXT, report), "Rapport delen"));
            } catch (Exception e) { Toast.makeText(this, "Er is nog geen rapport.", Toast.LENGTH_SHORT).show(); }
        });
        layout.addView(share);
        status = text("", 16);
        status.setTextIsSelectable(true);
        layout.addView(status);
        layout.addView(text("Verschillende IP-adressen of een time-out bewijzen geen blokkade. Provider-DNS kan uitsluitend voor eigen klanten werken. Gebruik de app ook via een mobiele hotspot van een andere provider om te vergelijken. Een VPN of beveiligde DNS kan je browserresultaten beïnvloeden.", 14));
        setContentView(scroll);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setPadding(0, 12, 0, 12);
        return view;
    }

    private void launch(boolean retryOnly) {
        try {
            startForegroundService(new Intent(this, TestService.class).putExtra("retry", retryOnly));
            start.setEnabled(false); retry.setEnabled(false);
        } catch (Exception e) { Toast.makeText(this, "De test kon niet starten. Probeer het opnieuw.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
