package com.example.my_project1.data.remote.model.cloudbill;

import android.util.Log;

import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.utils.BmobPointerUtil;
import com.example.my_project1.utils.DateConvertUtil;
import com.google.gson.Gson;

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
 *  - 精简云端 Bmob 字段列数，将非查询列整合为 JSON 存入 extraJson，规避 Bmob 字段上线限制(1004)
 */
public class CloudBill extends BmobObject {

    private static final String TAG = "CloudBill";

    // ======================== Bmob 顶层持久化列 (精简为9列) ========================
    private BmobPointer user;          // 关联用户
    private BmobPointer book;          // 关联账本
    private BmobPointer account;       // 关联账户 (转出)
    private BmobPointer toAccount;     // 转入账户 (用于转账)
    private String categoryId;         // 分类ID
    private Double amount;             // 金额
    private Integer type;              // 0支出 1收入
    private BmobDate billTime;         // 账单时间
    private String extraJson;          // 扩展字段 JSON 字符串

    // ======================== 旧版/次要 transient 字段 (不向 Bmob 新建独立列，仅供兼容旧数据) ========================
    private transient String categoryName;
    private transient String categoryIconUrl;
    private transient String categoryIconBackgroundColor;
    private transient Double discount;
    private transient Boolean excludeBudget;
    private transient Boolean excludeStat;
    private transient String remark;
    private transient List<String> imageUrls;
    private transient String location;

    // 报销相关 transient 字段
    private transient Boolean isReimbursable;
    private transient Boolean isReimbursed;
    private transient Double reimburseAmount;
    private transient BmobDate reimburseTime;
    private transient String reimburseAccountId;
    private transient String reimburseAccountName;
    private transient String reimburseRemark;

    /**
     * 扩展字段结构（打包存入 extraJson）
     */
    public static class ExtraData {
        public String categoryName;
        public String categoryIconUrl;
        public String categoryIconBackgroundColor;
        public Double discount;
        public Boolean excludeBudget;
        public Boolean excludeStat;
        public String remark;
        public List<String> imageUrls;
        public String location;
        public Long sourceWishId;

        // 报销相关
        public Boolean isReimbursable;
        public Boolean isReimbursed;
        public Double reimburseAmount;
        public Long reimburseTime;
        public String reimburseAccountId;
        public String reimburseAccountName;
        public String reimburseRemark;
    }

    // ======================== 安全 Log 工具函数 (兼容 JVM 单元测试) ========================
    private static void logD(String tag, String msg) {
        try { Log.d(tag, msg); } catch (Throwable ignored) {}
    }

    private static void logW(String tag, String msg) {
        try { Log.w(tag, msg); } catch (Throwable ignored) {}
    }

    private static void logE(String tag, String msg, Throwable t) {
        try { Log.e(tag, msg, t); } catch (Throwable ignored) {}
    }

