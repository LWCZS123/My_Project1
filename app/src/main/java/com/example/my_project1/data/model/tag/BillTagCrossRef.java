package com.example.my_project1.data.model.tag;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;

@Entity(
        tableName = "bill_tag_cross_ref",
        primaryKeys = {"bill_id", "tag_id"},
        indices = {
                @Index(value = {"bill_id"}),
                @Index(value = {"tag_id"})
        }
)
public class BillTagCrossRef implements Serializable {

    @ColumnInfo(name = "bill_id")
    private long billId;

    @ColumnInfo(name = "tag_id")
    private long tagId;

    @ColumnInfo(name = "user_id")
    private String userId;

    @ColumnInfo(name = "tag_name")
    private String tagName;

    @ColumnInfo(name = "tag_bg_color")
    private String tagBgColor;

    @ColumnInfo(name = "tag_text_color")
    private String tagTextColor;

    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    public BillTagCrossRef() {}

    @Ignore
    public BillTagCrossRef(long billId, long tagId, String userId, String tagName, String tagBgColor, String tagTextColor) {
        this.billId = billId;
        this.tagId = tagId;
        this.userId = userId;
        this.tagName = tagName;
        this.tagBgColor = tagBgColor;
        this.tagTextColor = tagTextColor;
        this.isDeleted = false;
        this.updatedAt = new Date();
        this.syncState = SyncState.TO_CREATE;
    }

    public long getBillId() {
        return billId;
    }

    public void setBillId(long billId) {
        this.billId = billId;
    }

    public long getTagId() {
        return tagId;
    }

    public void setTagId(long tagId) {
        this.tagId = tagId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTagBgColor() {
        return tagBgColor;
    }

    public void setTagBgColor(String tagBgColor) {
        this.tagBgColor = tagBgColor;
    }

    public String getTagTextColor() {
        return tagTextColor;
    }

    public void setTagTextColor(String tagTextColor) {
        this.tagTextColor = tagTextColor;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SyncState getSyncState() {
        return syncState;
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }
}
