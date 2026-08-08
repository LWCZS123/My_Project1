package com.example.my_project1.data.remote.model;

import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SyncState;

import cn.bmob.v3.BmobObject;

/**
 * 云端 Category 对象映射类（对应 Bmob 表 "Category"）
 *
 * 字段说明：
 *  - objectId：Bmob 自动生成的云端主键
 *  - localId：本地数据库的 id（long），用于同步匹配
 *  - ownerId：所属用户（BmobUser.objectId）
 *  - type：分类类型（支出/收入）
 *  - name：分类名称
 *  - iconUri：图标资源（url 或本地资源标识）
 *  - color：颜色（可选）
 *  - order：排序
 *  - createdAt / updatedAt：Bmob 自动维护
 */
public class CloudCategory extends BmobObject {

    private long localId;    // 本地 Room 分类ID（关键字段）
    private String ownerId;  // 用户ID（BmobUser.objectId）
    private String type;     // "支出" 或 "收入"
    private String name;     // 分类名
    private String iconUri;  // 图标URI
    private String iconBackgroundColor; // 图标背景色
    private String color;    // 可选颜色
    private int order;       // 排序序号
    private boolean isSystemPreset = false; //系统预设分类
    private boolean excludeBudget = false; // 是否计入预算

    private Integer archiveStatus = 0; // 0=正常，1=已归档
    private Long archiveTime;
    private String parentId; // 父级ID

    // ----- Getter and Setter -----

    public long getLocalId() {
        return localId;
    }

    public void setLocalId(long localId) {
        this.localId = localId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUri() {
        return iconUri;
    }

    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    public String getIconBackgroundColor() {
        return iconBackgroundColor;
    }

    public void setIconBackgroundColor(String iconBackgroundColor) {
        this.iconBackgroundColor = iconBackgroundColor;
    }

    public boolean isExcludeBudget() {
        return excludeBudget;
    }

    public void setExcludeBudget(boolean excludeBudget) {
        this.excludeBudget = excludeBudget;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isSystemPreset() {
        return isSystemPreset;
    }

    public void setSystemPreset(boolean systemPreset) {
        isSystemPreset = systemPreset;
    }

    public Integer getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(Integer archiveStatus) { this.archiveStatus = archiveStatus; }

    public Long getArchiveTime() { return archiveTime; }
    public void setArchiveTime(Long archiveTime) { this.archiveTime = archiveTime; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public Category toLocalCategory() {
        Category local = new Category();
        local.setCloudId(getObjectId());
        local.setOwnerId(ownerId);
        local.setType(type);
        local.setName(name);
        local.setIconUri(iconUri);
        local.setIconBackgroundColor(iconBackgroundColor);
        local.setColor(color);
        local.setSortIndex(order);
        local.setUpdatedAt(System.currentTimeMillis());
        local.setSyncState(SyncState.SYNCED.getValue()); // 标记为已同步
        local.setSystemPreset(false); // 默认不是系统预设
        local.setExcludeBudget(excludeBudget);
        local.setArchiveStatus(archiveStatus != null ? archiveStatus : 0);
        local.setArchiveTime(archiveTime);
        local.setParentId(parentId != null ? parentId : "0");
        return local;
    }
    public static CloudCategory fromLocalCategory(Category local) {
        CloudCategory cloud = new CloudCategory();

        // 本地与云端的同步字段
        cloud.setLocalId(local.getId());
        cloud.setOwnerId(local.getOwnerId());
        cloud.setType(local.getType());
        cloud.setName(local.getName());
        cloud.setIconUri(local.getIconUri());
        cloud.setIconBackgroundColor(local.getIconBackgroundColor());
        cloud.setExcludeBudget(local.isExcludeBudget());
        cloud.setColor(local.getColor());
        cloud.setOrder(local.getSortIndex());
        cloud.setArchiveStatus(local.getArchiveStatus());
        cloud.setArchiveTime(local.getArchiveTime());
        cloud.setParentId(local.getParentId());

        // 创建时间、更新时间由 Bmob 维护
        return cloud;
    }
}
