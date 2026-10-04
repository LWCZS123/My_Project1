package com.example.my_project1.data.repository.icon;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.dao.DownloadDao;
import com.example.my_project1.data.model.icon.DownloadRecord;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.DownloadPathManager;
import com.example.my_project1.work.BatchDownloadWorker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DownloadRepository {

    public interface DownloadEnqueueCallback {
        void onResult(boolean enqueued, String message);
    }

    private static final String TAG = "DownloadRepository";
    private static volatile DownloadRepository instance;
    private final DownloadDao downloadDao;
    private final WorkManager workManager;
    private final Context context;

    private DownloadRepository(Context context) {
        this.context = context.getApplicationContext();
        this.downloadDao = AppDatabase.getInstance(context).downloadDao();
        this.workManager = WorkManager.getInstance(context);
    }

    public static DownloadRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (DownloadRepository.class) {
                if (instance == null) {
                    instance = new DownloadRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<DownloadRecord>> getAllRecords() {
        return downloadDao.getAllRecords();
    }

    public LiveData<List<DownloadRecord>> getRecordsByBatchId(String batchId) {
        return downloadDao.getRecordsByBatchId(batchId);
    }

    public void startCollectionDownload(IconCategory category) {
        startCollectionDownload(category, null);
    }

    public void startCollectionDownload(IconCategory category, DownloadEnqueueCallback callback) {
        AppExecutors.get().networkIO().execute(() -> {
            try {
                List<IconItem> items = IconRepository.getInstance().getAllCategoryItemsSync(context.getAssets(), category);
                if (items == null || items.isEmpty()) {
                    dispatchEnqueueResult(callback, false, "合集内没有可下载的图标");
                    return;
                }

                List<DownloadRecord> toInsert = new ArrayList<>();
                String batchId = category.getCategory() + "_" + System.currentTimeMillis();
                Uri currentTreeUri = DownloadPathManager.getInstance(context).getCustomDownloadDirUri();
                String treeUriStr = currentTreeUri != null ? currentTreeUri.toString() : null;
                Map<String, DownloadRecord> existingRecords = getLatestRecordsByIconId();
                
                for (IconItem item : items) {
                    DownloadRecord existing = existingRecords.get(item.getId());
                    if (existing != null && (DownloadRecord.STATUS_SUCCESS.equals(existing.getStatus())
                            || DownloadRecord.STATUS_PENDING.equals(existing.getStatus())
                            || DownloadRecord.STATUS_DOWNLOADING.equals(existing.getStatus()))) {
                        continue;
                    }
                    
                    DownloadRecord record = new DownloadRecord();
                    if (existing != null) record.setId(existing.getId());
                    record.setIconId(item.getId());
                    record.setName(item.getName());
                    record.setCategoryName(category.getCategory());
                    record.setUrl(item.getUrl());
                    record.setThumbUrl(item.getThumbUrl());
                    record.setStyle(category.getStyle());
                    record.setStatus(DownloadRecord.STATUS_PENDING);
                    record.setTimestamp(System.currentTimeMillis());
                    record.setBatchId(batchId);
                    record.setTreeUri(treeUriStr);
                    record.setRelativePath(category.getCategory());
                    toInsert.add(record);
                }

                if (!toInsert.isEmpty()) {
                    downloadDao.insertAll(toInsert);
                    
                    Data inputData = new Data.Builder()
                            .putString("batchId", batchId)
                            .build();

                    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                            .setInputData(inputData)
                            .addTag("BatchDownload_" + batchId)
                            .addTag("BatchDownloadWorker")
                            .build();
                    
                    workManager.enqueue(request);
                    dispatchEnqueueResult(callback, true,
                            "已加入后台下载：" + category.getCategory());
                } else {
                    dispatchEnqueueResult(callback, false, "该合集已下载或正在下载");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start collection download", e);
                dispatchEnqueueResult(callback, false, "合集下载失败，请稍后重试");
            }
        });
    }

    public void startIconDownload(IconItem item) {
        startIconDownload(item, null);
    }

    public void startIconDownload(IconItem item, DownloadEnqueueCallback callback) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) {
            dispatchEnqueueResult(callback, false, "图标数据无效");
            return;
        }
        AppExecutors.get().diskIO().execute(() -> {
            try {
                DownloadRecord existing = downloadDao.getRecordByIconId(item.getId());
                if (existing != null && DownloadRecord.STATUS_SUCCESS.equals(existing.getStatus())) {
                    dispatchEnqueueResult(callback, false, "该图标已下载");
                    return;
                }
                if (existing != null && (DownloadRecord.STATUS_PENDING.equals(existing.getStatus())
                        || DownloadRecord.STATUS_DOWNLOADING.equals(existing.getStatus()))) {
                    dispatchEnqueueResult(callback, false, "该图标正在下载");
                    return;
                }

                DownloadRecord record = new DownloadRecord();
                if (existing != null) record.setId(existing.getId());
                record.setIconId(item.getId());
                record.setName(item.getName());
                record.setCategoryName(item.getCategory());
                record.setUrl(item.getUrl());
                record.setThumbUrl(item.getThumbUrl());
                record.setStyle(item.getStyle());
                record.setStatus(DownloadRecord.STATUS_PENDING);
                record.setTimestamp(System.currentTimeMillis());
                record.setBatchId("single_" + item.getId());

                Uri currentTreeUri = DownloadPathManager.getInstance(context).getCustomDownloadDirUri();
                if (currentTreeUri != null) {
                    record.setTreeUri(currentTreeUri.toString());
                }

                downloadDao.insert(record);

                Data inputData = new Data.Builder()
                        .putString("batchId", record.getBatchId())
                        .build();

                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                        .setInputData(inputData)
                        .addTag("SingleDownload_" + item.getId())
                        .addTag("BatchDownloadWorker")
                        .build();

                workManager.enqueueUniqueWork("SingleDownload_" + item.getId(),
                        ExistingWorkPolicy.KEEP, request);
                dispatchEnqueueResult(callback, true, "已加入后台下载：" + item.getName());
            } catch (Exception e) {
                Log.e(TAG, "Failed to enqueue icon download: " + item.getId(), e);
                dispatchEnqueueResult(callback, false, "下载任务创建失败，请稍后重试");
            }
        });
    }

    /** Enqueues selected icons with one database write and one background worker. */
    public void startIconDownloads(List<IconItem> items, DownloadEnqueueCallback callback) {
        if (items == null || items.isEmpty()) {
            dispatchEnqueueResult(callback, false, "没有可下载的图标");
            return;
        }
        AppExecutors.get().diskIO().execute(() -> {
            try {
                String batchId = "single_group_" + System.currentTimeMillis();
                Uri treeUri = DownloadPathManager.getInstance(context).getCustomDownloadDirUri();
                String treeUriString = treeUri == null ? null : treeUri.toString();
                List<DownloadRecord> records = new ArrayList<>();
                Map<String, DownloadRecord> existingRecords = getLatestRecordsByIconId();
                for (IconItem item : items) {
                    if (item == null || item.getId() == null || item.getId().isEmpty()) continue;
                    DownloadRecord existing = existingRecords.get(item.getId());
                    if (existing != null && (DownloadRecord.STATUS_SUCCESS.equals(existing.getStatus())
                            || DownloadRecord.STATUS_PENDING.equals(existing.getStatus())
                            || DownloadRecord.STATUS_DOWNLOADING.equals(existing.getStatus()))) {
                        continue;
                    }
                    DownloadRecord record = new DownloadRecord();
                    if (existing != null) record.setId(existing.getId());
                    record.setIconId(item.getId());
                    record.setName(item.getName());
                    record.setCategoryName(item.getCategory());
                    record.setUrl(item.getUrl());
                    record.setThumbUrl(item.getThumbUrl());
                    record.setStyle(item.getStyle());
                    record.setStatus(DownloadRecord.STATUS_PENDING);
                    record.setTimestamp(System.currentTimeMillis());
                    record.setBatchId(batchId);
                    record.setTreeUri(treeUriString);
                    records.add(record);
                }
                if (records.isEmpty()) {
                    dispatchEnqueueResult(callback, false, "所选图标已下载或正在下载");
                    return;
                }
                downloadDao.insertAll(records);
                Data inputData = new Data.Builder().putString("batchId", batchId).build();
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                        .setInputData(inputData)
                        .addTag("SingleGroupDownload_" + batchId)
                        .addTag("BatchDownloadWorker")
                        .build();
                workManager.enqueueUniqueWork("SingleGroupDownload_" + batchId,
                        ExistingWorkPolicy.KEEP, request);
                dispatchEnqueueResult(callback, true,
                        "已加入后台下载，共 " + records.size() + " 枚图标");
            } catch (Exception e) {
                Log.e(TAG, "Failed to enqueue selected icon downloads", e);
                dispatchEnqueueResult(callback, false, "下载任务创建失败，请稍后重试");
            }
        });
    }

    private void dispatchEnqueueResult(DownloadEnqueueCallback callback,
                                       boolean enqueued, String message) {
        if (callback == null) return;
        AppExecutors.get().mainThread().execute(() -> callback.onResult(enqueued, message));
    }

    private Map<String, DownloadRecord> getLatestRecordsByIconId() {
        Map<String, DownloadRecord> latest = new HashMap<>();
        List<DownloadRecord> records = downloadDao.getAllRecordsSync();
        if (records == null) return latest;
        for (DownloadRecord record : records) {
            if (record.getIconId() != null && !latest.containsKey(record.getIconId())) {
                latest.put(record.getIconId(), record);
            }
        }
        return latest;
    }

    public void updateStatus(String iconId, String status, int progress, String localPath) {
        AppExecutors.get().diskIO().execute(() -> {
            downloadDao.updateStatus(iconId, status, progress, localPath);
        });
    }

    public void pauseDownload(String batchId) {
        AppExecutors.get().diskIO().execute(() -> {
            if (batchId != null) {
                workManager.cancelAllWorkByTag("BatchDownload_" + batchId);
                workManager.cancelAllWorkByTag("SingleDownload_" + batchId.replace("single_", ""));
                downloadDao.pauseBatchActiveDownloads(batchId);
            } else {
                workManager.cancelAllWorkByTag("BatchDownloadWorker");
                downloadDao.pauseAllActiveDownloads();
            }
        });
    }

    public void resumeDownload(String batchId) {
        AppExecutors.get().diskIO().execute(() -> {
            downloadDao.resumeAllActiveDownloads(batchId, DownloadRecord.STATUS_PENDING);
            Data inputData = new Data.Builder()
                    .putString("batchId", batchId)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                    .setInputData(inputData)
                    .addTag(batchId != null ? (batchId.startsWith("single_") ? "SingleDownload_" + batchId.replace("single_", "") : "BatchDownload_" + batchId) : "GlobalDownload")
                    .addTag("BatchDownloadWorker")
                    .build();
            workManager.enqueue(request);
        });
    }

    public void cancelDownload(String batchId) {
        AppExecutors.get().diskIO().execute(() -> {
            if (batchId != null) {
                workManager.cancelAllWorkByTag("BatchDownload_" + batchId);
                workManager.cancelAllWorkByTag("SingleDownload_" + batchId.replace("single_", ""));
                downloadDao.updateStatusForBatch(batchId, DownloadRecord.STATUS_CANCELLED);
            } else {
                workManager.cancelAllWorkByTag("BatchDownloadWorker");
                downloadDao.updateStatusForBatch(null, DownloadRecord.STATUS_CANCELLED);
            }
        });
    }

    public void retryDownload(String iconId) {
        AppExecutors.get().diskIO().execute(() -> {
            DownloadRecord record = downloadDao.getRecordByIconId(iconId);
            if (record == null) return;
            
            record.setStatus(DownloadRecord.STATUS_PENDING);
            record.setProgress(0);
            record.setErrorMessage(null);
            downloadDao.update(record);

            Data inputData = new Data.Builder()
                    .putString("batchId", record.getBatchId())
                    .putString("retryIconId", iconId)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                    .setInputData(inputData)
                    .addTag("RetryDownload_" + iconId)
                    .addTag("BatchDownloadWorker")
                    .build();
            workManager.enqueue(request);
        });
    }

    public void clearHistory(String batchId) {
        AppExecutors.get().diskIO().execute(() -> {
            if (batchId != null) {
                downloadDao.deleteCompletedRecordsByBatch(batchId);
            } else {
                downloadDao.deleteAllCompletedRecords();
            }
        });
    }

    public void markSummaryShown(String batchId) {
        AppExecutors.get().diskIO().execute(() -> {
            downloadDao.markBatchSummaryShown(batchId);
        });
    }
}
