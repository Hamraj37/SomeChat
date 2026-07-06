package com.samechat37.utils;

import android.util.Base64;
import com.samechat37.BuildConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class GitHubStorage {

    private static final String OWNER = "Hamraj37";
    private static final String REPO = "same_chat_storage";
    private static final String TOKEN = BuildConfig.GITHUB_TOKEN;
    private static final OkHttpClient client = new OkHttpClient();

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onProgress(int progress);
        void onFailure(Exception e);
    }

    public static void uploadFile(String localPath, String remotePath, String fileName, UploadCallback callback) {
        File file = new File(localPath);
        if (!file.exists()) {
            callback.onFailure(new IOException("File not found"));
            return;
        }

        try {
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(bytes);
            fis.close();
            
            uploadBytes(bytes, remotePath, fileName, callback);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    public static void uploadBytes(byte[] bytes, String remotePath, String fileName, UploadCallback callback) {
        try {
            String encodedContent = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String url = String.format("https://api.github.com/repos/%s/%s/contents/%s/%s", OWNER, REPO, remotePath, fileName);

            JSONObject json = new JSONObject();
            json.put("message", "Upload file: " + fileName);
            json.put("content", encodedContent);

            RequestBody body = new ProgressRequestBody(
                    RequestBody.create(json.toString(), MediaType.parse("application/json")),
                    callback
            );

            Request request = new Request.Builder()
                    .url(url)
                    .put(body)
                    .addHeader("Authorization", "Bearer " + TOKEN)
                    .addHeader("Accept", "application/vnd.github+json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String downloadUrl = String.format("https://raw.githubusercontent.com/%s/%s/main/%s/%s", OWNER, REPO, remotePath, fileName);
                        callback.onSuccess(downloadUrl);
                    } else {
                        callback.onFailure(new IOException("GitHub upload failed: " + response.code() + " " + response.message()));
                    }
                    response.close();
                }
            });

        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    public interface DownloadCallback {
        void onSuccess(byte[] data);
        void onProgress(int progress);
        void onFailure(Exception e);
    }

    public static void downloadFile(String url, DownloadCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Download failed: " + response.code()));
                    response.close();
                    return;
                }

                try (Response res = response) {
                    long totalBytes = res.body().contentLength();
                    byte[] data = new byte[(int) totalBytes];
                    
                    try (java.io.InputStream is = res.body().byteStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            System.arraycopy(buffer, 0, data, (int) totalRead, bytesRead);
                            totalRead += bytesRead;
                            if (totalBytes > 0) {
                                callback.onProgress((int) ((totalRead * 100) / totalBytes));
                            }
                        }
                        callback.onSuccess(data);
                    }
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    public static void downloadFile(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(callback);
    }

    private static class ProgressRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final UploadCallback callback;

        public ProgressRequestBody(RequestBody delegate, UploadCallback callback) {
            this.delegate = delegate;
            this.callback = callback;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            BufferedSink bufferedSink = Okio.buffer(new ForwardingSink(sink) {
                private long bytesWritten = 0L;
                private long totalBytes = 0L;

                @Override
                public void write(Buffer source, long byteCount) throws IOException {
                    super.write(source, byteCount);
                    if (totalBytes == 0) {
                        totalBytes = contentLength();
                    }
                    bytesWritten += byteCount;
                    callback.onProgress((int) ((bytesWritten * 100) / totalBytes));
                }
            });
            delegate.writeTo(bufferedSink);
            bufferedSink.flush();
        }
    }
}
