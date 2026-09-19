package com.example.my_project1.ui.viewmodel.icon;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.my_project1.data.model.icon.DownloadRecord;
import com.example.my_project1.data.repository.icon.DownloadRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadViewModel extends AndroidViewModel {

    private final DownloadRepository repository;
    private final MutableLiveData<String> filterBatchId = new MutableLiveData<>(null);
    
    private final LiveData<List<DownloadRecord>> rawRecords;
    private final ThrottledLiveData<List<DownloadRecord>> throttledRecords;

    private final MutableLiveData<DownloadEvent> completionEvent = new MutableLiveData<>();

    public static class DownloadEvent {
        public String batchId;
        public int total;
        public int success;
        public int failed;
        public String collectionName;
        public String treeUri;
        
        public DownloadEvent(String batchId, int total, int success, int failed, String collectionName, String treeUri) {
            this.batchId = batchId;
            this.total = total;
            this.success = success;
            this.failed = failed;
            this.collectionName = collectionName;
            this.treeUri = treeUri;
        }
    }

    public static class DownloadStats {
        public int totalCount;
        public int completedCount;
        public int failedCount;
        public int progress;
        public int collectionCount;
        public String statusText;
        public long totalBytes;
        public long downloadedBytes;
        public String sizeText;
    }

    private final MediatorLiveData<DownloadStats> stats = new MediatorLiveData<>();

    public DownloadViewModel(@NonNull Application application) {
        super(application);
        repository = DownloadRepository.getInstance(application);
        
        rawRecords = Transformations.switchMap(filterBatchId, batchId -> {
            if (batchId == null) {
                return repository.getAllRecords();
            } else {
                return repository.getRecordsByBatchId(batchId);
            }
        });

        // 将 UI 更新频率限制在 300ms 一次，避免数据库高频更新时导致 UI 掉帧
        throttledRecords = new ThrottledLiveData<>(rawRecords, 300);

        stats.addSource(throttledRecords, records -> {
            // 在后台线程计算统计信息，避免阻塞主线程
            AppExecutors.get().diskIO().execute(() -> calculateStats(records));
        });
    }

    public void setFilterBatchId(String batchId) {
        filterBatchId.setValue(batchId);
    }

    public LiveData<List<DownloadRecord>> getRecords() {
        return throttledRecords;
    }

    public LiveData<DownloadStats> getStats() {
        return stats;
    }

    public LiveData<DownloadEvent> getCompletionEvent() {
        return completionEvent;
    }

    public void pauseDownload(String batchId) {
        repository.pauseDownload(batchId);
    }

    public void resumeDownload(String batchId) {
        repository.resumeDownload(batchId);
    }

    public void cancelDownload(String batchId) {
        repository.cancelDownload(batchId);
    }

    public void retryIconDownload(String iconId) {
        repository.retryDownload(iconId);
    }

    public void clearHistory(String batchId) {
        repository.clearHistory(batchId);
    }

    public void markSummaryShown(String batchId) {
        repository.markSummaryShown(batchId);
    }

    private void calculateStats(List<DownloadRecord> records) {
        if (records == null || records.isEmpty()) {
            DownloadStats s = new DownloadStats();
            s.totalCount = 0;
            s.completedCount = 0;
            s.failedCount = 0;
            s.progress = 0;
            s.collectionCount = 0;
            s.statusText = "暂无任务";
            s.totalBytes = 0;
            s.downloadedBytes = 0;
            s.sizeText = "0 B / 0 B";
            stats.postValue(s);
            return;
        }

        DownloadStats s = new DownloadStats();
        s.totalCount = records.size();
        Set<String> collections = new HashSet<>();
        s.downloadedBytes = 0;
        s.totalBytes = 0;
        int pausedCount = 0;

        for (DownloadRecord r : records) {
            if (r.getCategoryName() != null) {
                collections.add(r.getCategoryName());
            }
            if (DownloadRecord.STATUS_SUCCESS.equals(r.getStatus())) {
                s.completedCount++;
            } else if (DownloadRecord.STATUS_FAILED.equals(r.getStatus())) {
                s.failedCount++;
            } else if (DownloadRecord.STATUS_PAUSED.equals(r.getStatus())) {
                pausedCount++;
            }
            s.downloadedBytes += r.getDownloadedBytes();
            s.totalBytes += r.getTotalBytes();
        }

        s.collectionCount = collections.size();
        s.progress = s.totalCount > 0 ? (s.completedCount * 100) / s.totalCount : 0;
        
        if (s.progress == 100) {
            s.statusText = "下载完成";
        } else if (pausedCount > 0 && (s.completedCount + s.failedCount + pausedCount >= s.totalCount)) {
            s.statusText = "已暂停";
        } else {
            s.statusText = "正在下载 (" + s.completedCount + "/" + s.totalCount + ")";
        }

        s.sizeText = formatSize(s.downloadedBytes) + " / " + formatSize(s.totalBytes);

        stats.postValue(s);

        // 检测批次完成状态，触发一次性完成事件
        if (s.progress == 100 && s.totalCount > 0) {
            // 如果是详情页模式，根据 batchId 触发
            String currentBatchId = filterBatchId.getValue();
            if (currentBatchId != null) {
                // 检查数据库持久化的 summary_shown 状态，防止重复弹出
                boolean alreadyShown = records.get(0).isSummaryShown();
                if (!alreadyShown) {
                    completionEvent.postValue(new DownloadEvent(
                            currentBatchId, s.totalCount, s.completedCount, s.failedCount,
                            records.get(0).getCategoryName(), records.get(0).getTreeUri()));
                }
            } else {
                // 全局模式下简化处理，通常在详情页触发
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.##").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * 自定义 LiveData，用于对高频更新进行节流处理
     */
    private static class ThrottledLiveData<T> extends MediatorLiveData<T> {
        private final long intervalMillis;
        private long lastUpdateTime = 0;
        private T pendingValue = null;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable dispatchRunnable = new Runnable() {
            @Override
            public void run() {
                lastUpdateTime = System.currentTimeMillis();
                setValue(pendingValue);
            }
        };

        public ThrottledLiveData(LiveData<T> source, long intervalMillis) {
            this.intervalMillis = intervalMillis;
            addSource(source, value -> {
                pendingValue = value;
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= intervalMillis) {
                    handler.removeCallbacks(dispatchRunnable);
                    dispatchRunnable.run();
                } else if (!handler.hasMessages(0)) { // 简化版消息检查
                    handler.removeCallbacks(dispatchRunnable);
                    handler.postDelayed(dispatchRunnable, intervalMillis - (currentTime - lastUpdateTime));
                }
            });
        }
    }
}
