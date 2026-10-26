package com.example.my_project1.data.remote.model.cloudtag;

import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.tag.BillTagCrossRef;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端账单标签关联模型类（对应 Bmob 后台的 BillTagCrossRef 表）
 */
public class CloudBillTagCrossRef extends BmobObject {

    /** 所属用户的 Pointer 指针 */
    private BmobUser user;

    /** 关联账单的云端 objectId */
    private String billObjectId;

    /** 关联标签的云端 objectId */
    private String tagObjectId;

    /** 标签名称冗余字段 */
    private String tagName;

    /** 标签背景颜色冗余字段 */
    private String tagBgColor;

    /** 标签文字颜色冗余字段 */
    private String tagTextColor;

    /** 是否被逻辑删除 */
    private boolean isDeleted;

    /**
     * 构造函数，指定云端表名为 CloudBillTagCrossRef
     */
    public CloudBillTagCrossRef() {
        this.setTableName("CloudBillTagCrossRef");
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

    /**
     * 将云端账单标签关联模型转换为本地数据库实体类
     *
     * @param localBillId 本地账单ID
     * @param localTagId  本地标签ID
     * @return 本地账单标签关联实体
     */
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

    /**
     * 从本地账单标签关联实体转换为云端关联模型类
     *
     * @param local        本地账单标签关联实体
     * @param billObjectId 账单云端 objectId
     * @param tagObjectId  标签云端 objectId
     * @return 云端账单标签关联模型
     */
    public static CloudBillTagCrossRef fromLocal(BillTagCrossRef local, String billObjectId, String tagObjectId) {
        CloudBillTagCrossRef cloud = new CloudBillTagCrossRef();
        if (local.getUserId() != null && !local.getUserId().isEmpty()) {
            BmobUser user = new BmobUser();
            user.setObjectId(local.getUserId());
            cloud.setUser(user);
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
