package com.example.my_project1.data.model.tag;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.my_project1.data.model.SyncState;

import java.io.Serializable;
import java.util.Date;

/**
 * 标签实体类（对应本地 SQLite 数据库中的 tags 表）
 */
@Entity(
        tableName = "tags",
        indices = {
                @Index(value = {"user_id", "group_id"}),
                @Index(value = {"user_id", "is_archived"})
        }
)
public class TagEntity implements Serializable {

    /** 本地数据库主键ID，自增 */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /** 云端 Bmob 对象的唯一标识符 */
    @ColumnInfo(name = "object_id")
    private String objectId;

    /** 所属用户的唯一标识ID */
    @ColumnInfo(name = "user_id")
    private String userId;

    /** 所属标签分组的本地数据库主键ID */
    @ColumnInfo(name = "group_id")
    private long groupId;

    /** 标签名称 */
    private String name;

    /** 标签背景颜色的十六进制字符串（如 #FF5722） */
    @ColumnInfo(name = "bg_color")
    private String bgColor;

    /** 标签文字颜色的十六进制字符串（如 #FFFFFF） */
    @ColumnInfo(name = "text_color")
    private String textColor;

    /** 标签在分组内的排序权重序号 */
    @ColumnInfo(name = "sort_order")
    private int sortOrder;

    /** 是否归档状态（归档后的标签不显示在常规选择列表中，但在历史账单中保留） */
    @ColumnInfo(name = "is_archived")
    private boolean isArchived;

    /** 逻辑删除标记，true表示该标签已被软删除 */
    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    /** 记录创建的时间 */
    @ColumnInfo(name = "created_at")
    private Date createdAt;

    /** 记录更新的时间 */
    @ColumnInfo(name = "updated_at")
    private Date updatedAt;

    /** 本地与云端同步状态标记（已同步/待创建/待更新/待删除） */
    @ColumnInfo(name = "sync_state")
    private SyncState syncState = SyncState.SYNCED;

    /** 无参构造函数，Room 数据库解包使用 */
    public TagEntity() {}

    /**
     * 快捷构造函数，用于创建新的标签实体
     *
     * @param userId    用户ID
     * @param groupId   所属分组本地ID
     * @param name      标签名称
     * @param bgColor   背景颜色值
     * @param textColor 文字颜色值
     * @param sortOrder 排序权重
     */
    @Ignore
    public TagEntity(String userId, long groupId, String name, String bgColor, String textColor, int sortOrder) {
        this.userId = userId;
        this.groupId = groupId;
        this.name = name;
        this.bgColor = bgColor;
        this.textColor = textColor;
        this.sortOrder = sortOrder;
        this.isArchived = false;
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

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBgColor() {
        return bgColor;
    }

    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
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
