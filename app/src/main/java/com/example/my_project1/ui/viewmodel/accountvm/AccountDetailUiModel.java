package com.example.my_project1.ui.viewmodel.accountvm;

import com.example.my_project1.data.model.account.Account;
import com.github.mikephil.charting.data.PieEntry;
import java.util.List;
import java.util.Objects;

public class AccountDetailUiModel {
    public static final int TYPE_HEADER = -1;
    public static final int TYPE_MONTH_HEADER = 0;
    public static final int TYPE_DAY_HEADER = 1;
    public static final int TYPE_BILL_ITEM = 2;

    public final int type;
    
    // For Header
    public final Account account;
    public final double income, expense, transferIn, transferOut;
    public final List<PieEntry> pieEntries;
    public final int[] chartColors;
    public final boolean showingExpense;
    
    // For List Items
    public final AccountBillUiModel billUiModel;
    public final boolean isFirstInSection;
    public final boolean isLastInSection;

    // Header Constructor
    public AccountDetailUiModel(Account account, double income, double expense, double transferIn, double transferOut, 
                               List<PieEntry> pieEntries, int[] chartColors, boolean showingExpense) {
        this.type = TYPE_HEADER;
        this.account = account;
        this.income = income;
        this.expense = expense;
        this.transferIn = transferIn;
        this.transferOut = transferOut;
        this.pieEntries = pieEntries;
        this.chartColors = chartColors;
        this.showingExpense = showingExpense;
        this.billUiModel = null;
        this.isFirstInSection = false;
        this.isLastInSection = false;
    }

    // Bill Item Constructor
    public AccountDetailUiModel(AccountBillUiModel billUiModel, boolean isFirst, boolean isLast) {
        this.type = billUiModel.type;
        this.billUiModel = billUiModel;
        this.isFirstInSection = isFirst;
        this.isLastInSection = isLast;
        this.account = null;
        this.income = 0;
        this.expense = 0;
        this.transferIn = 0;
        this.transferOut = 0;
        this.pieEntries = null;
        this.chartColors = null;
        this.showingExpense = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountDetailUiModel that = (AccountDetailUiModel) o;
        return type == that.type &&
                Double.compare(that.income, income) == 0 &&
                Double.compare(that.expense, expense) == 0 &&
                Double.compare(that.transferIn, transferIn) == 0 &&
                Double.compare(that.transferOut, transferOut) == 0 &&
                showingExpense == that.showingExpense &&
                isFirstInSection == that.isFirstInSection &&
                isLastInSection == that.isLastInSection &&
                Objects.equals(account, that.account) &&
                isPieEntriesSame(pieEntries, that.pieEntries) &&
                Objects.equals(billUiModel, that.billUiModel);
    }

    private boolean isPieEntriesSame(List<PieEntry> l1, List<PieEntry> l2) {
        if (l1 == l2) return true;
        if (l1 == null || l2 == null) return false;
        if (l1.size() != l2.size()) return false;
        for (int j = 0; j < l1.size(); j++) {
            PieEntry e1 = l1.get(j);
            PieEntry e2 = l2.get(j);
            if (e1.getValue() != e2.getValue() || !Objects.equals(e1.getLabel(), e2.getLabel())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, account, income, expense, transferIn, transferOut, pieEntries, showingExpense, billUiModel, isFirstInSection, isLastInSection);
    }
}
