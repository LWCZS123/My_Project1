package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.ui.adapter.bill.BillAdapter;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pages the current month's bills from Room. Each response ends at a day boundary so
 * a date header and its daily totals are never duplicated on the following page.
 */
final class HomeBillsPagingSource extends PagingSource<Integer, HomeBillUiModel> {

    private static final int MIN_BILL_PAGE_SIZE = 20;
    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat dateDisplayFormat = new SimpleDateFormat("M月d日", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final DecimalFormat amountFormat = new DecimalFormat("#,##0.00");
    private static final String[] WEEK_DAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private final Application application;
    private final BillRepository repository;
    private final AccountDao accountDao;
    private final String userId;
    private final Date monthStart;
    private final Date monthEnd;

    HomeBillsPagingSource(Application application, BillRepository repository, AccountDao accountDao,
                           String userId, Date monthStart, Date monthEnd) {
        this.application = application;
        this.repository = repository;
        this.accountDao = accountDao;
        this.userId = userId;
        this.monthStart = monthStart;
        this.monthEnd = monthEnd;
    }

    @NonNull
    @Override
    public Object load(@NonNull PagingSource.LoadParams<Integer> params,
                       @NonNull kotlin.coroutines.Continuation<? super PagingSource.LoadResult<Integer, HomeBillUiModel>> continuation) {
        try {
            int offset = params.getKey() != null ? params.getKey() : 0;
            int requested = Math.max(MIN_BILL_PAGE_SIZE, params.getLoadSize());
            
            // 🚀 核心修复：首页不显示通常是因为 userId 为空或查询范围不对
            if (userId == null || userId.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            List<Bill> fetched = repository.getBillsInTimeRangePaged(
                    userId, monthStart, monthEnd, requested + 1, offset);

            if (fetched == null || fetched.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            int consumed = Math.min(requested, fetched.size());
            if (fetched.size() > requested && isSameDay(fetched.get(consumed - 1), fetched.get(consumed))) {
                String trailingDay = dateKeyFormat.format(fetched.get(consumed - 1).getBillTime());
                while (consumed > 0 && trailingDay.equals(dateKeyFormat.format(fetched.get(consumed - 1).getBillTime()))) {
                    consumed--;
                }
            }

            List<Bill> pageBills;
            if (consumed == 0) {
                pageBills = getWholeDay(fetched.get(0).getBillTime());
                consumed = pageBills.size();
            } else {
                pageBills = new ArrayList<>(fetched.subList(0, consumed));
            }

            List<Account> accounts = accountDao.getAllAccountsSyncExcludeDeleted();
            Map<String, Account> accountMap = new HashMap<>();
            if (accounts != null) {
                for (Account account : accounts) {
                    accountMap.put(account.getObjectId(), account);
                }
            }

            boolean hasMore = fetched.size() > consumed;
            if (!hasMore && consumed > 0) {
                hasMore = !repository.getBillsInTimeRangePaged(
                        userId, monthStart, monthEnd, 1, offset + consumed).isEmpty();
            }
            Integer nextKey = hasMore ? offset + consumed : null;
            return new PagingSource.LoadResult.Page<>(mapToFlatItems(pageBills, accountMap), null, nextKey);
        } catch (Throwable throwable) {
            return new PagingSource.LoadResult.Error<>(throwable);
        }
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull androidx.paging.PagingState<Integer, HomeBillUiModel> state) {
        return null;
    }

    private List<Bill> getWholeDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        return repository.getBillsInTimeRangeSync(userId, start, calendar.getTime());
    }

    private List<HomeBillUiModel> mapToFlatItems(List<Bill> bills, Map<String, Account> accountMap) {
        List<HomeBillUiModel> items = new ArrayList<>();
        String activeDateKey = null;
        int headerIndex = -1;
        double expense = 0;
        double income = 0;

        for (int index = 0; index < bills.size(); index++) {
            Bill bill = bills.get(index);
            Date billTime = bill.getBillTime();
            if (billTime == null) {
                continue;
            }

            String dateKey = dateKeyFormat.format(billTime);
            if (!dateKey.equals(activeDateKey)) {
                finishHeader(items, headerIndex, expense, income);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(billTime);
                String dateText = dateDisplayFormat.format(billTime) + "（"
                        + WEEK_DAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1] + "）";
                headerIndex = items.size();
                items.add(HomeBillUiModel.header(new BillAdapter.DateHeader(dateKey, dateText, "", "")));
                activeDateKey = dateKey;
                expense = 0;
                income = 0;
            }

            if (bill.getType() == 0) {
                expense += bill.getAmount();
            } else if (bill.getType() == 1) {
                income += bill.getAmount();
            }

            boolean isLastInDay = index + 1 == bills.size() || !isSameDay(bill, bills.get(index + 1));
            items.add(HomeBillUiModel.item(buildBillUiModel(bill, accountMap), isLastInDay));
        }
        finishHeader(items, headerIndex, expense, income);
        return items;
    }

    private void finishHeader(List<HomeBillUiModel> items, int headerIndex, double expense, double income) {
        if (headerIndex < 0) {
            return;
        }
        HomeBillUiModel oldHeader = items.get(headerIndex);
        items.set(headerIndex, HomeBillUiModel.header(new BillAdapter.DateHeader(
                oldHeader.dateKey,
                oldHeader.dateText,
                String.format(Locale.getDefault(), "支 %.2f", expense),
                String.format(Locale.getDefault(), "收 %.2f", income))));
    }

    private boolean isSameDay(Bill first, Bill second) {
        return first != null && second != null && first.getBillTime() != null && second.getBillTime() != null
                && dateKeyFormat.format(first.getBillTime()).equals(dateKeyFormat.format(second.getBillTime()));
    }

    private BillUiModel buildBillUiModel(Bill bill, Map<String, Account> accountMap) {
        int billType = bill.getType();
        String amountPrefix;
        int amountColor;
        String categoryIcon = bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "";

        if (billType == 0) {
            amountPrefix = "- ¥";
            amountColor = application.getColor(R.color.red);
        } else if (billType == 1) {
            amountPrefix = "+ ¥";
            amountColor = application.getColor(R.color.green);
        } else {
            amountPrefix = "¥";
            amountColor = application.getColor(R.color.orange_500);
            categoryIcon = Uri.parse("android.resource://" + application.getPackageName()
                    + "/" + R.drawable.ic_transference).toString();
        }

        Account account = accountMap.get(bill.getAccountId());
        Account toAccount = (billType == 2 || billType == 3) ? accountMap.get(bill.getToAccountId()) : null;
        return BillUiModel.builder()
                .localId(bill.getId())
                .objectId(bill.getObjectId())
                .timeText(timeFormat.format(bill.getBillTime()))
                .categoryName(bill.getCategoryName())
                .categoryIconUrl(categoryIcon)
                .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                .amountText(amountPrefix + amountFormat.format(bill.getAmount()))
                .amountColor(amountColor)
                .accountName(account != null ? account.getName() : "")
                .accountIconUrl(account != null ? account.getIconUrl() : "")
                .toAccountName(toAccount != null ? toAccount.getName() : "")
                .billType(billType)
                .remarkText(bill.getRemark())
                .imageUrls(bill.getImageUrls())
                .build();
    }
}
