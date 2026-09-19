package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.icon.DownloadRecord;

import java.util.List;

@Dao
public interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DownloadRecord record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DownloadRecord> records);

    @Update
    void update(DownloadRecord record);

    @Delete
    void delete(DownloadRecord record);

    @Query("SELECT * FROM download_records ORDER BY timestamp DESC")
    LiveData<List<DownloadRecord>> getAllRecords();

    @Query("SELECT * FROM download_records ORDER BY timestamp DESC")
    List<DownloadRecord> getAllRecordsSync();

    @Query("SELECT * FROM download_records WHERE icon_id = :iconId LIMIT 1")
    DownloadRecord getRecordByIconId(String iconId);

    @Query("SELECT * FROM download_records WHERE batch_id = :batchId ORDER BY timestamp ASC")
    LiveData<List<DownloadRecord>> getRecordsByBatchId(String batchId);

    @Query("SELECT * FROM download_records WHERE batch_id = :batchId ORDER BY timestamp ASC")
    List<DownloadRecord> getRecordsByBatchIdSync(String batchId);

    @Query("UPDATE download_records SET status = :status, progress = :progress, downloaded_bytes = :downloaded, total_bytes = :total, local_path = :localPath, error_message = :error, finish_time = :finishTime WHERE icon_id = :iconId")
    void updateDownloadProgress(String iconId, String status, int progress, long downloaded, long total, String localPath, String error, long finishTime);

    @Query("UPDATE download_records SET status = :status, start_time = :startTime WHERE icon_id = :iconId")
    void updateDownloadStart(String iconId, String status, long startTime);

    @Query("UPDATE download_records SET status = :status, progress = :progress, local_path = :localPath WHERE icon_id = :iconId")
    void updateStatus(String iconId, String status, int progress, String localPath);

    @Query("UPDATE download_records SET status = :status WHERE batch_id = :batchId AND status != '已完成'")
    void updateBatchStatus(String batchId, String status);

    @Query("UPDATE download_records SET status = '已暂停' WHERE status = '等待中' OR status = '下载中'")
    void pauseAllActiveDownloads();

    @Query("UPDATE download_records SET status = '已取消' WHERE status = '等待中' OR status = '下载中'")
    void cancelAllActiveDownloads();

    @Query("UPDATE download_records SET status = '已暂停' WHERE batch_id = :batchId AND (status = '等待中' OR status = '下载中')")
    void pauseBatchActiveDownloads(String batchId);

    @Query("UPDATE download_records SET status = '已取消' WHERE batch_id = :batchId AND (status = '等待中' OR status = '下载中')")
    void cancelBatchActiveDownloads(String batchId);

    @Query("UPDATE download_records SET status = :status WHERE (status = '已暂停' OR status = '下载失败' OR status = '已取消') AND (:batchId IS NULL OR batch_id = :batchId)")
    void resumeAllActiveDownloads(String batchId, String status);

    @Query("UPDATE download_records SET status = :status WHERE status != '已完成' AND (:batchId IS NULL OR batch_id = :batchId)")
    void updateStatusForBatch(String batchId, String status);

    @Query("UPDATE download_records SET summary_shown = 1 WHERE batch_id = :batchId")
    void markBatchSummaryShown(String batchId);

    @Query("DELETE FROM download_records WHERE batch_id = :batchId AND status = '已完成'")
    void deleteCompletedRecordsByBatch(String batchId);

    @Query("DELETE FROM download_records WHERE status = '已完成'")
    void deleteAllCompletedRecords();

    @Query("SELECT * FROM download_records WHERE icon_id = :iconId")
    DownloadRecord getRecordSync(String iconId);
}
