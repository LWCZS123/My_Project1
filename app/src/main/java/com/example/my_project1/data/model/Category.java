package com.example.my_project1.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Category（主分类）Room 实体 — 重构版
 *
 * 重构要点：
 *  1. 新增复合唯一索引 (owner_id, type, name)，从数据库层防止同一用户的同类型重名分类
 *  2. cloud_id 索引保留，用于云端同步快速查找
 *  3. subCategories 为非持久化字段（@Ignore），由 CategoryWithSubCategories 关系查询填充
 *  4. markUpdatedForSync / markDeletedForSync 辅助方法保持不变
 */
@Entity(
        tableName = "categories",
        indices = {
                @Index(value = "cloud_id"),
                @Index(value = {"owner_id", "type"}),
                // ★ 唯一约束：同一用户在同类型下不允许重名（防止批量插入重复）
                @Index(value = {"owner_id", "type", "name"}, unique = true)
        }
)
public class Category {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 云端 objectId，首次上传后写入，用于后续 update/delete */
    @ColumnInfo(name = "cloud_id")
    public String cloudId;

    /** 所属用户的 Bmob objectId，多用户数据隔离 */
    @ColumnInfo(name = "owner_id")
    public String ownerId;

    /** "expense" 或 "income" */
    public String type;

    public String name;

    @ColumnInfo(name = "icon_uri")
    public String iconUri;

    /** 可选颜色，如 "#FF6B6B" */
    public String color;

    /** 排序权重，越小越靠前 */
    public int order;

    /** 本地修改时间戳（ms），用于多端冲突解决 */
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /** SyncState 枚举的 int 值 */
    @ColumnInfo(name = "sync_state")
    public int syncState;

    @ColumnInfo(name = "is_system_preset")
    private boolean isSystemPreset;

    @ColumnInfo(name = "exclude_budget")
    private boolean excludeBudget;

    /** 非持久化：由 Room @Relation 填充，不写入 categories 表 */
    @Ignore
    private List<SubCategory> subCategories;

    // -------------------------------------------------------------------------
    // 构造器
    // -------------------------------------------------------------------------

    /** Room 必须的空构造器 */
    public Category() {}

    /**
     * 便捷构造器：新建本地分类时使用
     * syncState 默认为 TO_CREATE，待后台 Worker 上传云端
     */
    @Ignore
    public Category(String ownerId, String type, String name, String iconUri, Boolean excludeBudget) {
        this.ownerId = ownerId;
        this.type = type;
        this.name = name;
        this.iconUri = iconUri;
        this.excludeBudget = excludeBudget != null && excludeBudget;
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_CREATE.getValue();
        this.subCategories = new ArrayList<>();
        this.order = 0;
    }

    // -------------------------------------------------------------------------
    // Getter / Setter
    // -------------------------------------------------------------------------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIconUri() { return iconUri; }
    public void setIconUri(String iconUri) { this.iconUri = iconUri; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getSyncState() { return syncState; }
    public void setSyncState(int syncState) { this.syncState = syncState; }

    public boolean isSystemPreset() { return isSystemPreset; }
    public void setSystemPreset(boolean systemPreset) { isSystemPreset = systemPreset; }

    public boolean isExcludeBudget() { return excludeBudget; }
    public void setExcludeBudget(boolean excludeBudget) { this.excludeBudget = excludeBudget; }

    public List<SubCategory> getSubCategories() { return subCategories; }
    public void setSubCategories(List<SubCategory> subCategories) { this.subCategories = subCategories; }

    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------

    /** 标记为待更新，同时刷新 updatedAt 时间戳 */
    public void markUpdatedForSync() {
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_UPDATE.getValue();
    }

    /** 标记为待删除，同时刷新 updatedAt 时间戳 */
    public void markDeletedForSync() {
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_DELETE.getValue();
    }

    @Override
    public String toString() {
        return "Category{id=" + id + ", cloudId='" + cloudId + "', ownerId='" + ownerId
                + "', type='" + type + "', name='" + name + "', syncState=" + syncState + '}';
    }
}