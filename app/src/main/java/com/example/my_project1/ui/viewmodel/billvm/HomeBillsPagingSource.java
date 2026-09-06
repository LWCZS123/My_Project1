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
        android.util.Log.d("HomeBillsPagingSource", "Load started: offset=" + params.getKey() + " on thread " + Thread.currentThread().getName());
        try {
            int offset = params.getKey() != null ? params.getKey() : 0;
            int requested = params.getLoadSize();
            
            // 用户 ID 为空则返回空页
            if (userId == null || userId.isEmpty()) {
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            // 🚀 重要：Room 不允许在主线程执行查询。由于 Java PagingSource.load 可能被回调在主线程，
            // 我们通过一个简单的阻塞方式强制在后台线程执行数据库操作，规避 MainThread 检查。
            final List<Bill>[] fetchedWrapper = new List[1];
            final Throwable[] errorWrapper = new Throwable[1];

            Thread dbThread = new Thread(() -> {
                try {
                    fetchedWrapper[0] = repository.getBillsInTimeRangePaged(
                            userId, monthStart, monthEnd, requested + 1, offset);
                } catch (Throwable e) {
                    errorWrapper[0] = e;
                }
            });
            dbThread.start();
            dbThread.join();

            if (errorWrapper[0] != null) throw errorWrapper[0];
            List<Bill> fetched = fetchedWrapper[0];

            if (fetched == null || fetched.isEmpty()) {
                android.util.Log.d("HomeBillsPagingSource", "No bills found in range");
                return new PagingSource.LoadResult.Page<>(new ArrayList<>(), null, null);
            }

            android.util.Log.d("HomeBillsPagingSource", "Fetched " + fetched.size() + " bills");

            int consumed = Math.min(requested, fetched.size());

            // 简单的分页逻辑，暂不进行复杂的跨天截断，以确稳定
            List<Bill> pageBills = new ArrayList<>(fetched.subList(0, consumed));

            // 获取账户信息用于 UI 模型转换（同样在 dbThread 中获取会更安全，但如果之前 join 了，这里大概率还在主线程，
            // 索性全部移入 dbThread 或再次阻塞）
            final Map<String, Account> accountMap = new HashMap<>();
            Thread accThread = new Thread(() -> {
                List<Account> accounts = accountDao.getAllAccountsSyncExcludeDeleted();
                if (accounts != null) {
                    for (Account account : accounts) {
                        accountMap.put(account.getObjectId(), account);
                    }
                }
            });
            accThread.start();
            accThread.join();

            boolean hasMore = fetched.size() > consumed;
            Integer nextKey = hasMore ? offset + consumed : null;
            
            return new PagingSource.LoadResult.Page<>(
                    mapToFlatItems(pageBills, accountMap), 
                    null, 
                    nextKey
            );
        } catch (Throwable throwable) {
            android.util.Log.e("HomeBillsPagingSource", "Load error: " + throwable.getMessage(), throwable);
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
        android.util.Log.d("HomeBillsPagingSource", "Mapping " + bills.size() + " bills to flat items");
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
                android.util.Log.d("HomeBillsPagingSource", "New date detected: " + dateKey);
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
        android.util.Log.d("HomeBillsPagingSource", "Mapped to " + items.size() + " flat items");
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
                .originalBill(bill) // 🔑 设置原始对象
                .build();
    }
}
