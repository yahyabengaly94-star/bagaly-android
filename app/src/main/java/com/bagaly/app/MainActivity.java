package com.bagaly.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Bagaly : l'application Android.
 * Affiche Bagaly (servi par Termux sur http://localhost:8080) en plein écran, démarre le service
 * tout seul via Termux, et gère le micro (« Hey Bagaly »), les téléchargements et les liens externes.
 */
public class MainActivity extends Activity {
    private static final String HOME = "http://localhost:8080/";
    private static final String TERMUX = "com.termux";
    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private PermissionRequest pendingMic;
    private int tries = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String h = u.getHost() == null ? "" : u.getHost();
                if (("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && (h.equals("localhost") || h.equals("127.0.0.1"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException e) { toast("Aucune application pour ouvrir ce lien"); }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                ui.post(() -> {
                    String o = req.getOrigin() == null ? "" : req.getOrigin().getHost();
                    if (!"localhost".equals(o) && !"127.0.0.1".equals(o)) { req.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) req.grant(req.getResources());
                    else { pendingMic = req; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7); }
                });
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, cd, mime);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                toast("Téléchargement : " + name);
            } catch (Exception e) { toast("Téléchargement impossible"); }
        });
        String start = HOME;
        Intent i = getIntent();
        if (i != null && i.getData() != null && "localhost".equals(i.getData().getHost())) start = i.getData().toString();
        boot(start);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i != null && i.getData() != null && "localhost".equals(i.getData().getHost())) web.loadUrl(i.getData().toString());
    }

    /** Charge Bagaly ; si le service ne répond pas, demande à Termux de le démarrer puis réessaie. */
    private void boot(String url) {
        new Thread(() -> {
            boolean ok = alive(HOME);
            if (!ok && tries == 0) ui.post(this::startTermux);
            ui.post(() -> {
                if (ok) web.loadUrl(url);
                else if (tries++ < 20) { if (tries == 1) web.loadData(waitPage(), "text/html; charset=utf-8", null); ui.postDelayed(() -> boot(url), 1500); }
                else web.loadData(helpPage(), "text/html; charset=utf-8", null);
            });
        }).start();
    }

    private boolean alive(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(800); c.setReadTimeout(800);
            int code = c.getResponseCode(); c.disconnect();
            return code > 0;
        } catch (Exception e) { return false; }
    }

    private void startTermux() {
        try { getPackageManager().getPackageInfo(TERMUX, 0); } catch (PackageManager.NameNotFoundException e) { return; }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 8);
        }
        try {
            Intent i = new Intent();
            i.setClassName(TERMUX, "com.termux.app.RunCommandService");
            i.setAction("com.termux.RUN_COMMAND");
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"/data/data/com.termux/files/home/bagaly-start.sh"});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            toast("Ouvre Termux et tape : bash ~/bagaly-start.sh");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == 7 && pendingMic != null) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) pendingMic.grant(pendingMic.getResources()); else pendingMic.deny();
            pendingMic = null;
        }
        if (code == 8 && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) { tries = 0; startTermux(); }
    }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }

    private String waitPage() {
        return "<html><body style='background:#0f0f1a;color:#eee;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:90vh;text-align:center'>"
             + "<div><h2 style='color:#a29bfe'>Bagaly</h2><p>Démarrage du cerveau local…</p></div></body></html>";
    }

    private String helpPage() {
        return "<html><body style='background:#0f0f1a;color:#eee;font-family:sans-serif;padding:20px;line-height:1.5'>"
             + "<h2 style='color:#a29bfe'>Bagaly ne répond pas</h2>"
             + "<p>L'application affiche Bagaly, mais son cerveau tourne dans <b>Termux</b>.</p><ol>"
             + "<li>Installe <b>Termux</b> et <b>Termux:API</b> depuis F-Droid.</li>"
             + "<li>Dans Termux : <code>cd ~/storage/downloads/Bagaly &amp;&amp; bash install.sh</code></li>"
             + "<li>Puis : <code>bash ~/bagaly-start.sh</code> et choisis <b>Bagaly</b> pour ouvrir le lien.</li>"
             + "<li>Réglages Android → Applications → Bagaly → Autorisations → <b>Exécuter des commandes dans Termux</b>.</li></ol>"
             + "<p><a style='color:#74b9ff' href='" + HOME + "'>Réessayer</a></p></body></html>";
    }
}
