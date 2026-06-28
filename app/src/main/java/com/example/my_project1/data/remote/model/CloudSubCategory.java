package com.example.my_project1.data.remote.model;

import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;

/**
 * CloudSubCategory — 重构版
 *
 * 重构要点：
 *  1. ★ parentCategory 使用 Bmob Pointer 关联云端父分类
 *       - 上传：创建一个只含 objectId 的 CloudCategory Pointer 对象
 *       - 查询：include("parentCategory") 后，可直接读取 parentCategory.objectId
 *  2. ★ 移除 parentCategoryId（本地 id）字段
 *       - 本地 id 在不同设备上不同，存储到云端会造成跨设备同步错乱
 *       - 下载时，通过 parentCategory.objectId 反查当前设备本地的 Category.id
 *  3. ★ 保留 parentCloudId（冗余字符串字段）用于 Bmob 查询过滤
 *       - Bmob Pointer 字段按 "field.objectId" 查询语法有时不稳定
 *       - 同时存储 parentCloudId 字符串便于 addWhereEqualTo("parentCloudId", id) 直接过滤
 *  4. toLocalSubCategory() 不再填充 parentCategoryId（交由 CategoryDownloadWorker 反查）
 */
public class CloudSubCategory extends BmobObject {

    /**
     * ★ 父分类 Pointer
     * - 上传时：new CloudCategory(); parent.setObjectId(cloudId); 仅设置 objectId
     * - 查询时：query.include("parentCategory") 展开后可读取完整对象
     */
    private CloudCategory parentCategory;

    /**
     * ★ 父分类 cloudId 冗余字符串字段
     * 用于 BmobQuery.addWhereEqualTo("parentCloudId", parentCloudId) 精确过滤
     * 与 parentCategory Pointer 保持同步写入
     */
    private String parentCloudId;

    /** 所属用户（BmobUser Pointer） */
    private BmobUser ownerId;

    /** 子分类名 */
    private String name;

    /** 图标地址或资源标识 */
    private String iconUri;

    /** 可选颜色 */
    private String color;

    /** 排序 */
    private int order;

    // -------------------------------------------------------------------------
    // Getter / Setter
    // -------------------------------------------------------------------------

    public CloudCategory getParentCategory() { return parentCategory; }
    public void setParentCategory(CloudCategory parentCategory) { this.parentCategory = parentCategory; }

    public String getParentCloudId() { return parentCloudId; }
    public void setParentCloudId(String parentCloudId) { this.parentCloudId = parentCloudId; }

    public BmobUser getOwnerId() { return ownerId; }
    public void setOwnerId(BmobUser ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIconUri() { return iconUri; }
    public void setIconUri(String iconUri) { this.iconUri = iconUri; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    // -------------------------------------------------------------------------
    // 云端 → 本地转换
    // -------------------------------------------------------------------------

    /**
     * 云端对象转本地 SubCategory
     *
     * ★ 注意：parentCategoryId 不在此处填充！
     * 调用方（CategoryDownloadWorker）负责根据 getEffectiveParentCloudId()
     * 反查本地数据库，找到对应的 Category.id 后再设置 parentCategoryId。
     *
     * @return 部分填充的 SubCategory（parentCategoryId = 0，需调用方补全）
     */
    public SubCategory toLocalSubCategory() {
        SubCategory local = new SubCategory();
        local.setCloudId(getObjectId());

        // ★ 优先从 Pointer 对象取 objectId，其次用冗余字符串字段
        local.setParentCloudId(getEffectiveParentCloudId());

        // parentCategoryId 刻意不设置，由 CategoryDownloadWorker 负责反查填充
        local.setOwnerId(ownerId != null ? ownerId.getObjectId() : null);
        local.setName(name);
        local.setIconUri(iconUri);
        local.setColor(color);
        local.setOrder(order);
        local.setUpdatedAt(System.currentTimeMillis());
        local.setSyncState(SyncState.SYNCED.getValue());
        local.setSystemPreset(false);
        return local;
    }

    // -------------------------------------------------------------------------
    // 本地 → 云端转换
    // -------------------------------------------------------------------------

    /**
     * 本地 SubCategory 转云端对象
     *
     * @param local         本地子分类
     * @param parentCloudId 父分类的云端 objectId（调用前必须确保非空）
     * @return 准备上传的 CloudSubCategory
     */
    public static CloudSubCategory fromLocalSubCategory(SubCategory local, String parentCloudId) {
        CloudSubCategory cloud = new CloudSubCategory();

        // ★ 同时设置 Pointer 和冗余字符串，两者保持一致
        if (parentCloudId != null && !parentCloudId.isEmpty()) {
            // Pointer：只需 objectId，Bmob 会自动处理关联
            CloudCategory parentPointer = new CloudCategory();
            parentPointer.setObjectId(parentCloudId);
            cloud.setParentCategory(parentPointer);

            // 冗余字符串，用于 BmobQuery 过滤
            cloud.setParentCloudId(parentCloudId);
        }

        // 用户 Pointer
        if (local.getOwnerId() != null) {
            BmobUser userPointer = new BmobUser();
            userPointer.setObjectId(local.getOwnerId());
            cloud.setOwnerId(userPointer);
        }

        // 基本字段
        cloud.setName(local.getName());
        cloud.setIconUri(local.getIconUri());
        cloud.setColor(local.getColor());
        cloud.setOrder(local.getOrder());

        return cloud;
    }

    // -------------------------------------------------------------------------
    // 辅助
    // -------------------------------------------------------------------------

    /**
     * 获取有效的父分类 cloudId
     * 优先从已展开的 Pointer 对象取，其次用冗余字符串字段
     */
    public String getEffectiveParentCloudId() {
        if (parentCategory != null && parentCategory.getObjectId() != null
                && !parentCategory.getObjectId().isEmpty()) {
            return parentCategory.getObjectId();
        }
        return parentCloudId;
    }
}