package com.example.my_project1.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * SubCategory（二级分类）Room 实体 — 重构版
 *
 * 重构要点：
 *  1. ★ 新增 ForeignKey(Category.id → parent_category_id)
 *       - onDelete = CASCADE：父分类被删除时，子分类自动级联删除（Room 层保障）
 *       - onUpdate = CASCADE：父分类 id 变更时，外键自动更新
 *  2. ★ parent_cloud_id 作为云端关联的 Source of Truth
 *       - 上传子分类时，必须先确保此字段已填充（来自父分类的 cloudId）
 *       - 下载时，根据云端 parentCategory.objectId 反查本地 id 后写入 parent_category_id
 *  3. 新增复合唯一索引 (parent_category_id, name)，防止同一父类下重名子分类
 *  4. 移除了 localId 冗余字段（本地 id 即 @PrimaryKey，无需重复存储）
 */
@Entity(
        tableName = "sub_categories",
        foreignKeys = {
                @ForeignKey(
                        entity = Category.class,
                        parentColumns = "id",
                        childColumns = "parent_category_id",
                        onDelete = ForeignKey.CASCADE,   // 父分类删除 → 子分类自动删除
                        onUpdate = ForeignKey.CASCADE    // 父分类 id 更新 → 外键自动跟随
                )
        },
        indices = {
                @Index(value = "cloud_id"),
                // ★ 外键字段必须建索引，否则 Room 编译警告且查询性能差
                @Index(value = "parent_category_id"),
                // ★ parent_cloud_id 索引：下载时反查父类的核心查询字段
                @Index(value = "parent_cloud_id"),
                // ★ 同一父分类下子分类名唯一约束
                @Index(value = {"parent_category_id", "name"}, unique = true)
        }
)
public class SubCategory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * 本地父分类 id（Room 外键），关联 categories.id
     * ★ 下载场景：根据云端 parentCategory.objectId 查到本地 Category 后写入此字段
     */
    @ColumnInfo(name = "parent_category_id")
    public long parentCategoryId;

    /**
     * ★ 云端父分类 objectId（Source of Truth）
     * 上传子分类前必须确保此字段非空（即父分类已完成云端同步）
     * 下载场景：直接从云端 parentCategory.objectId 获取
     */
    @ColumnInfo(name = "parent_cloud_id")
    public String parentCloudId;

    /** 云端 objectId，首次上传后写入 */
    @ColumnInfo(name = "cloud_id")
    public String cloudId;

    /** 所属用户的 Bmob objectId */
    @ColumnInfo(name = "owner_id")
    public String ownerId;

    public String name;

    @ColumnInfo(name = "icon_uri")
    public String iconUri;

    public String color;

    public int order;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /** SyncState 枚举的 int 值 */
    @ColumnInfo(name = "sync_state")
    public int syncState;

    @ColumnInfo(name = "is_system_preset")
    private boolean isSystemPreset;

    /** 非持久化：仅用于 UI 列表中的"+ 添加"占位项 */
    @Ignore
    private boolean isAddButton;

    // -------------------------------------------------------------------------
    // 构造器
    // -------------------------------------------------------------------------

    /** Room 必须的空构造器 */
    public SubCategory() {}

    /**
     * 便捷构造器：新建本地子分类
     * @param parentCategoryId 本地父分类 id
     * @param parentCloudId    父分类的云端 objectId（可为 null，若父类尚未同步）
     * @param ownerId          用户 objectId
     * @param name             子分类名
     * @param iconUri          图标标识
     */
    @Ignore
    public SubCategory(long parentCategoryId, String parentCloudId,
                       String ownerId, String name, String iconUri) {
        this.parentCategoryId = parentCategoryId;
        this.parentCloudId = parentCloudId;
        this.ownerId = ownerId;
        this.name = name;
        this.iconUri = iconUri;
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_CREATE.getValue();
        this.order = 0;
    }

    // -------------------------------------------------------------------------
    // 静态工厂
    // -------------------------------------------------------------------------

    /** 创建 UI 列表中的"+ 添加"占位项（不写入数据库） */
    public static SubCategory createAddButton() {
        SubCategory sub = new SubCategory();
        sub.isAddButton = true;
        sub.name = "添加";
        sub.iconUri = "ic_add_circle";
        return sub;
    }

    // -------------------------------------------------------------------------
    // Getter / Setter
    // -------------------------------------------------------------------------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(long parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public String getParentCloudId() { return parentCloudId; }
    public void setParentCloudId(String parentCloudId) { this.parentCloudId = parentCloudId; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

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

    public boolean isAddButton() { return isAddButton; }
    public void setAddButton(boolean addButton) { isAddButton = addButton; }

    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------

    /** 标记为待更新，刷新时间戳 */
    public void markUpdatedForSync() {
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_UPDATE.getValue();
    }

    /** 标记为待删除，刷新时间戳 */
    public void markDeletedForSync() {
        this.updatedAt = System.currentTimeMillis();
        this.syncState = SyncState.TO_DELETE.getValue();
    }

    /**
     * 检查此子分类是否可以上传云端
     * 条件：父分类必须已同步（即 parentCloudId 非空）
     */
    public boolean isReadyToUpload() {
        return parentCloudId != null && !parentCloudId.isEmpty();
    }

    @Override
    public String toString() {
        return "SubCategory{id=" + id + ", parentCategoryId=" + parentCategoryId
                + ", parentCloudId='" + parentCloudId + "', cloudId='" + cloudId
                + "', name='" + name + "', syncState=" + syncState + '}';
    }
}