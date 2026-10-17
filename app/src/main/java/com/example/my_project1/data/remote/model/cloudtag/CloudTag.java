package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobPointer;

public class CloudTag extends BmobObject {

    private BmobPointer user;
    private String groupObjectId;
    private String name;
    private String bgColor;
    private String textColor;
    private int sortOrder;
    private boolean isArchived;
    private boolean isDeleted;

    public CloudTag() {
        this.setTableName("Tag");
    }

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

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

    public static CloudTag fromLocal(TagEntity local, String groupObjectId) {
        CloudTag cloud = new CloudTag();
        if (local.getObjectId() != null && !local.getObjectId().isEmpty()) {
            cloud.setObjectId(local.getObjectId());
        }
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
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
