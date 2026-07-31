package com.example.my_project1.ui.viewmodel.accountvm;

import com.example.my_project1.data.model.bill.Bill;
import java.util.Objects;

public class AccountBillUiModel {
    public static final int TYPE_MONTH_HEADER = 0;
    public static final int TYPE_DAY_HEADER = 1;
    public static final int TYPE_BILL_ITEM = 2;

    public final int type;
    
    // For Headers
    public final String title;
    public final String subtitle;
    public final String inflowText;
    public final String outflowText;
    public final boolean isCollapsed;
    public final String key; // monthKey or dayKey

    // For Bill Item
    public final long id;
    public final String objectId;
    public final String categoryName;
    public final String categoryIconUrl;
    public final String timeNote;
    public final String amountText;
    public final int amountColor;
    public final String balanceText;
    public final Bill originalBill;

    // Header Constructor
    public AccountBillUiModel(int type, String title, String subtitle, String inflowText, String outflowText, boolean isCollapsed, String key) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.inflowText = inflowText;
        this.outflowText = outflowText;
        this.isCollapsed = isCollapsed;
        this.key = key;
        
        this.id = -1;
        this.objectId = null;
        this.categoryName = null;
        this.categoryIconUrl = null;
        this.timeNote = null;
        this.amountText = null;
        this.amountColor = 0;
        this.balanceText = null;
        this.originalBill = null;
    }

    // Bill Item Constructor
    public AccountBillUiModel(long id, String objectId, String categoryName, String categoryIconUrl, String timeNote, String amountText, int amountColor, String balanceText, Bill originalBill) {
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
        
        this.title = null;
        this.subtitle = null;
        this.inflowText = null;
        this.outflowText = null;
        this.isCollapsed = false;
        this.key = null;
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
                Objects.equals(title, that.title) &&
                Objects.equals(subtitle, that.subtitle) &&
                Objects.equals(inflowText, that.inflowText) &&
                Objects.equals(outflowText, that.outflowText) &&
                Objects.equals(key, that.key) &&
                Objects.equals(objectId, that.objectId) &&
                Objects.equals(categoryName, that.categoryName) &&
                Objects.equals(categoryIconUrl, that.categoryIconUrl) &&
                Objects.equals(timeNote, that.timeNote) &&
                Objects.equals(amountText, that.amountText) &&
                Objects.equals(balanceText, that.balanceText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, title, subtitle, inflowText, outflowText, isCollapsed, key, id, objectId, categoryName, categoryIconUrl, timeNote, amountText, amountColor, balanceText);
    }
}
