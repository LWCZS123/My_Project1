package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端标签模型类（对应 Bmob 后台的 Tag 表）
 */
public class CloudTag extends BmobObject {

    /** 所属用户的 Pointer 指针 */
    private BmobUser user;

    /** 所属标签分组的云端 objectId */
    private String groupObjectId;

    /** 标签名称 */
    private String name;

    /** 背景颜色十六进制字符串 */
    private String bgColor;

    /** 文字颜色十六进制字符串 */
    private String textColor;

    /** 排序顺序 */
    private int sortOrder;

    /** 是否归档 */
    private boolean isArchived;

    /** 是否被逻辑删除 */
    private boolean isDeleted;

    /**
     * 构造函数，指定云端表名为 CloudTag
     */
    public CloudTag() {
        this.setTableName("CloudTag");
    }

    public BmobUser getUser() { return user; }
    public void setUser(BmobUser user) { this.user = user; }
    public void setUser(BmobPointer user) {
        if (user != null) {
            BmobUser u = new BmobUser();
            u.setObjectId(user.getObjectId());
            this.user = u;
        } else {
            this.user = null;
        }
    }

    public String getGroupObjectId() { return groupObjectId; }
    public void setGroupObjectId(String groupObjectId) { this.groupObjectId = groupObjectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBgColor() { return bgColor; }
    public void setBgColor(String bgColor) { this.bgColor = bgColor; }

    public String getTextColor() { return textColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    /**
     * 将云端标签转换为本地标签数据库实体
     *
     * @param localGroupId 本地关联的分组ID
     * @return 本地标签实体
     */
    public TagEntity toLocalEntity(long localGroupId) {
        TagEntity local = new TagEntity();
        local.setObjectId(getObjectId());
        local.setUserId(user != null ? user.getObjectId() : null);
        local.setGroupId(localGroupId);
        local.setName(name);
        local.setBgColor(bgColor);
        local.setTextColor(textColor);
        local.setSortOrder(sortOrder);
        local.setArchived(isArchived);
        local.setDeleted(isDeleted);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        local.setSyncState(SyncState.SYNCED);
        return local;
    }

    /**
     * 从本地标签实体转换为云端标签模型类
     *
     * @param local         本地标签实体
     * @param groupObjectId 所属分组的云端 objectId
     * @return 云端标签模型
     */
    public static CloudTag fromLocal(TagEntity local, String groupObjectId) {
        CloudTag cloud = new CloudTag();
        if (local.getObjectId() != null && !local.getObjectId().isEmpty()) {
            cloud.setObjectId(local.getObjectId());
        }
        if (local.getUserId() != null && !local.getUserId().isEmpty()) {
            BmobUser user = new BmobUser();
            user.setObjectId(local.getUserId());
            cloud.setUser(user);
        }
        cloud.setGroupObjectId(groupObjectId);
        cloud.setName(local.getName());
        cloud.setBgColor(local.getBgColor());
        cloud.setTextColor(local.getTextColor());
        cloud.setSortOrder(local.getSortOrder());
        cloud.setArchived(local.isArchived());
        cloud.setDeleted(local.isDeleted());
        return cloud;
    }
}
