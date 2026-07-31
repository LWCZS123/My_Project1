package com.example.my_project1.ui.viewmodel.accountvm;

import com.example.my_project1.data.model.account.AccountGroup;
import java.util.List;
import java.util.Objects;

public class AccountGroupUiModel {
    public final long id;
    public final String objectId;
    public final String name;
    public final String countText;
    public final String positiveBalanceText;
    public final String negativeBalanceText;
    public final boolean isDebtVisible;
    public final boolean isExpanded;
    public final List<AccountUiModel> accounts;
    public final boolean isAmountHidden;
    public final AccountGroup originalGroup;

    public AccountGroupUiModel(long id, String objectId, String name, String countText, 
                               String positiveBalanceText, String negativeBalanceText, 
                               boolean isDebtVisible, boolean isExpanded, 
                               List<AccountUiModel> accounts, boolean isAmountHidden, AccountGroup originalGroup) {
        this.id = id;
        this.objectId = objectId;
        this.name = name;
        this.countText = countText;
        this.positiveBalanceText = positiveBalanceText;
        this.negativeBalanceText = negativeBalanceText;
        this.isDebtVisible = isDebtVisible;
        this.isExpanded = isExpanded;
        this.accounts = accounts;
        this.isAmountHidden = isAmountHidden;
        this.originalGroup = originalGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountGroupUiModel that = (AccountGroupUiModel) o;
        return id == that.id &&
                isDebtVisible == that.isDebtVisible &&
                isExpanded == that.isExpanded &&
                isAmountHidden == that.isAmountHidden &&
                Objects.equals(objectId, that.objectId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(countText, that.countText) &&
                Objects.equals(positiveBalanceText, that.positiveBalanceText) &&
                Objects.equals(negativeBalanceText, that.negativeBalanceText) &&
                Objects.equals(accounts, that.accounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, objectId, name, countText, positiveBalanceText, negativeBalanceText, isDebtVisible, isExpanded, accounts, isAmountHidden);
    }
}
