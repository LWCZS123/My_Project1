package com.example.my_project1.data.model.tag;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;

/**
 * 标签分组本地数据库实体类（对应 tag_groups 表）
 */
@Entity(tableName = "tag_groups")
public class TagGroupEntity implements Serializable {

    /** 本地数据库主键ID，自增 */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 云端 Bmob 对象的唯一标识符 */
    @ColumnInfo(name = "object_id")
    private String objectId;

    /** 所属用户的唯一标识ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 标签分组的显示名称 */
    private String name;

    /** 标签分组在UI页面上的排序权重或顺序 */
    @ColumnInfo(name = "sort_order")
    private int sortOrder;

    /** 是否为系统默认创建的标签分组（默认分组不可重命名与删除） */
    @ColumnInfo(name = "is_default")
    private boolean isDefault;

    /** UI列表中该标签分组是否处于折叠状态 */
    @ColumnInfo(name = "is_folded")
    private boolean isFolded;

    /** 逻辑删除标记，true表示该记录已被删除 */
    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    /** 记录创建的时间 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;

    /** 记录最后更新的时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    /** 本地与云端同步状态标记（已同步/待创建/待更新/待删除） */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 无参构造函数，Room 数据库映射使用 */
    public TagGroupEntity() {}

    /**
     * 快捷构造函数，用于创建新的标签分组
     *
     * @param userId    用户ID
     * @param name      分组名称
     * @param sortOrder 排序序号
     * @param isDefault 是否为默认分组
     */
    @Ignore
    public TagGroupEntity(String userId, String name, int sortOrder, boolean isDefault) {
        this.userId = userId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.isDefault = isDefault;
        this.isFolded = false;
        this.isDeleted = false;
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.syncState = SyncState.TO_CREATE;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isFolded() {
        return isFolded;
    }

    public void setFolded(boolean folded) {
        isFolded = folded;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
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
