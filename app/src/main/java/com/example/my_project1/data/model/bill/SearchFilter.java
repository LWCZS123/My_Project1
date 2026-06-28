package com.example.my_project1.data.model.bill;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SearchFilter - 搜索筛选条件模型 (增强版)
 * -------------------------------------------------------
 * ✅ 功能:
 * 1. 日期范围筛选
 * 2. 金额范围筛选
 * 3. 备注关键词筛选
 * 4. 多账户筛选 (新增)
 * 5. 账本筛选
 * 6. 分类筛选
 */
public class SearchFilter {

    // ==================== 基础筛选条件 ====================

    /** 开始日期 */
    private Date startDate;

    /** 结束日期 */
    private Date endDate;

    /** 最低金额 */
    private Double minAmount;

    /** 最高金额 */
    private Double maxAmount;

    /** 备注关键词 */
    private String remarkKeyword;

    // ==================== 账户筛选 (支持多账户) ====================

    /** 🔥 新增：多个账户ID列表 */
    private List<String> accountIds;

    /** 单个账户ID (兼容旧代码) */
    private String accountId;

    /** 账户名称 (用于显示) */
    private String accountName;

    // ==================== 其他筛选条件 ====================

    /** 账本ID */
    private String bookId;

    /** 分类ID */
    private String categoryId;

    /** 账单类型 (0=支出, 1=收入) */
    private Integer billType;

    // ==================== 构造函数 ====================

    public SearchFilter() {
        this.accountIds = new ArrayList<>();
    }

    // ==================== Getter & Setter ====================

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Double getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(Double minAmount) {
        this.minAmount = minAmount;
    }

    public Double getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(Double maxAmount) {
        this.maxAmount = maxAmount;
    }

    public String getRemarkKeyword() {
        return remarkKeyword;
    }

    public void setRemarkKeyword(String remarkKeyword) {
        this.remarkKeyword = remarkKeyword;
    }

    // 🔥 新增：多账户相关方法

    public List<String> getAccountIds() {
        return accountIds;
    }

    public void setAccountIds(List<String> accountIds) {
        this.accountIds = accountIds != null ? accountIds : new ArrayList<>();
    }

    public void addAccountId(String accountId) {
        if (accountId != null && !accountIds.contains(accountId)) {
            accountIds.add(accountId);
        }
    }

    public void removeAccountId(String accountId) {
        accountIds.remove(accountId);
    }

    public boolean hasMultipleAccounts() {
        return accountIds != null && accountIds.size() > 1;
    }

    // 兼容旧代码的方法

    public String getAccountId() {
        // 如果有多个账户ID，返回第一个
        if (accountIds != null && !accountIds.isEmpty()) {
            return accountIds.get(0);
        }
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
        // 同步到accountIds列表
        if (accountId != null) {
            accountIds.clear();
            accountIds.add(accountId);
        }
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getBillType() {
        return billType;
    }

    public void setBillType(Integer billType) {
        this.billType = billType;
    }

    // ==================== 工具方法 ====================

    /**
     * 判断是否有任何筛选条件
     */
    public boolean hasAnyFilter() {
        return startDate != null
                || endDate != null
                || minAmount != null
                || maxAmount != null
                || remarkKeyword != null
                || (accountIds != null && !accountIds.isEmpty())
                || bookId != null
                || categoryId != null
                || billType != null;
    }

    /**
     * 清空所有筛选条件
     */
    public void clear() {
        startDate = null;
        endDate = null;
        minAmount = null;
        maxAmount = null;
        remarkKeyword = null;
        accountIds = new ArrayList<>();
        accountId = null;
        accountName = null;
        bookId = null;
        categoryId = null;
        billType = null;
    }

    /**
     * 获取筛选条件描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();

        if (startDate != null || endDate != null) {
            sb.append("日期范围 ");
        }
        if (minAmount != null || maxAmount != null) {
            sb.append("金额范围 ");
        }
        if (remarkKeyword != null) {
            sb.append("备注 ");
        }
        if (accountIds != null && !accountIds.isEmpty()) {
            sb.append(accountIds.size()).append("个账户 ");
        }
        if (bookId != null) {
            sb.append("账本 ");
        }
        if (categoryId != null) {
            sb.append("分类 ");
        }

        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "SearchFilter{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                ", minAmount=" + minAmount +
                ", maxAmount=" + maxAmount +
                ", remarkKeyword='" + remarkKeyword + '\'' +
                ", accountIds=" + accountIds +
                ", accountId='" + accountId + '\'' +
                ", accountName='" + accountName + '\'' +
                ", bookId='" + bookId + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", billType=" + billType +
                '}';
    }
}