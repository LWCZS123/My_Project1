package com.example.my_project1.data.remote.model.cloudbill;

import android.util.Log;

import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.datatype.BmobDate;
import cn.bmob.v3.datatype.BmobPointer;

/**
 * CloudBill - 云端账单模型
 * -------------------------------------------------------
 * 对应 Bmob 云端 Bill 表
 * 功能：
 *  - 本地 Bill 实体与云端模型的转换
 *  - 支持关联用户、账本、账户、分类
 */
public class CloudBill extends BmobObject {

    private static final String TAG = "CloudBill";

    private BmobPointer user;          // 关联用户
    private BmobPointer book;          // 关联账本
    private BmobPointer account;       // 关联账户
    private String categoryId;         // 分类ID
    private String categoryName;       // 分类名称（冗余字段）
    private String categoryIconUrl;    // 分类图标URL
    private Double amount;             // 金额
    private Integer type;              // 0支出 1收入
    private Boolean excludeBudget;     // 是否不计入预算
    private String remark;             // 备注
    private BmobDate billTime;         // 账单时间
    private List<String> imageUrls;    // 图片URL
    private String location;           // 地点

    // ======================== Getter & Setter ========================

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public BmobPointer getBook() { return book; }
    public void setBook(BmobPointer book) { this.book = book; }

    public BmobPointer getAccount() { return account; }
    public void setAccount(BmobPointer account) { this.account = account; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryIconUrl() { return categoryIconUrl; }
    public void setCategoryIconUrl(String categoryIconUrl) { this.categoryIconUrl = categoryIconUrl; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Boolean getExcludeBudget() { return excludeBudget; }
    public void setExcludeBudget(Boolean excludeBudget) { this.excludeBudget = excludeBudget; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public BmobDate getBillTime() {
        return billTime;
    }

    public void setBillTime(BmobDate billTime) {
        this.billTime = billTime;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // ======================== 便捷方法 ========================

    /** 获取本地用户ID */
    public String getUserId() {
        return user != null ? user.getObjectId() : null;
    }

    /** 获取本地账本ID */
    public String getBookId() {
        return book != null ? book.getObjectId() : null;
    }

    /** 获取本地账户ID */
    public String getAccountId() {
        return account != null ? account.getObjectId() : null;
    }

    // ======================== 转换方法 ========================

    /**
     * 🔴 BmobDate 转换为 Date 的工具方法
     * BmobDate.getDate() 返回的是 ISO 8601 格式的字符串
     * 例如: "2025-12-13 20:24:55"
     */
    private Date convertBmobDateToDate(BmobDate bmobDate) {
        if (bmobDate == null) {
            return null;
        }

        try {
            String dateStr = bmobDate.getDate();
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }

            // Bmob 返回的日期格式: "yyyy-MM-dd HH:mm:ss"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(dateStr);

            Log.d(TAG, "BmobDate 转换成功: " + dateStr + " -> " + date);
            return date;

        } catch (Exception e) {
            Log.e(TAG, "BmobDate 转换失败: " + e.getMessage(), e);

            // 尝试其他可能的格式
            try {
                String dateStr = bmobDate.getDate();
                // ISO 8601 格式: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                return isoFormat.parse(dateStr);
            } catch (Exception ex) {
                Log.e(TAG, "备用格式转换也失败", ex);
                return null;
            }
        }
    }

    /**
     * 🔴 云端 → 本地 (完全修复版本)
     * 将云端 CloudBill 转换为本地 Bill 实体
     */
    public Bill toLocalEntity() {
        Bill local = new Bill();

        // 基本字段
        local.setObjectId(getObjectId());
        local.setUserId(getUserId());
        local.setBookId(getBookId());
        local.setAccountId(getAccountId());
        local.setCategoryId(categoryId);
        local.setCategoryName(categoryName);
        local.setCategoryIconUrl(categoryIconUrl);
        local.setAmount(amount != null ? amount : 0);
        local.setType(type != null ? type : 0);
        local.setExcludeBudget(excludeBudget != null && excludeBudget);
        local.setRemark(remark);
        local.setImageUrls(imageUrls);
        local.setLocation(location);

        // 🔴 关键修复: BmobDate 转换为 Date
        if (billTime != null) {
            Date date = convertBmobDateToDate(billTime);
            if (date != null) {
                local.setBillTime(date);
                Log.d(TAG, "账单时间转换成功 - objectId=" + getObjectId() + ", billTime=" + date);
            } else {
                // 如果转换失败，使用当前时间
                Log.w(TAG, "账单时间转换失败，使用当前时间 - objectId=" + getObjectId());
                local.setBillTime(new Date());
            }
        } else {
            // 如果 billTime 为空，使用当前时间
            Log.w(TAG, "账单时间为空，使用当前时间 - objectId=" + getObjectId());
            local.setBillTime(new Date());
        }

        // 时间字段 - createdAt 和 updatedAt
        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));

        return local;
    }

    /**
     * 本地 → 云端
     * 将本地 Bill 实体转换为云端 CloudBill
     */
    public static CloudBill fromLocal(Bill local) {
        CloudBill cloud = new CloudBill();

        // 设置 objectId（如果存在）
        if (local.getObjectId() != null) {
            cloud.setObjectId(local.getObjectId());
        }

        // 基本字段
        cloud.setCategoryId(local.getCategoryId());
        cloud.setCategoryName(local.getCategoryName());
        cloud.setCategoryIconUrl(local.getCategoryIconUrl());
        cloud.setAmount(local.getAmount());
        cloud.setType(local.getType());
        cloud.setExcludeBudget(local.isExcludeBudget());
        cloud.setRemark(local.getRemark());
        cloud.setImageUrls(local.getImageUrls());
        cloud.setLocation(local.getLocation());


        // 🔴 时间字段: Date → BmobDate
        if (local.getBillTime() != null) {
            cloud.setBillTime(new BmobDate(local.getBillTime()));
        } else {
            // 如果本地时间为空，使用当前时间
            Log.w(TAG, "本地账单时间为空，使用当前时间");
            cloud.setBillTime(new BmobDate(new Date()));
        }

        // 关联字段（使用 BmobPointer）
        if (local.getUserId() != null) {
            cloud.setUser(BmobPointerUtil.user(local.getUserId()));
        }

        /**
         * 账本功能待实现
         if (local.getBookId() != null) {
         cloud.setBook(BmobPointerUtil.book(local.getBookId()));
         }
         */

        if (local.getAccountId() != null) {
            cloud.setAccount(BmobPointerUtil.account(local.getAccountId()));
        }

        return cloud;
    }

    @Override
    public String toString() {
        return "CloudBill{" +
                "objectId='" + getObjectId() + '\'' +
                ", userId='" + getUserId() + '\'' +
                ", bookId='" + getBookId() + '\'' +
                ", accountId='" + getAccountId() + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", categoryName='" + categoryName + '\'' +
                ", amount=" + amount +
                ", type=" + type +
                ", excludeBudget=" + excludeBudget +
                ", remark='" + remark + '\'' +
                ", billTime='" + billTime + '\'' +
                ", location='" + location + '\'' +
                '}';
    }
}