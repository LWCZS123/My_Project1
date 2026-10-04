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
import com.example.my_project1.utils.AppExecutors;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kotlin.coroutines.intrinsics.IntrinsicsKt;

/**
 * 首页账单分页数据源
 * -------------------------------------------------------
 * 职责：从 Room 数据库分页加载当月账单，按天组合生成 Header 标题与账单模型，确保跨页不会打断日期头。
 */
final class HomeBillsPagingSource extends PagingSource<Integer, HomeBillUiModel> {

    interface FirstPageListener {
        void onFirstPageLoaded(HomeBillsPagingSource source, List<HomeBillUiModel> items);
    }

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
    private final FirstPageListener firstPageListener;
    private final Map<Integer, Integer> previousOffsetByOffset = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> nextOffsetByOffset = new ConcurrentHashMap<>();
    private volatile Map<String, Account> accountMap;

    HomeBillsPagingSource(Application application, BillRepository repository, AccountDao accountDao,
                          String userId, Date monthStart, Date monthEnd,
                          FirstPageListener firstPageListener) {
        this.application = application;
        this.repository = repository;
        this.accountDao = accountDao;
        this.userId = userId;
        this.monthStart = monthStart;
        this.monthEnd = monthEnd;
        this.firstPageListener = firstPageListener;
    }

    @NonNull
    @Override
    public Object load(@NonNull PagingSource.LoadParams<Integer> params,
                       @NonNull kotlin.coroutines.Continuation<? super PagingSource.LoadResult<Integer, HomeBillUiModel>> continuation) {
        // Cloud sync can occupy the shared disk pool for a long time. Keep the read path
        // independent so returning to Home is not queued behind upload/download writes.
        AppExecutors.get().computation().execute(() -> continuation.resumeWith(loadPage(params)));
        return IntrinsicsKt.getCOROUTINE_SUSPENDED();
    }

    private synchronized PagingSource.LoadResult<Integer, HomeBillUiModel> loadPage(
            PagingSource.LoadParams<Integer> params) {
        try {
            int offset = params.getKey() != null ? params.getKey() : 0;
            int requested = Math.max(1, params.getLoadSize());

            if (userId == null || userId.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            final Date endExclusive = new Date(monthEnd.getTime() + 1L);
            List<Bill> fetched = repository.getBillsInTimeRangePaged(
                    userId, monthStart, endExclusive, requested + 1, offset);

            if (fetched == null || fetched.isEmpty()) {
                notifyFirstPage(offset, new ArrayList<>());
                return new PagingSource.LoadResult.Page<>(
                        new ArrayList<>(), getPreviousKey(offset), null);
            }


            int consumed = Math.min(requested, fetched.size());
            boolean hasMore = fetched.size() > requested;

            if (hasMore && isSameDay(fetched.get(requested - 1), fetched.get(requested))) {
                Bill boundaryBill = fetched.get(requested - 1);
                int queryLimit = requested + 1;
                int dayEnd = findFirstDifferentDay(fetched, requested, boundaryBill);

                while (dayEnd < 0 && fetched.size() == queryLimit) {
                    int nextLimit = Math.max(queryLimit + requested, queryLimit * 2);
                    List<Bill> expanded = repository.getBillsInTimeRangePaged(
                            userId, monthStart, endExclusive, nextLimit, offset);
                    if (expanded == null || expanded.size() <= fetched.size()) break;
                    fetched = expanded;
                    queryLimit = nextLimit;
                    dayEnd = findFirstDifferentDay(fetched, requested, boundaryBill);
                }

                if (dayEnd >= 0) {
                    consumed = dayEnd;
                    hasMore = true;
                } else {
                    consumed = fetched.size();
                    hasMore = false;
                }
            }
            List<Bill> pageBills = new ArrayList<>(fetched.subList(0, consumed));

            Map<String, Account> map = getAccountMap();

            Integer nextKey = hasMore ? offset + consumed : null;
            Integer previousKey = getPreviousKey(offset);
            if (nextKey != null) {
                previousOffsetByOffset.put(nextKey, offset);
                nextOffsetByOffset.put(offset, nextKey);
            }

            List<HomeBillUiModel> items = mapToFlatItems(pageBills, map);
            notifyFirstPage(offset, items);
            return new PagingSource.LoadResult.Page<>(
                    items,
                    previousKey,
                    nextKey
            );
        } catch (Throwable throwable) {
            android.util.Log.e("HomeBillsPagingSource", "加载数据发生错误: " + throwable.getMessage(), throwable);
            return new PagingSource.LoadResult.Error<>(throwable);
        }
    }

    private void notifyFirstPage(int offset, List<HomeBillUiModel> items) {
        if (offset == 0 && firstPageListener != null) {
            firstPageListener.onFirstPageLoaded(this, new ArrayList<>(items));
        }
    }

    private int findFirstDifferentDay(List<Bill> bills, int startIndex, Bill boundaryBill) {
        for (int index = startIndex; index < bills.size(); index++) {
            if (!isSameDay(boundaryBill, bills.get(index))) return index;
        }
        return -1;
    }

    private Integer getPreviousKey(int offset) {
        if (offset == 0) return null;
        Integer previousKey = previousOffsetByOffset.get(offset);
        return previousKey != null ? previousKey : 0;
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull androidx.paging.PagingState<Integer, HomeBillUiModel> state) {
        Integer anchorPosition = state.getAnchorPosition();
        if (anchorPosition == null) return 0;
        PagingSource.LoadResult.Page<Integer, HomeBillUiModel> page =
                state.closestPageToPosition(anchorPosition);
        if (page == null) return 0;

        Integer nextKey = page.getNextKey();
        if (nextKey != null) {
            Integer currentKey = previousOffsetByOffset.get(nextKey);
            if (currentKey != null) return currentKey;
        }
        Integer previousKey = page.getPrevKey();
        if (previousKey != null) {
            Integer currentKey = nextOffsetByOffset.get(previousKey);
            if (currentKey != null) return currentKey;
        }
        return 0;
    }

    private Map<String, Account> getAccountMap() {
        Map<String, Account> cached = accountMap;
        if (cached != null) return cached;
        synchronized (this) {
            if (accountMap == null) {
                Map<String, Account> loaded = new HashMap<>();
                List<Account> accounts = accountDao.getAllAccountsSyncExcludeDeleted();
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
