package yota.traffic;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.util.JsonReader;
import android.util.Log;

import org.jsoup.*;
import org.json.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

public class Updater {
    private Context context;

    private final int versionMajor = 0;
    private final int versionMinor = 5;

    private int serverVersionMajor = -1;
    private int serverVersionMinor = -1;

    private final String updateURL = "http://92.243.95.68/yota/yotaregulator.apk";
    private final String apkName = "yotaregulator.apk";
    private final String versionURL = "http://92.243.95.68/yota/version.json";
    private final String releaseNotesURL = "http://92.243.95.68/yota/?notes";

    private final String userAgent = "YOTA Регулятор";
    private final String TAG = "Updater";

    private boolean bWriteAccepted = false;

    public Updater(Context c) {
        this.context = c;
    }

    private void getServerVersion() {
        boolean bloaded = false;
        JSONObject json = new JSONObject();
        while (!bloaded) {
            try {
                Connection.Response response = Jsoup.connect(versionURL)
                        .userAgent(userAgent)
                        .header("X-App-Version", Integer.toString(versionMajor) + "." + Integer.toString(versionMinor))
                        .ignoreContentType(true)
                        .execute();

                bloaded = true;

                //json = null;
                Log.v(TAG, "[getServerVersion] Response: " + response.body());

                json = new JSONObject(response.body());
            }catch (SocketTimeoutException e) {
                e.printStackTrace();

                bloaded = false;
            }catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            serverVersionMajor = json.getInt("major");
            serverVersionMinor = json.getInt("minor");

            Log.v(TAG, "[" + TAG + "] Got server response. Major version is " + json.getInt("major") + ", minor version is " + json.getInt("minor"));
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getUpdateNotes(int majorVersion_, int minorVersion_) {
        boolean bloaded = false;
        while (!bloaded) {
            try {
                Connection.Response response = Jsoup.connect(releaseNotesURL)
                        .userAgent(userAgent)
                        .header("X-Notes-Version", Integer.toString(majorVersion_) + "." + Integer.toString(minorVersion_))
                        .header("X-App-Version", Integer.toString(versionMajor) + "." + Integer.toString(versionMinor))
                        .ignoreContentType(true)
                        .execute();

                bloaded = true;

                return response.body();
            }catch (SocketTimeoutException e) {
                e.printStackTrace();

                bloaded = false;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private void downloadUpdate() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateURL));
        context.startActivity(intent);
        /*
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(updateURL));
        request.setDescription("Скачивание обновления...");
        request.setTitle("Обновление");
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName);

        //  Теперь можно скачать обновление по URL, указанному в классе.
        DownloadManager manager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);
        */
    }

    @Deprecated
    private void downloadUpdate_obsolete() {
        //  Сначала получаем версию, которая на сервере.
        if (serverVersionMajor == -1 || serverVersionMinor == -1)
            getServerVersion();

        //  Теперь можно скачать обновление по URL, указанному в классе.
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(updateURL).openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException(connection.getResponseMessage());

            int contentLength = connection.getContentLength();
            input = connection.getInputStream();
            output = new FileOutputStream(Environment.getExternalStorageDirectory().getPath() + apkName);

            byte data[] = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1)
                output.write(data, 0, count);
        }catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            }catch (IOException e) {
                e.printStackTrace();
            }

            if (connection != null)
                connection.disconnect();
        }
    }

    public void Update() {
        getServerVersion();

        if (versionMinor >= serverVersionMinor)
            //  Обновление не требуется.
            return;

        //  TODO: оформить диалог с заметками к обновлению.
        String message = "Доступна новая версия приложения: " + serverVersionMajor + "." + serverVersionMinor;
        message += "\nВ новой версии:\n";
        message += getUpdateNotes(serverVersionMajor, serverVersionMinor);
        final android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context);
        builder.setTitle("Доступно обновление!");
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("Обновить", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        downloadUpdate();
                    }
                }).start();
            }
        });
        builder.setNegativeButton("Позже", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.create().show();
            }
        });
    }
}