    // ======================== Getter & Setter ========================

    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }

    public Boolean getIsReimbursable() { return isReimbursable; }
    public void setIsReimbursable(Boolean isReimbursable) { this.isReimbursable = isReimbursable; }

    public Boolean getIsReimbursed() { return isReimbursed; }
    public void setIsReimbursed(Boolean isReimbursed) { this.isReimbursed = isReimbursed; }

    public Double getReimburseAmount() { return reimburseAmount; }
    public void setReimburseAmount(Double reimburseAmount) { this.reimburseAmount = reimburseAmount; }

    public BmobDate getReimburseTime() { return reimburseTime; }
    public void setReimburseTime(BmobDate reimburseTime) { this.reimburseTime = reimburseTime; }

    public String getReimburseAccountId() { return reimburseAccountId; }
    public void setReimburseAccountId(String reimburseAccountId) { this.reimburseAccountId = reimburseAccountId; }

    public String getReimburseAccountName() { return reimburseAccountName; }
    public void setReimburseAccountName(String reimburseAccountName) { this.reimburseAccountName = reimburseAccountName; }

    public String getReimburseRemark() { return reimburseRemark; }
    public void setReimburseRemark(String reimburseRemark) { this.reimburseRemark = reimburseRemark; }

    public BmobPointer getUser() { return user; }
    public void setUser(BmobPointer user) { this.user = user; }

    public BmobPointer getBook() { return book; }
    public void setBook(BmobPointer book) { this.book = book; }

    public BmobPointer getAccount() { return account; }
    public void setAccount(BmobPointer account) { this.account = account; }

    public BmobPointer getToAccount() { return toAccount; }
    public void setToAccount(BmobPointer toAccount) { this.toAccount = toAccount; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryIconUrl() { return categoryIconUrl; }
    public void setCategoryIconUrl(String categoryIconUrl) { this.categoryIconUrl = categoryIconUrl; }

    public String getCategoryIconBackgroundColor() { return categoryIconBackgroundColor; }
    public void setCategoryIconBackgroundColor(String categoryIconBackgroundColor) { this.categoryIconBackgroundColor = categoryIconBackgroundColor; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public Double getDiscount() { return discount; }
    public void setDiscount(Double discount) { this.discount = discount; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public Boolean getExcludeBudget() { return excludeBudget; }
    public void setExcludeBudget(Boolean excludeBudget) { this.excludeBudget = excludeBudget; }

    public Boolean getExcludeStat() { return excludeStat; }
    public void setExcludeStat(Boolean excludeStat) { this.excludeStat = excludeStat; }

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

    public String getUserId() {
        return user != null ? user.getObjectId() : null;
    }

    public String getBookId() {
        return book != null ? book.getObjectId() : null;
    }

    public String getAccountId() {
        return account != null ? account.getObjectId() : null;
    }

    public String getToAccountId() {
        return toAccount != null ? toAccount.getObjectId() : null;
    }

    // ======================== 转换方法 ========================

    /**
     * BmobDate 转换为 Date 的工具方法
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

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(dateStr);

            logD(TAG, "BmobDate 转换成功: " + dateStr + " -> " + date);
            return date;

        } catch (Exception e) {
            logE(TAG, "BmobDate 转换失败: " + e.getMessage(), e);

            try {
                String dateStr = bmobDate.getDate();
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                return isoFormat.parse(dateStr);
            } catch (Exception ex) {
                logE(TAG, "备用格式转换也失败", ex);
                return null;
            }
        }
    }

    /**
     * 云端 → 本地
     * 将云端 CloudBill 转换为本地 Bill 实体
     */
    public Bill toLocalEntity() {
        Bill local = new Bill();

        local.setObjectId(getObjectId());
        local.setUserId(getUserId());
        local.setBookId(getBookId());
        local.setAccountId(getAccountId());
        local.setToAccountId(getToAccountId());
        local.setCategoryId(categoryId);
        local.setAmount(amount != null ? amount : 0);
        local.setType(type != null ? type : 0);

        if (billTime != null) {
            Date date = convertBmobDateToDate(billTime);
            if (date != null) {
                local.setBillTime(date);
                logD(TAG, "账单时间转换成功 - objectId=" + getObjectId() + ", billTime=" + date);
            } else {
                logW(TAG, "账单时间转换失败，使用当前时间 - objectId=" + getObjectId());
                local.setBillTime(new Date());
            }
        } else {
            logW(TAG, "账单时间为空，使用当前时间 - objectId=" + getObjectId());
            local.setBillTime(new Date());
        }

        local.setCreatedAt(DateConvertUtil.safeConvertToDate(getCreatedAt()));
        local.setUpdatedAt(DateConvertUtil.safeConvertToDate(getUpdatedAt()));

        // 解析 extraJson 扩展结构
        ExtraData extra = null;
        if (extraJson != null && !extraJson.trim().isEmpty()) {
            try {
                extra = new Gson().fromJson(extraJson, ExtraData.class);
            } catch (Exception e) {
                logE(TAG, "解析 extraJson 失败: " + e.getMessage(), e);
            }
        }

        if (extra != null) {
            local.setCategoryName(extra.categoryName);
            local.setCategoryIconUrl(extra.categoryIconUrl);
            local.setCategoryIconBackgroundColor(extra.categoryIconBackgroundColor);
            local.setDiscount(extra.discount != null ? extra.discount : 0.0);
            local.setExcludeBudget(extra.excludeBudget != null && extra.excludeBudget);
            local.setExcludeStat(extra.excludeStat != null && extra.excludeStat);
            local.setRemark(extra.remark);
            local.setImageUrls(extra.imageUrls);
            local.setLocation(extra.location);
            if (extra.sourceWishId != null) {
                local.setSourceWishId(extra.sourceWishId);
            }

            local.setReimbursable(extra.isReimbursable != null && extra.isReimbursable);
            local.setReimbursed(extra.isReimbursed != null && extra.isReimbursed);
            local.setReimburseAmount(extra.reimburseAmount != null ? extra.reimburseAmount : 0.0);
            if (extra.reimburseTime != null && extra.reimburseTime > 0) {
                local.setReimburseTime(new Date(extra.reimburseTime));
            }
            local.setReimburseAccountId(extra.reimburseAccountId);
            local.setReimburseAccountName(extra.reimburseAccountName);
            local.setReimburseRemark(extra.reimburseRemark);
        } else {
            // 兼容无 extraJson 的历史记录
            local.setCategoryName(categoryName);
            local.setCategoryIconUrl(categoryIconUrl);
            local.setCategoryIconBackgroundColor(categoryIconBackgroundColor);
            local.setDiscount(discount != null ? discount : 0.0);
            local.setExcludeBudget(excludeBudget != null && excludeBudget);
            local.setExcludeStat(excludeStat != null && excludeStat);
            local.setRemark(remark);
            local.setImageUrls(imageUrls);
            local.setLocation(location);

            local.setReimbursable(isReimbursable != null && isReimbursable);
            local.setReimbursed(isReimbursed != null && isReimbursed);
            local.setReimburseAmount(reimburseAmount != null ? reimburseAmount : 0.0);
            if (reimburseTime != null) {
                local.setReimburseTime(convertBmobDateToDate(reimburseTime));
            }
            local.setReimburseAccountId(reimburseAccountId);
            local.setReimburseAccountName(reimburseAccountName);
            local.setReimburseRemark(reimburseRemark);
        }

        return local;
    }

    /**
     * 本地 → 云端
     * 将本地 Bill 实体转换为云端 CloudBill
     */
    public static CloudBill fromLocal(Bill local) {
        CloudBill cloud = new CloudBill();

        if (local.getObjectId() != null && !local.getObjectId().trim().isEmpty()) {
            cloud.setObjectId(local.getObjectId());
        }

        cloud.setCategoryId(local.getCategoryId());
        cloud.setAmount(local.getAmount());
        cloud.setType(local.getType());

        if (local.getBillTime() != null) {
            cloud.setBillTime(new BmobDate(local.getBillTime()));
        } else {
            logW(TAG, "本地账单时间为空，使用当前时间");
            cloud.setBillTime(new BmobDate(new Date()));
        }

        if (local.getUserId() != null && !local.getUserId().isEmpty()) {
            BmobPointer userPointer = BmobPointerUtil.user(local.getUserId());
            if (userPointer != null) {
                cloud.setUser(userPointer);
            }
        }

        if (local.getBookId() != null && !local.getBookId().isEmpty()) {
            BmobPointer bookPointer = BmobPointerUtil.book(local.getBookId());
            if (bookPointer != null) {
                cloud.setBook(bookPointer);
            }
        }

        if (local.getAccountId() != null && !local.getAccountId().isEmpty()) {
            BmobPointer accountPointer = BmobPointerUtil.account(local.getAccountId());
            if (accountPointer != null) {
                cloud.setAccount(accountPointer);
            }
        }

        if (local.getToAccountId() != null && !local.getToAccountId().isEmpty()) {
            BmobPointer toAccountPointer = BmobPointerUtil.account(local.getToAccountId());
            if (toAccountPointer != null) {
                cloud.setToAccount(toAccountPointer);
            }
        }

        // 打包次要扩展字段至 ExtraData JSON 结构
        ExtraData extra = new ExtraData();
        extra.categoryName = local.getCategoryName();
        extra.categoryIconUrl = local.getCategoryIconUrl();
        extra.categoryIconBackgroundColor = local.getCategoryIconBackgroundColor();
        extra.discount = local.getDiscount();
        extra.excludeBudget = local.isExcludeBudget();
        extra.excludeStat = local.isExcludeStat();
        extra.remark = local.getRemark();
        extra.imageUrls = local.getImageUrls();
        extra.location = local.getLocation();
        extra.sourceWishId = local.getSourceWishId();

        extra.isReimbursable = local.isReimbursable();
        extra.isReimbursed = local.isReimbursed();
        extra.reimburseAmount = local.getReimburseAmount();
        if (local.getReimburseTime() != null) {
            extra.reimburseTime = local.getReimburseTime().getTime();
        }
        extra.reimburseAccountId = local.getReimburseAccountId();
        extra.reimburseAccountName = local.getReimburseAccountName();
        extra.reimburseRemark = local.getReimburseRemark();

        cloud.setExtraJson(new Gson().toJson(extra));

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
                ", amount=" + amount +
                ", type=" + type +
                ", billTime='" + billTime + '\'' +
                ", extraJson='" + extraJson + '\'' +
                '}';
    }
}
