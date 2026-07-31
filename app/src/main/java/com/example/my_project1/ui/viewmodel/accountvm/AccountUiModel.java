package com.example.my_project1.ui.viewmodel.accountvm;

import com.example.my_project1.data.model.account.Account;
import java.util.Objects;

public class AccountUiModel {
    public final long id;
    public final String objectId;
    public final String name;
    public final String subtitle;
    public final String amountText;
    public final int amountColor;
    public final String availableText;
    public final boolean isAvailableVisible;
    public final String iconUrl;
    public final boolean isAmountHidden;
    public final boolean isSwipeEnabled;
    public final Account originalAccount;

    public AccountUiModel(long id, String objectId, String name, String subtitle, String amountText, 
                          int amountColor, String availableText, boolean isAvailableVisible, 
                          String iconUrl, boolean isAmountHidden, boolean isSwipeEnabled, Account originalAccount) {
        this.id = id;
        this.objectId = objectId;
        this.name = name;
        this.subtitle = subtitle;
        this.amountText = amountText;
        this.amountColor = amountColor;
        this.availableText = availableText;
        this.isAvailableVisible = isAvailableVisible;
        this.iconUrl = iconUrl;
        this.isAmountHidden = isAmountHidden;
        this.isSwipeEnabled = isSwipeEnabled;
        this.originalAccount = originalAccount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountUiModel that = (AccountUiModel) o;
        return id == that.id &&
                amountColor == that.amountColor &&
                isAvailableVisible == that.isAvailableVisible &&
                isAmountHidden == that.isAmountHidden &&
                isSwipeEnabled == that.isSwipeEnabled &&
                Objects.equals(objectId, that.objectId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(subtitle, that.subtitle) &&
                Objects.equals(amountText, that.amountText) &&
                Objects.equals(availableText, that.availableText) &&
                Objects.equals(iconUrl, that.iconUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, objectId, name, subtitle, amountText, amountColor, availableText, isAvailableVisible, iconUrl, isAmountHidden, isSwipeEnabled);
    }
}
