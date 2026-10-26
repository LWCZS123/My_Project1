package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端标签分组模型类（对应 Bmob 后台的 TagGroup 表）
 */
public class CloudTagGroup extends BmobObject {

    /** 所属用户的 Pointer 指针 */
    private BmobUser user;

    /** 分组名称 */
    private String name;

    /** 排序顺序 */
    private int sortOrder;

    /** 是否为默认分组 */
    private boolean isDefault;

    /** 是否被逻辑删除 */
    private boolean isDeleted;

    /**
     * 构造函数，指定云端表名为 CloudTagGroup
     */
    public CloudTagGroup() {
        this.setTableName("CloudTagGroup");
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    /**
     * 将云端分组模型转换为本地数据库实体类
     *
     * @return 本地标签分组实体
     */
    public TagGroupEntity toLocalEntity() {
        TagGroupEntity local = new TagGroupEntity();
        local.setObjectId(getObjectId());
        local.setUserId(user != null ? user.getObjectId() : null);
        local.setName(name);
        local.setSortOrder(sortOrder);
        local.setDefault(isDefault);
        local.setDeleted(isDeleted);
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));
        local.setSyncState(SyncState.SYNCED);
        return local;
    }

    /**
     * 从本地数据库实体转换为云端分组模型类
     *
     * @param local 本地标签分组实体
     * @return 云端标签分组模型
     */
    public static CloudTagGroup fromLocal(TagGroupEntity local) {
        CloudTagGroup cloud = new CloudTagGroup();
        if (local.getObjectId() != null && !local.getObjectId().isEmpty()) {
            cloud.setObjectId(local.getObjectId());
        }
        if (local.getUserId() != null && !local.getUserId().isEmpty()) {
            BmobUser user = new BmobUser();
            user.setObjectId(local.getUserId());
            cloud.setUser(user);
        }
        cloud.setName(local.getName());
        cloud.setSortOrder(local.getSortOrder());
        cloud.setDefault(local.isDefault());
        cloud.setDeleted(local.isDeleted());
        return cloud;
    }
}
