package com.example.my_project1.ui.viewmodel.accountvm;

import com.example.my_project1.data.model.bill.Bill;

import java.util.Objects;

public class AccountBillUiModel {

    public static final int TYPE_MONTH_HEADER = 0;
    public static final int TYPE_DAY_HEADER = 1;
    public static final int TYPE_BILL_ITEM = 2;

    public int type;
    
    // Header fields
    public String title;
    public String subtitle;
    public String billAmountText;
    public String inflowText;
    public String outflowText;
    public boolean isCollapsed;
    public String key;

    // Bill fields
    public long id;
    public String objectId;
    public String categoryName;
    public String categoryIconUrl;
    public String timeNote;
    public String amountText;
    public int amountColor;
    public String balanceText;
    public Bill originalBill;
    public boolean isLastInSection;

    // Header Constructor
    public AccountBillUiModel(int type, String title, String subtitle, String billAmountText, 
                             String inflowText, String outflowText, boolean isCollapsed, String key) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.billAmountText = billAmountText;
        this.inflowText = inflowText;
        this.outflowText = outflowText;
        this.isCollapsed = isCollapsed;
        this.key = key;
    }

    // Bill Item Constructor
    public AccountBillUiModel(long id, String objectId, String categoryName, String categoryIconUrl, 
                             String timeNote, String amountText, int amountColor, String balanceText, 
                             Bill originalBill) {
        this.type = TYPE_BILL_ITEM;
        this.id = id;
        this.objectId = objectId;
        this.categoryName = categoryName;
        this.categoryIconUrl = categoryIconUrl;
        this.timeNote = timeNote;
        this.amountText = amountText;
        this.amountColor = amountColor;
        this.balanceText = balanceText;
        this.originalBill = originalBill;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountBillUiModel that = (AccountBillUiModel) o;
        return type == that.type &&
                isCollapsed == that.isCollapsed &&
                id == that.id &&
                amountColor == that.amountColor &&
                isLastInSection == that.isLastInSection &&
                Objects.equals(title, that.title) &&
                Objects.equals(subtitle, that.subtitle) &&
                Objects.equals(billAmountText, that.billAmountText) &&
                Objects.equals(inflowText, that.inflowText) &&
                Objects.equals(outflowText, that.outflowText) &&
                Objects.equals(key, that.key) &&
                Objects.equals(objectId, that.objectId) &&
                Objects.equals(categoryName, that.categoryName) &&
                Objects.equals(categoryIconUrl, that.categoryIconUrl) &&
                Objects.equals(timeNote, that.timeNote) &&
                Objects.equals(amountText, that.amountText) &&
                Objects.equals(balanceText, that.balanceText) &&
                Objects.equals(originalBill, that.originalBill);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, subtitle, billAmountText, inflowText, outflowText, isCollapsed, 
                            key, id, objectId, categoryName, categoryIconUrl, timeNote, amountText, 
                            amountColor, balanceText, originalBill, isLastInSection);
    }
}
