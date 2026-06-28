package com.example.my_project1.ui.viewmodel.billvm;

/**
 * HeaderUiModel - 首页顶部概览卡片数据模型
 * -------------------------------------------------------
 * 由 BillViewModel 在后台线程预计算，直接传给 HeaderAdapter 显示
 */
public class HeaderUiModel {

    /** e.g. "4月 - 支出" */
    public final String assetTypeText;
    /** e.g. "¥608.0" */
    public final String totalExpenseText;
    /** e.g. "¥500.00" */
    public final String totalIncomeText;
    /** e.g. "¥-108.00" 结余 */
    public final String balanceText;

    public HeaderUiModel(String assetTypeText,
                         String totalExpenseText,
                         String totalIncomeText,
                         String balanceText) {
        this.assetTypeText   = assetTypeText;
        this.totalExpenseText = totalExpenseText;
        this.totalIncomeText  = totalIncomeText;
        this.balanceText      = balanceText;
    }
}