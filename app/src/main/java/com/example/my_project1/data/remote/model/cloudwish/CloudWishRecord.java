package com.example.my_project1.data.remote.model.cloudwish;

import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * 云端 WishRecord 实体
 */
public class CloudWishRecord extends BmobObject {

    private BmobPointer user;
    private BmobPointer wish;
    private Double amount;
    private String note;
    private BmobDate recordDate; // 这是一个 BmobDate 对象
    private String wishObjectId;
    private String linkedBillObjectId;
    private String clientKey;

    public CloudWishRecord() {}

    // Getter & Setter ...
    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }
    public BmobPointer getWish() { return wish; }
    public void setWish(BmobPointer wish) { this.wish = wish; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public BmobDate getRecordDate() { return recordDate; }
    public void setRecordDate(BmobDate recordDate) { this.recordDate = recordDate; }
    public String getWishObjectId() { return wishObjectId; }
    public void setWishObjectId(String wishObjectId) { this.wishObjectId = wishObjectId; }
    public String getLinkedBillObjectId() { return linkedBillObjectId; }
    public void setLinkedBillObjectId(String linkedBillObjectId) { this.linkedBillObjectId = linkedBillObjectId; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String clientKey) { this.clientKey = clientKey; }

    /**
     * 本地 → 云端
     */
    public static CloudWishRecord fromLocal(WishRecord local) {
        if (local == null) return null;
        CloudWishRecord cloud = new CloudWishRecord();
        if (local.getObjectId() != null) cloud.setObjectId(local.getObjectId());
        cloud.setAmount(local.getAmount());
        cloud.setNote(local.getNote());

        if (local.getRecordDate() != null) {
            cloud.setRecordDate(new BmobDate(local.getRecordDate()));
        }
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        }
        if (local.getWishObjectId() != null) {
            cloud.setWish(BmobPointerUtil.wish(local.getWishObjectId()));
            cloud.setWishObjectId(local.getWishObjectId());
        }
        cloud.setLinkedBillObjectId(local.getLinkedBillObjectId());
        return cloud;
    }

    /**
     * 云端 → 本地 (已修复日期转换逻辑)
     */
    public WishRecord toLocalEntity() {
        WishRecord local = new WishRecord();
        local.setObjectId(getObjectId());

        if (user != null) local.setUserId(user.getObjectId());

        if (wish != null) {
            local.setWishObjectId(wish.getObjectId());
        } else if (wishObjectId != null) {
            local.setWishObjectId(wishObjectId);
        }

        local.setAmount(amount != null ? amount : 0d);
        local.setNote(note);
        local.setLinkedBillObjectId(linkedBillObjectId);

        // BmobDate 需要先取出服务端日期字符串，再交给统一日期转换器处理。
        if (recordDate != null) {
            // recordDate.getDate() 返回 "yyyy-MM-dd HH:mm:ss" 格式的字符串
            local.setRecordDate(DateConvertUtil.safeConvertToDate(recordDate.getDate()));
        }

        // getCreatedAt() 本身就是 String，直接转换即可
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));

        return local;
    }
}
