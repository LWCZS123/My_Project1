package com.example.my_project1.data.model.account;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.util.Date;

import io.reactivex.annotations.NonNull;

@Entity(tableName = "account_groups")
public class AccountGroup {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "object_id")
    private String objectId; // Bmob 对应 objectId

    @ColumnInfo(name = "user_id")
    private String userId; // 所属用户ID

    private String name; // 分组名称
    private String iconUrl; // 图标
    private int accountCount; // 分组下账户数量

    private Date createdAt;
    private Date updatedAt;

    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    //空构造
    public AccountGroup(){}

    @Ignore
    public AccountGroup(String objectId, String name, String userId, String iconUrl,
                        int accountCount) {
        this.objectId = objectId;
        this.name = name;
        this.userId = userId;
        this.iconUrl = iconUrl;
        this.accountCount = accountCount;
        this.createdAt =  new Date();
        this.updatedAt =  new Date();
        this.syncState = syncState != null ? syncState : SyncState.SYNCED;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    // getter & setter
    @NonNull


    public String getObjectId() { return objectId; }
    public void setObjectId(@NonNull String objectId) { this.objectId = objectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public int getAccountCount() { return accountCount; }
    public void setAccountCount(int accountCount) { this.accountCount = accountCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public SyncState getSyncState() {
        return syncState;
    }

    public void setSyncState(SyncState syncState) {
        this.syncState = syncState;
    }
}
