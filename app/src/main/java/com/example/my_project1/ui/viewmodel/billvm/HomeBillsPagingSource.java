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
 * 首页账单分页数据源
 * -------------------------------------------------------
 * 职责：从 Room 数据库分页加载当月账单，按天组合生成 Header 标题与账单模型，确保跨页不会打断日期头。
 */
final class HomeBillsPagingSource extends PagingSource<Integer, HomeBillUiModel> {

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
    private volatile Map<String, Account> accountMap;

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
        android.util.Log.d("HomeBillsPagingSource", "分页开始加载: offset=" + params.getKey());
        try {
            int offset = params.getKey() != null ? params.getKey() : 0;
            int requested = params.getLoadSize();

            if (userId == null || userId.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            final List<Bill>[] fetchedHolder = new List[1];
            final Throwable[] errorHolder = new Throwable[1];
            final int finalOffset = offset;
            final int finalRequested = requested;
            final Date endExclusive = new Date(monthEnd.getTime() + 1L);

            Thread thread = new Thread(() -> {
                try {
                    fetchedHolder[0] = repository.getBillsInTimeRangePaged(
                            userId, monthStart, endExclusive, finalRequested + 1, finalOffset);
                } catch (Throwable t) {
                    errorHolder[0] = t;
                }
            });
            thread.start();
            thread.join();

            if (errorHolder[0] != null) {
                throw errorHolder[0];
            }

            List<Bill> fetched = fetchedHolder[0];

            if (fetched == null || fetched.isEmpty()) {
                android.util.Log.d("HomeBillsPagingSource", "指定范围内未找到账单");
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            android.util.Log.d("HomeBillsPagingSource", "已查询到 " + fetched.size() + " 条账单");

            while (fetched != null && fetched.size() > requested
                    && isSameDay(fetched.get(requested - 1), fetched.get(fetched.size() - 1))) {
                int nextLimit = Math.max(fetched.size() + requested, fetched.size() * 2);
                final List<Bill>[] expandedHolder = new List[1];
                final int finalNextLimit = nextLimit;
                Thread expThread = new Thread(() -> {
                    try {
                        expandedHolder[0] = repository.getBillsInTimeRangePaged(
                                userId, monthStart, endExclusive, finalNextLimit, finalOffset);
                    } catch (Throwable ignored) {}
                });
                expThread.start();
                expThread.join();

                List<Bill> expanded = expandedHolder[0];
                if (expanded == null || expanded.size() <= fetched.size()) break;
                fetched = expanded;
            }

            if (fetched == null || fetched.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            int consumed = Math.min(requested, fetched.size());
            if (fetched.size() > requested && isSameDay(fetched.get(requested - 1), fetched.get(fetched.size() - 1))) {
                consumed = fetched.size();
            }
            List<Bill> pageBills = new ArrayList<>(fetched.subList(0, consumed));

            Map<String, Account> map = getAccountMap();

            boolean hasMore = fetched.size() > consumed;
            Integer nextKey = hasMore ? offset + consumed : null;

            return new PagingSource.LoadResult.Page<>(
                    mapToFlatItems(pageBills, map),
                    null,
                    nextKey
            );
        } catch (Throwable throwable) {
            android.util.Log.e("HomeBillsPagingSource", "加载数据发生错误: " + throwable.getMessage(), throwable);
            return new PagingSource.LoadResult.Error<>(throwable);
        }
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull androidx.paging.PagingState<Integer, HomeBillUiModel> state) {
        return null;
    }

    private Map<String, Account> getAccountMap() {
        Map<String, Account> cached = accountMap;
        if (cached != null) return cached;
        synchronized (this) {
            if (accountMap == null) {
                Map<String, Account> loaded = new HashMap<>();
                final List<Account>[] accountsHolder = new List[1];
                Thread thread = new Thread(() -> {
                    try {
                        accountsHolder[0] = accountDao.getAllAccountsSyncExcludeDeleted();
                    } catch (Throwable ignored) {}
                });
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException ignored) {}

                List<Account> accounts = accountsHolder[0];
                if (accounts != null) {
                    for (Account account : accounts) {
                        if (account.getObjectId() != null) loaded.put(account.getObjectId(), account);
                        loaded.put("local:" + account.getId(), account);
                    }
                }
                accountMap = loaded;
            }
            return accountMap;
        }
    }

    private List<HomeBillUiModel> mapToFlatItems(List<Bill> bills, Map<String, Account> accountMap) {
        List<HomeBillUiModel> items = new ArrayList<>();
        String activeDateKey = null;
        int headerIndex = -1;
        double expense = 0;
        double income = 0;

        for (int index = 0; index < bills.size(); index++) {
            Bill bill = bills.get(index);
            if (bill == null) continue;
            Date billTime = bill.getBillTime();
            if (billTime == null) continue;

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
        if (headerIndex < 0 || headerIndex >= items.size()) {
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

        Account account = findAccount(accountMap, bill.getAccountId(), bill.getLocalAccountId());
        Account toAccount = (billType == 2 || billType == 3)
                ? findAccount(accountMap, bill.getToAccountId(), bill.getToLocalAccountId()) : null;
        return BillUiModel.builder()
                .localId(bill.getId())
                .objectId(bill.getObjectId())
                .timeText(bill.getBillTime() != null ? timeFormat.format(bill.getBillTime()) : "")
                .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                .categoryIconUrl(categoryIcon)
                .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                .amountText(amountPrefix + amountFormat.format(bill.getAmount()))
                .amountColor(amountColor)
                .accountName(account != null && account.getName() != null ? account.getName() : "")
                .accountIconUrl(account != null && account.getIconUrl() != null ? account.getIconUrl() : "")
                .toAccountName(toAccount != null && toAccount.getName() != null ? toAccount.getName() : "")
                .billType(billType)
                .remarkText(bill.getRemark() != null ? bill.getRemark() : "")
                .imageUrls(bill.getImageUrls() != null ? bill.getImageUrls() : new ArrayList<>())
                .originalBill(bill)
                .build();
    }

    private Account findAccount(Map<String, Account> accounts, String cloudId, long localId) {
        if (accounts == null) return null;
        Account account = cloudId == null ? null : accounts.get(cloudId);
        return account != null || localId <= 0 ? account : accounts.get("local:" + localId);
    }
}
