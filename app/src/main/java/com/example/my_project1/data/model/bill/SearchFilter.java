package com.example.my_project1.data.model.bill;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SearchFilter - 统一搜索筛选条件
 */
public class SearchFilter implements Serializable {

    private String keyword;
    private Integer billType; // -1全部, 0支出, 1收入, 2转账
    private String categoryId;
    private String categoryName;
    private List<String> accountIds;
    private String accountSummary; // 用于回显显示的名称
    private Date startDate;
    private Date endDate;
    private Double minAmount;
    private Double maxAmount;
    private Boolean includeBudget; // null=不限, true=计入, false=不计入
    private Boolean includeIncomeExpense; // null=不限

    public SearchFilter() {
        this.billType = -1;
        this.accountIds = new ArrayList<>();
    }

    // Getters and Setters
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getBillType() { return billType; }
    public void setBillType(Integer billType) { this.billType = billType; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public List<String> getAccountIds() { return accountIds; }
    public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }

    public String getAccountSummary() { return accountSummary; }
    public void setAccountSummary(String accountSummary) { this.accountSummary = accountSummary; }

    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public Double getMinAmount() { return minAmount; }
    public void setMinAmount(Double minAmount) { this.minAmount = minAmount; }

    public Double getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Double maxAmount) { this.maxAmount = maxAmount; }

    public Boolean getIncludeBudget() { return includeBudget; }
    public void setIncludeBudget(Boolean includeBudget) { this.includeBudget = includeBudget; }

    public Boolean getIncludeIncomeExpense() { return includeIncomeExpense; }
    public void setIncludeIncomeExpense(Boolean includeIncomeExpense) { this.includeIncomeExpense = includeIncomeExpense; }

    public boolean hasAnyFilter() {
        return (keyword != null && !keyword.trim().isEmpty())
                || (billType != null && billType != -1)
                || (categoryId != null && !categoryId.isEmpty())
                || (accountIds != null && !accountIds.isEmpty())
                || startDate != null
                || endDate != null
                || minAmount != null
                || maxAmount != null
                || includeBudget != null
                || includeIncomeExpense != null;
    }

    public void clear() {
        keyword = null;
        billType = -1;
        categoryId = null;
        categoryName = null;
        accountIds = new ArrayList<>();
        accountSummary = null;
        startDate = null;
        endDate = null;
        minAmount = null;
        maxAmount = null;
        includeBudget = null;
        includeIncomeExpense = null;
    }
}
