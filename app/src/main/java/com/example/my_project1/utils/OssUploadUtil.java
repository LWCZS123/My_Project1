package com.example.my_project1.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OSS上传工具类（优化版 - 返回objectKey）
 */
public class OssUploadUtil {

    private static final String TAG = "OssUploadUtil";
    private static final String STS_SERVER_URL = "https://oss-handler-uiwdwteceb.cn-hangzhou.fcapp.run/oss/token";
    private static final String SIGN_SERVER_URL = "https://oss-handler-uiwdwteceb.cn-hangzhou.fcapp.run/oss/sign";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 1000;

    /**
     * 上传场景枚举
     */
    public enum UploadScene {
        AVATAR("avatar"),
        BACKGROUND("background"),
        BILL("bill"),
        CATEGORY("category"),
        VIDEO("video"),
        DOCUMENT("document");

        private final String value;

        UploadScene(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 文件类型枚举
     */
    public enum FileType {
        IMAGE,
        VIDEO,
        DOCUMENT
    }

    /**
     * 上传结果实体类（包含objectKey）
     */
    public static class UploadResult {
        public final String objectKey;  // OSS对象键
        public final String fileUrl;    // 临时访问URL（可选）

        public UploadResult(String objectKey, String fileUrl) {
            this.objectKey = objectKey;
            this.fileUrl = fileUrl;
        }
    }

    /**
     * 上传文件到OSS（统一入口）
     */
    public static void uploadFile(Context context, Uri fileUri, String userId,
                                  UploadScene scene, UploadCallback callback) {
        FileType fileType = detectFileType(context, fileUri);

        if (fileType == FileType.IMAGE) {
            uploadImageWithCompression(context, fileUri, userId, scene, callback);
        } else {
            uploadOriginalFile(context, fileUri, userId, scene, fileType, callback);
        }
    }

    /**
     * 上传图片（带智能压缩）
     */
    private static void uploadImageWithCompression(Context context, Uri imageUri, String userId,
                                                   UploadScene scene, UploadCallback callback) {
        AppExecutors.get().computation().execute(() -> {
            File compressedFile = null;
            try {
                compressedFile = ImageCompressUtil.compressImage(context, imageUri);

                if (compressedFile == null || !compressedFile.exists()) {
                    throw new IOException("图片压缩失败：文件不存在");
                }

                File finalFile = compressedFile;
                Log.d(TAG, "压缩成功，文件路径: " + finalFile.getAbsolutePath());

                AppExecutors.get().networkIO().execute(() -> {
                    performUploadWithRetry(context, finalFile, userId, scene,
                            FileType.IMAGE, callback, true);
                });

            } catch (Exception e) {
                Log.e(TAG, "图片压缩失败", e);
                if (compressedFile != null && compressedFile.exists()) {
                    compressedFile.delete();
                }
                notifyFailureOnMainThread(callback, e);
            }
        });
    }

    /**
     * 上传原始文件（视频、文档等不压缩）
     */
    private static void uploadOriginalFile(Context context, Uri fileUri, String userId,
                                           UploadScene scene, FileType fileType, UploadCallback callback) {
        AppExecutors.get().diskIO().execute(() -> {
            File tempFile = null;
            try {
                tempFile = copyUriToTempFile(context, fileUri, fileType);

                if (tempFile == null || !tempFile.exists()) {
                    throw new IOException("文件复制失败：文件不存在");
                }

                File finalFile = tempFile;
                Log.d(TAG, "文件准备成功，路径: " + finalFile.getAbsolutePath());

                AppExecutors.get().networkIO().execute(() -> {
                    performUploadWithRetry(context, finalFile, userId, scene,
                            fileType, callback, true);
                });

            } catch (Exception e) {
                Log.e(TAG, "文件准备失败", e);
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                notifyFailureOnMainThread(callback, e);
            }
        });
    }

    /**
     * 批量上传文件
     */
    public static void uploadFiles(Context context, List<Uri> fileUris, String userId,
                                   UploadScene scene, BatchUploadCallback callback) {
        AppExecutors.get().computation().execute(() -> {
            List<ProcessedFile> processedFiles = new ArrayList<>();
            int total = fileUris.size();

            try {
                // 1. 预处理所有文件
                for (int i = 0; i < fileUris.size(); i++) {
                    try {
                        Uri fileUri = fileUris.get(i);
                        FileType fileType = detectFileType(context, fileUri);
                        File processedFile;

                        if (fileType == FileType.IMAGE) {
                            processedFile = ImageCompressUtil.compressImage(context, fileUri);
                        } else {
                            processedFile = copyUriToTempFile(context, fileUri, fileType);
                        }

                        if (processedFile != null && processedFile.exists()) {
                            processedFiles.add(new ProcessedFile(processedFile, fileType));
                            Log.d(TAG, "预处理成功 [" + (i + 1) + "/" + total + "]: " +
                                    processedFile.getAbsolutePath());
                        }

                        int currentIndex = i + 1;
                        int progress = (currentIndex * 30) / total;
                        notifyProgressOnMainThread(callback, currentIndex, total, progress);

                    } catch (Exception e) {
                        Log.e(TAG, "预处理第" + (i + 1) + "个文件失败", e);
                    }
                }

                if (processedFiles.isEmpty()) {
                    throw new Exception("没有可上传的文件");
                }

                // 2. 批量上传
                AppExecutors.get().networkIO().execute(() -> {
                    List<UploadResult> uploadResults = new ArrayList<>();

                    try {
                        StsToken stsToken = fetchStsTokenWithRetry(userId, scene.getValue());
                        OSS ossClient = createOssClient(context, stsToken);

                        for (int i = 0; i < processedFiles.size(); i++) {
                            ProcessedFile pf = processedFiles.get(i);

                            try {
                                if (!pf.file.exists()) {
                                    Log.e(TAG, "文件不存在，跳过: " + pf.file.getAbsolutePath());
                                    continue;
                                }

                                String fileName = generateFileName(pf.file.getName(), pf.fileType);
                                String objectKey = stsToken.dir + fileName;

                                Log.d(TAG, "开始上传 [" + (i + 1) + "/" + processedFiles.size() +
                                        "]: " + pf.file.getAbsolutePath());

                                PutObjectRequest request = new PutObjectRequest(
                                        stsToken.bucket, objectKey, pf.file.getAbsolutePath());

                                request.setProgressCallback((request1, currentSize, totalSize) -> {
                                    int fileProgress = (int) ((currentSize * 100) / totalSize);
                                    Log.d(TAG, "文件上传进度: " + fileProgress + "%");
                                });

                                PutObjectResult result = ossClient.putObject(request);

                                // 保存objectKey（用于存储到Bmob）
                                uploadResults.add(new UploadResult(objectKey, null));
                                Log.d(TAG, "上传成功，objectKey: " + objectKey);

                                int currentIndex = i + 1;
                                int progress = 30 + (currentIndex * 70) / processedFiles.size();
                                notifyProgressOnMainThread(callback, currentIndex, total, progress);

                            } catch (Exception e) {
                                Log.e(TAG, "上传第" + (i + 1) + "个文件失败", e);
                            }
                        }

                        notifyBatchSuccessOnMainThread(callback, uploadResults);

                    } catch (Exception e) {
                        Log.e(TAG, "批量上传失败", e);
                        notifyBatchFailureOnMainThread(callback, e);
                    } finally {
                        cleanupFiles(processedFiles);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "批量预处理失败", e);
                cleanupFiles(processedFiles);
                notifyBatchFailureOnMainThread(callback, e);
            }
        });
    }

    /**
     * 根据objectKey列表获取临时访问URL
     */
    public static void getSignedUrls(List<String> objectKeys, SignUrlCallback callback) {
        AppExecutors.get().networkIO().execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                JSONObject jsonBody = new JSONObject();
                JSONArray keysArray = new JSONArray();
                for (String key : objectKeys) {
                    keysArray.put(key);
                }
                jsonBody.put("keys", keysArray);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(SIGN_SERVER_URL)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("获取签名URL失败: HTTP " + response.code());
                    }

                    if (response.body() == null) {
                        throw new Exception("响应体为空");
                    }

                    String json = response.body().string();
                    JSONObject obj = new JSONObject(json);
                    JSONArray urlsArray = obj.getJSONArray("urls");

                    List<String> signedUrls = new ArrayList<>();
                    for (int i = 0; i < urlsArray.length(); i++) {
                        signedUrls.add(urlsArray.getString(i));
                    }

                    notifySignUrlSuccessOnMainThread(callback, signedUrls);
                }

            } catch (Exception e) {
                Log.e(TAG, "获取签名URL失败", e);
                notifySignUrlFailureOnMainThread(callback, e);
            }
        });
    }

    /**
     * 执行上传（带重试机制）- 返回objectKey
     */
    private static void performUploadWithRetry(Context context, File file, String userId,
                                               UploadScene scene, FileType fileType,
                                               UploadCallback callback, boolean shouldCleanup) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                if (!file.exists()) {
                    throw new IOException("文件不存在: " + file.getAbsolutePath());
                }

                Log.d(TAG, "开始上传（第 " + (retryCount + 1) + " 次尝试）: " +
                        file.getAbsolutePath() + ", 文件大小: " + file.length() + " bytes");

                // 1. 获取 STS 凭证
                StsToken stsToken = fetchStsTokenWithRetry(userId, scene.getValue());

                // 2. 生成文件名和objectKey
                String fileName = generateFileName(file.getName(), fileType);
                String objectKey = stsToken.dir + fileName;

                // 3. 创建 OSS 客户端
                OSS ossClient = createOssClient(context, stsToken);

                // 4. 上传文件
                PutObjectRequest request = new PutObjectRequest(
                        stsToken.bucket, objectKey, file.getAbsolutePath());

                request.setProgressCallback((request1, currentSize, totalSize) -> {
                    int progress = (int) ((currentSize * 100) / totalSize);
                    Log.d(TAG, "上传进度: " + progress + "% (" + currentSize + "/" + totalSize + ")");
                });

                PutObjectResult result = ossClient.putObject(request);

                Log.d(TAG, "上传成功，objectKey: " + objectKey);

                // 5. 清理临时文件
                if (shouldCleanup) {
                    AppExecutors.get().diskIO().execute(() -> {
                        if (file.exists()) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "清理临时文件: " + file.getAbsolutePath() +
                                    ", 删除" + (deleted ? "成功" : "失败"));
                        }
                    });
                }

                // 6. 返回objectKey（不返回URL，由前端调用getSignedUrls获取）
                UploadResult uploadResult = new UploadResult(objectKey, null);
                notifySuccessOnMainThread(callback, uploadResult);
                return;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                Log.w(TAG, "上传失败，第 " + retryCount + " 次重试", e);

                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Log.e(TAG, "上传失败，已重试 " + MAX_RETRY_COUNT + " 次", lastException);
        if (shouldCleanup) {
            AppExecutors.get().diskIO().execute(() -> {
                if (file.exists()) {
                    file.delete();
                }
            });
        }
        notifyFailureOnMainThread(callback, lastException);
    }

    /**
     * 从服务器获取 STS 临时凭证（带重试）
     */
    private static StsToken fetchStsTokenWithRetry(String userId, String scene) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                return fetchStsToken(userId, scene);
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                Log.w(TAG, "获取 STS 凭证失败，第 " + retryCount + " 次重试", e);

                if (retryCount < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new Exception("获取 STS 凭证失败，已重试 " + MAX_RETRY_COUNT + " 次", lastException);
    }

    /**
     * 从服务器获取 STS 临时凭证
     */
    private static StsToken fetchStsToken(String userId, String scene) throws Exception {
        OkHttpClient client = new OkHttpClient();
        String url = STS_SERVER_URL + "?userId=" + userId + "&scene=" + scene;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("获取 STS 凭证失败: HTTP " + response.code());
            }

            if (response.body() == null) {
                throw new Exception("STS 响应体为空");
            }

            String json = response.body().string();
            JSONObject obj = new JSONObject(json);

            return new StsToken(
                    obj.getString("AccessKeyId"),
                    obj.getString("AccessKeySecret"),
                    obj.getString("SecurityToken"),
                    obj.getString("Bucket"),
                    obj.getString("Dir"),
                    obj.getString("Endpoint"),
                    obj.getString("Host")
            );
        }
    }

    /**
     * 创建 OSS 客户端
     */
    private static OSS createOssClient(Context context, StsToken token) {
        OSSFederationCredentialProvider provider = new OSSFederationCredentialProvider() {
            @Override
            public OSSFederationToken getFederationToken() {
                return new OSSFederationToken(
                        token.accessKeyId,
                        token.accessKeySecret,
                        token.securityToken,
                        System.currentTimeMillis() + 3600 * 1000
                );
            }
        };

        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000);
        conf.setSocketTimeout(15 * 1000);
        conf.setMaxConcurrentRequest(5);
        conf.setMaxErrorRetry(2);

        return new OSSClient(context.getApplicationContext(), token.endpoint, provider, conf);
    }

    /**
     * 检测文件类型
     */
    private static FileType detectFileType(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }

        if (mimeType != null) {
            if (mimeType.startsWith("image/")) {
                return FileType.IMAGE;
            } else if (mimeType.startsWith("video/")) {
                return FileType.VIDEO;
            }
        }

        return FileType.DOCUMENT;
    }

    /**
     * 将 URI 复制到临时文件
     */
    private static File copyUriToTempFile(Context context, Uri uri, FileType fileType) throws IOException {
        String extension = getFileExtension(context, uri);
        File tempFile = File.createTempFile("upload_", extension, context.getCacheDir());

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {

            if (inputStream == null) {
                throw new IOException("无法打开文件输入流");
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            Log.d(TAG, "文件复制成功: " + tempFile.getAbsolutePath() +
                    ", 大小: " + totalBytes + " bytes");
        } catch (IOException e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw e;
        }

        return tempFile;
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                return "." + extension;
            }
        }

        String path = uri.getPath();
        if (path != null && path.contains(".")) {
            return path.substring(path.lastIndexOf("."));
        }

        return ".dat";
    }

    /**
     * 生成文件名（根据文件类型）
     */
    private static String generateFileName(String originalName, FileType fileType) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String ext = getExtensionByFileType(originalName, fileType);
        return timeStamp + "_" + System.currentTimeMillis() + ext;
    }

    /**
     * 根据文件类型获取扩展名
     */
    private static String getExtensionByFileType(String originalName, FileType fileType) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf("."));
        }

        switch (fileType) {
            case IMAGE:
                return ".jpg";
            case VIDEO:
                return ".mp4";
            case DOCUMENT:
                return ".pdf";
            default:
                return ".dat";
        }
    }

    /**
     * 清理临时文件
     */
    private static void cleanupFiles(List<ProcessedFile> files) {
        AppExecutors.get().diskIO().execute(() -> {
            for (ProcessedFile pf : files) {
                if (pf.file != null && pf.file.exists()) {
                    boolean deleted = pf.file.delete();
                    Log.d(TAG, "清理文件: " + pf.file.getAbsolutePath() +
                            ", " + (deleted ? "成功" : "失败"));
                }
            }
        });
    }

    // ========== 主线程回调方法 ==========

    private static void notifySuccessOnMainThread(UploadCallback callback, UploadResult result) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onSuccess(result));
        }
    }

    private static void notifyFailureOnMainThread(UploadCallback callback, Exception e) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onFailure(e));
        }
    }

    private static void notifyProgressOnMainThread(BatchUploadCallback callback,
                                                   int current, int total, int progress) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() ->
                    callback.onProgress(current, total, progress));
        }
    }

    private static void notifyBatchSuccessOnMainThread(BatchUploadCallback callback, List<UploadResult> results) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onSuccess(results));
        }
    }

    private static void notifyBatchFailureOnMainThread(BatchUploadCallback callback, Exception e) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onFailure(e));
        }
    }

    private static void notifySignUrlSuccessOnMainThread(SignUrlCallback callback, List<String> urls) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onSuccess(urls));
        }
    }

    private static void notifySignUrlFailureOnMainThread(SignUrlCallback callback, Exception e) {
        if (callback != null) {
            AppExecutors.get().mainThread().execute(() -> callback.onFailure(e));
        }
    }

    // ========== 内部类 ==========

    private static class StsToken {
        String accessKeyId;
        String accessKeySecret;
        String securityToken;
        String bucket;
        String dir;
        String endpoint;
        String host;

        StsToken(String accessKeyId, String accessKeySecret, String securityToken,
                 String bucket, String dir, String endpoint, String host) {
            this.accessKeyId = accessKeyId;
            this.accessKeySecret = accessKeySecret;
            this.securityToken = securityToken;
            this.bucket = bucket;
            this.dir = dir;
            this.endpoint = endpoint;
            this.host = host;
        }
    }

    private static class ProcessedFile {
        File file;
        FileType fileType;

        ProcessedFile(File file, FileType fileType) {
            this.file = file;
            this.fileType = fileType;
        }
    }

    // ========== 回调接口 ==========

    /**
     * 单文件上传回调接口
     */
    public interface UploadCallback {
        void onSuccess(UploadResult result);
        void onFailure(Exception e);
    }

    /**
     * 批量上传回调接口
     */
    public interface BatchUploadCallback {
        void onProgress(int current, int total, int percentage);
        void onSuccess(List<UploadResult> results);
        void onFailure(Exception e);
    }

    /**
     * 签名URL回调接口
     */
    public interface SignUrlCallback {
        void onSuccess(List<String> signedUrls);
        void onFailure(Exception e);
    }
}