package com.example.my_project1.data.model.bill;

import java.io.Serializable;

/**
 * 账单日度统计数据模型
 * -------------------------------------------------------
 * 用于 SQLite 数据库按天聚合查询输出的结果对象。
 */
public class DailyStat implements Serializable {

    public String day;
    public int billCount;
    public double incomeTotal;
    public double expenseTotal;

    public DailyStat() {}

    public DailyStat(String day, int billCount, double incomeTotal, double expenseTotal) {
        this.day = day;
        this.billCount = billCount;
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
    }
}
