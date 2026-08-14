package com.example.my_project1.ui.viewmodel.billvm;

import com.example.my_project1.ui.adapter.bill.BillAdapter;

import java.util.Objects;

/**
 * HomeBillUiModel - 首页扁平化列表统一模型
 */
public class HomeBillUiModel {
    public static final int TYPE_DATE_HEADER = 0;
    public static final int TYPE_BILL_ITEM = 1;

    public final int type;
    
    // For TYPE_DATE_HEADER
    public final String dateKey;
    public final String dateText;
    public final String expenseText;
    public final String incomeText;
    
    // For TYPE_BILL_ITEM
    public final BillUiModel billItem;
    public final boolean isLastInDay;

    private HomeBillUiModel(int type, String dateKey, String dateText, String expenseText, String incomeText, 
                            BillUiModel billItem, boolean isLastInDay) {
        this.type = type;
        this.dateKey = dateKey;
        this.dateText = dateText;
        this.expenseText = expenseText;
        this.incomeText = incomeText;
        this.billItem = billItem;
        this.isLastInDay = isLastInDay;
    }

    public static HomeBillUiModel header(BillAdapter.DateHeader header) {
        if (header == null) {
            return new HomeBillUiModel(TYPE_DATE_HEADER, "", "未知日期", "", "", null, false);
        }
        return new HomeBillUiModel(TYPE_DATE_HEADER, header.dateKey, header.dateText, 
                                 header.expenseText, header.incomeText, null, false);
    }

    public static HomeBillUiModel item(BillUiModel item, boolean isLast) {
        return new HomeBillUiModel(TYPE_BILL_ITEM, null, null, null, null, item, isLast);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HomeBillUiModel that = (HomeBillUiModel) o;
        return type == that.type &&
                isLastInDay == that.isLastInDay &&
                Objects.equals(dateKey, that.dateKey) &&
                Objects.equals(dateText, that.dateText) &&
                Objects.equals(expenseText, that.expenseText) &&
                Objects.equals(incomeText, that.incomeText) &&
                Objects.equals(billItem, that.billItem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, dateKey, dateText, expenseText, incomeText, billItem, isLastInDay);
    }
}
