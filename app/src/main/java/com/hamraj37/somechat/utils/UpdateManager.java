package com.hamraj37.somechat.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hamraj37.somechat.BuildConfig;
import com.hamraj37.somechat.R;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {

    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/Hamraj37/SameChat/releases/latest";
    private static final String TOKEN = BuildConfig.GITHUB_TOKEN;
    private static final OkHttpClient client = new OkHttpClient();

    public static void checkForUpdates(Activity activity) {
        Request.Builder builder = new Request.Builder().url(GITHUB_RELEASES_URL);
        if (TOKEN != null && !TOKEN.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + TOKEN);
        }
        Request request = builder.build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Ignore failure for update check
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonData = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonData);
                        String latestVersion = jsonObject.getString("tag_name").replace("v", "");
                        String currentVersion = BuildConfig.VERSION_NAME;

                        if (isNewerVersion(currentVersion, latestVersion)) {
                            String downloadUrl = jsonObject.getString("html_url");
                            String changelog = jsonObject.optString("body", "No changelog available.");
                            
                            activity.runOnUiThread(() -> showUpdateDialog(activity, latestVersion, changelog, downloadUrl));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static boolean isNewerVersion(String current, String latest) {
        try {
            String[] currParts = current.split("\\.");
            String[] lateParts = latest.split("\\.");
            int length = Math.max(currParts.length, lateParts.length);
            for (int i = 0; i < length; i++) {
                int curr = i < currParts.length ? Integer.parseInt(currParts[i]) : 0;
                int late = i < lateParts.length ? Integer.parseInt(lateParts[i]) : 0;
                if (late > curr) return true;
                if (curr > late) return false;
            }
        } catch (Exception e) {
            return !current.equals(latest);
        }
        return false;
    }

    private static void showUpdateDialog(Activity activity, String version, String changelog, String downloadUrl) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);
        TextView tvVersion = dialogView.findViewById(R.id.tv_update_version);
        TextView tvChangelog = dialogView.findViewById(R.id.tv_update_changelog);

        String title = activity.getString(R.string.update_available);
        tvVersion.setText(title + " (v" + version + ")");
        tvChangelog.setText(changelog);

        new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setPositiveButton(R.string.update_now, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    activity.startActivity(intent);
                })
                .setNegativeButton(R.string.later, null)
                .show();
    }
}
