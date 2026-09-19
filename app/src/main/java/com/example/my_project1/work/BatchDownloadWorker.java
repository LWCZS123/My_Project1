package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.dao.DownloadDao;
import com.example.my_project1.data.model.icon.DownloadRecord;
import com.example.my_project1.utils.DownloadPathManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BatchDownloadWorker extends Worker {

    private static final String TAG = "BatchDownloadWorker";
    private static final long PROGRESS_UPDATE_INTERVAL = 500; // 进度更新频率限制为 500ms，降低数据库写入压力
    private final DownloadDao downloadDao;
    private final OkHttpClient client;
    private final DownloadPathManager pathManager;

    public BatchDownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.downloadDao = AppDatabase.getInstance(context).downloadDao();
        this.client = new OkHttpClient();
        this.pathManager = DownloadPathManager.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        String batchId = getInputData().getString("batchId");
        String retryIconId = getInputData().getString("retryIconId");
        
        if (retryIconId != null) {
            // 重试单个特定图标
            DownloadRecord record = downloadDao.getRecordByIconId(retryIconId);
            if (record != null) {
                downloadIcon(record);
            }
        } else {
            // 批量下载模式
            List<DownloadRecord> records;
            if (batchId != null) {
                records = downloadDao.getRecordsByBatchIdSync(batchId);
            } else {
                records = downloadDao.getAllRecordsSync();
            }

            if (records == null || records.isEmpty()) return Result.success();

            for (DownloadRecord record : records) {
                // 仅下载非完成状态的任务
                if (DownloadRecord.STATUS_SUCCESS.equals(record.getStatus())) continue;
                if (DownloadRecord.STATUS_PAUSED.equals(record.getStatus())) continue;
                if (DownloadRecord.STATUS_CANCELLED.equals(record.getStatus())) continue;
                
                // 检查 WorkManager 任务是否已停止/取消
                if (isStopped()) {
                    return Result.retry();
                }

                downloadIcon(record);
            }
        }

        return Result.success();
    }

    private void downloadIcon(DownloadRecord record) {
        long startTime = System.currentTimeMillis();
        downloadDao.updateDownloadStart(record.getIconId(), DownloadRecord.STATUS_DOWNLOADING, startTime);

        try {
            String subDir = null;
            if (record.getBatchId() != null && !record.getBatchId().startsWith("single_")) {
                subDir = record.getCategoryName();
            }

            // 优先使用图标名称，备选为 ID
            String name = record.getName();
            if (name == null || name.isEmpty()) name = "icon_" + record.getIconId();
            
            String fileName = name;
            String mimeType = "image/png";
            String extension = ".png";
            
            String urlLower = record.getUrl().toLowerCase();
            if (urlLower.contains(".jpg") || urlLower.contains(".jpeg")) {
                mimeType = "image/jpeg";
                extension = ".jpg";
            } else if (urlLower.contains(".webp")) {
                mimeType = "image/webp";
                extension = ".webp";
            } else if (urlLower.contains(".gif")) {
                mimeType = "image/gif";
                extension = ".gif";
            }

            // 确保文件名包含正确的后缀
            if (!fileName.toLowerCase().endsWith(extension)) {
                fileName += extension;
            }

            DocumentFile file = pathManager.createDownloadFile(subDir, fileName, mimeType);
            if (file == null) throw new Exception("无法在存储器中创建文件");

            Request request = new Request.Builder().url(record.getUrl()).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new Exception("服务器响应错误: " + response.code());
                }

                long totalBytes = response.body().contentLength();
                InputStream in = response.body().byteStream();
                try (OutputStream out = getApplicationContext().getContentResolver().openOutputStream(file.getUri())) {
                    if (out == null) throw new Exception("无法打开输出流");

                    byte[] buffer = new byte[16384]; // Larger buffer
                    int bytesRead;
                    long bytesWritten = 0;
                    long lastUpdateTime = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (isStopped()) {
                            // 取消任务时清理未下载完成的临时文件
                            try { file.delete(); } catch (Exception ignored) {}
                            return;
                        }

                        out.write(buffer, 0, bytesRead);
                        bytesWritten += bytesRead;

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime > PROGRESS_UPDATE_INTERVAL) {
                            int progress = totalBytes > 0 ? (int) ((bytesWritten * 100) / totalBytes) : 0;
                            updateProgress(record, DownloadRecord.STATUS_DOWNLOADING, progress, bytesWritten, totalBytes, file.getUri().toString(), null, 0);
                            lastUpdateTime = currentTime;
                        }
                    }
                    out.flush();
                }

                updateProgress(record, DownloadRecord.STATUS_SUCCESS, 100, totalBytes, totalBytes, file.getUri().toString(), null, System.currentTimeMillis());
                
                // 触发 MediaScanner 扫描文件，确保下载完成后立即在相册中可见
                try {
                    android.content.Intent mediaScanIntent = new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(file.getUri());
                    getApplicationContext().sendBroadcast(mediaScanIntent);
                } catch (Exception e) {
                    Log.e(TAG, "媒体库刷新失败", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "下载图标失败: " + record.getIconId(), e);
            updateProgress(record, DownloadRecord.STATUS_FAILED, 0, 0, 0, null, e.getMessage(), System.currentTimeMillis());
        }
    }

    private void updateProgress(DownloadRecord record, String status, int progress, long downloaded, long total, String localPath, String error, long finishTime) {
        downloadDao.updateDownloadProgress(record.getIconId(), status, progress, downloaded, total, localPath, error, finishTime);
    }
}
