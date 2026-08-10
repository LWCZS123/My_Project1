package com.example.my_project1.data.model.bill;

import java.io.Serializable;

/**
 * SearchSummary - 搜索结果汇总模型
 */
public class SearchSummary implements Serializable {
    private double incomeTotal;
    private double expenseTotal;
    private double netAmount;
    private int billCount;
    private int billDays;

    public SearchSummary() {}

    public SearchSummary(double incomeTotal, double expenseTotal, double netAmount, int billCount, int billDays) {
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
        this.netAmount = netAmount;
        this.billCount = billCount;
        this.billDays = billDays;
    }

    public double getIncomeTotal() { return incomeTotal; }
    public void setIncomeTotal(double incomeTotal) { this.incomeTotal = incomeTotal; }

    public double getExpenseTotal() { return expenseTotal; }
    public void setExpenseTotal(double expenseTotal) { this.expenseTotal = expenseTotal; }

    public double getNetAmount() { return netAmount; }
    public void setNetAmount(double netAmount) { this.netAmount = netAmount; }

    public int getBillCount() { return billCount; }
    public void setBillCount(int billCount) { this.billCount = billCount; }

    public int getBillDays() { return billDays; }
    public void setBillDays(int billDays) { this.billDays = billDays; }
}
