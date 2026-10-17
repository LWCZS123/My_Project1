package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.BillTagCrossRef;
import com.example.my_project1.utils.BmobPointerUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobPointer;

public class CloudBillTagCrossRef extends BmobObject {

    private BmobPointer user;
    private String billObjectId;
    private String tagObjectId;
    private String tagName;
    private String tagBgColor;
    private String tagTextColor;
    private boolean isDeleted;

    public CloudBillTagCrossRef() {
        this.setTableName("BillTagCrossRef");
    }

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public String getBillObjectId() { return billObjectId; }
    public void setBillObjectId(String billObjectId) { this.billObjectId = billObjectId; }

    public String getTagObjectId() { return tagObjectId; }
    public void setTagObjectId(String tagObjectId) { this.tagObjectId = tagObjectId; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getTagBgColor() { return tagBgColor; }
    public void setTagBgColor(String tagBgColor) { this.tagBgColor = tagBgColor; }

    public String getTagTextColor() { return tagTextColor; }
    public void setTagTextColor(String tagTextColor) { this.tagTextColor = tagTextColor; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public BillTagCrossRef toLocalEntity(long localBillId, long localTagId) {
        BillTagCrossRef local = new BillTagCrossRef();
        local.setBillId(localBillId);
        local.setTagId(localTagId);
        local.setUserId(user != null ? user.getObjectId() : null);
        local.setTagName(tagName);
        local.setTagBgColor(tagBgColor);
        local.setTagTextColor(tagTextColor);
        local.setDeleted(isDeleted);
        local.setSyncState(SyncState.SYNCED);
        return local;
    }

    public static CloudBillTagCrossRef fromLocal(BillTagCrossRef local, String billObjectId, String tagObjectId) {
        CloudBillTagCrossRef cloud = new CloudBillTagCrossRef();
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        }
        cloud.setBillObjectId(billObjectId);
        cloud.setTagObjectId(tagObjectId);
        cloud.setTagName(local.getTagName());
        cloud.setTagBgColor(local.getTagBgColor());
        cloud.setTagTextColor(local.getTagTextColor());
        cloud.setDeleted(local.isDeleted());
        return cloud;
    }
}
