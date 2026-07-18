package com.example.my_project1.ui.viewmodel.billvm;

/**
 * HeaderUiModel - 首页顶部概览卡片数据模型
 * -------------------------------------------------------
 * 由 BillViewModel 在后台线程预计算，直接传给 HeaderAdapter 显示
 */
public class HeaderUiModel {

    public final String mainBalance;      // 大字余额
    public final String todayChange;      // 今日变化
    public final String assets;           // 资产
    public final String liabilities;      // 负债
    public final String monthlyIncome;    // 月收入
    public final String totalIncome;      // 总收入
    public final String monthlyExpense;   // 月支出
    public final String totalExpense;     // 总支出
    public final String weeklyBalance;    // 周结余

    public HeaderUiModel(String mainBalance,
                         String todayChange,
                         String assets,
                         String liabilities,
                         String monthlyIncome,
                         String totalIncome,
                         String monthlyExpense,
                         String totalExpense,
                         String weeklyBalance) {
        this.mainBalance    = mainBalance;
        this.todayChange    = todayChange;
        this.assets         = assets;
        this.liabilities    = liabilities;
        this.monthlyIncome  = monthlyIncome;
        this.totalIncome    = totalIncome;
        this.monthlyExpense = monthlyExpense;
        this.totalExpense   = totalExpense;
        this.weeklyBalance  = weeklyBalance;
    }
}
