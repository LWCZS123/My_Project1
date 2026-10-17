package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobPointer;

public class CloudTagGroup extends BmobObject {

    private BmobPointer user;
    private String name;
    private int sortOrder;
    private boolean isDefault;
    private boolean isDeleted;

    public CloudTagGroup() {
        this.setTableName("TagGroup");
    }

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

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

    public static CloudTagGroup fromLocal(TagGroupEntity local) {
        CloudTagGroup cloud = new CloudTagGroup();
        if (local.getObjectId() != null && !local.getObjectId().isEmpty()) {
            cloud.setObjectId(local.getObjectId());
        }
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        }
        cloud.setName(local.getName());
        cloud.setSortOrder(local.getSortOrder());
        cloud.setDefault(local.isDefault());
        cloud.setDeleted(local.isDeleted());
        return cloud;
    }
}
