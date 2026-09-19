package com.example.my_project1.data.repository.icon;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.Data;
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
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class DownloadRepository {

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
        AppExecutors.get().networkIO().execute(() -> {
            try {
                List<IconItem> items = IconRepository.getInstance().getAllCategoryItemsSync(context.getAssets(), category);
                if (items == null || items.isEmpty()) return;

                List<DownloadRecord> toInsert = new ArrayList<>();
                String batchId = category.getCategory() + "_" + System.currentTimeMillis();
                Uri currentTreeUri = DownloadPathManager.getInstance(context).getCustomDownloadDirUri();
                String treeUriStr = currentTreeUri != null ? currentTreeUri.toString() : null;
                
                for (IconItem item : items) {
                    DownloadRecord existing = downloadDao.getRecordByIconId(item.getId());
                    if (existing != null && DownloadRecord.STATUS_SUCCESS.equals(existing.getStatus())) {
                        continue;
                    }
                    
                    DownloadRecord record = new DownloadRecord();
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
                            .putString("categoryJson", new Gson().toJson(category))
                            .build();

                    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                            .setInputData(inputData)
                            .addTag("BatchDownload_" + batchId)
                            .addTag("BatchDownloadWorker")
                            .build();
                    
                    workManager.enqueue(request);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start collection download", e);
            }
        });
    }

    public void startIconDownload(IconItem item) {
        AppExecutors.get().networkIO().execute(() -> {
            DownloadRecord existing = downloadDao.getRecordByIconId(item.getId());
            if (existing != null && DownloadRecord.STATUS_SUCCESS.equals(existing.getStatus())) {
                // Check if file exists would be better but let's stick to requirements for now or add it later
                return;
            }

            DownloadRecord record = new DownloadRecord();
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
                    .putString("iconJson", new Gson().toJson(item))
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                    .setInputData(inputData)
                    .addTag("SingleDownload_" + item.getId())
                    .addTag("BatchDownloadWorker")
                    .build();

            workManager.enqueue(request);
        });
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
