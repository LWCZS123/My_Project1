package com.example.my_project1.data.model.bill;

import java.io.Serializable;

/**
 * 账单月度统计数据模型
 * -------------------------------------------------------
 * 用于 SQLite 数据库按月聚合查询输出的结果对象。
 */
public class MonthlyStat implements Serializable {

    public String month;
    public double incomeTotal;
    public double expenseTotal;
    public double transferInTotal;
    public double transferOutTotal;

    public MonthlyStat() {}

    public MonthlyStat(String month, double incomeTotal, double expenseTotal, double transferInTotal, double transferOutTotal) {
        this.month = month;
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
        this.transferInTotal = transferInTotal;
        this.transferOutTotal = transferOutTotal;
    }
}
