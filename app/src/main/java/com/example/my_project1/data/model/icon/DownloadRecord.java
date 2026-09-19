package com.example.my_project1.data.model.icon;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "download_records")
public class DownloadRecord {

    // 状态常量
    public static final String STATUS_PENDING = "等待中";
    public static final String STATUS_DOWNLOADING = "下载中";
    public static final String STATUS_SUCCESS = "已完成";
    public static final String STATUS_FAILED = "下载失败";
    public static final String STATUS_CANCELLED = "已取消";
    public static final String STATUS_PAUSED = "已暂停";

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "icon_id")
    private String iconId;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "category_name")
    private String categoryName;

    @ColumnInfo(name = "url")
    private String url;

    @ColumnInfo(name = "thumb_url")
    private String thumbUrl;

    @ColumnInfo(name = "style")
    private String style;

    @ColumnInfo(name = "local_path")
    private String localPath;

    @ColumnInfo(name = "status")
    private String status = STATUS_PENDING;

    @ColumnInfo(name = "progress")
    private int progress;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @ColumnInfo(name = "start_time")
    private long startTime;

    @ColumnInfo(name = "finish_time")
    private long finishTime;

    @ColumnInfo(name = "batch_id")
    private String batchId; // 批量下载任务标识（合集下载时使用）

    @ColumnInfo(name = "tree_uri")
    private String treeUri;

    @ColumnInfo(name = "relative_path")
    private String relativePath;

    @ColumnInfo(name = "file_name")
    private String fileName;

    @ColumnInfo(name = "total_bytes")
    private long totalBytes;

    @ColumnInfo(name = "downloaded_bytes")
    private long downloadedBytes;

    @ColumnInfo(name = "error_message")
    private String errorMessage;

    @ColumnInfo(name = "summary_shown")
    private boolean summaryShown;

    public DownloadRecord() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getIconId() { return iconId; }
    public void setIconId(String iconId) { this.iconId = iconId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getFinishTime() { return finishTime; }
    public void setFinishTime(long finishTime) { this.finishTime = finishTime; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getTreeUri() { return treeUri; }
    public void setTreeUri(String treeUri) { this.treeUri = treeUri; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getDownloadedBytes() { return downloadedBytes; }
    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isSummaryShown() { return summaryShown; }
    public void setSummaryShown(boolean summaryShown) { this.summaryShown = summaryShown; }
}
