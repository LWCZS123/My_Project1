package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.bill.SearchRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * SearchViewModel - 搜索功能 ViewModel
 * -------------------------------------------------------
 * 处理账单搜索、筛选、分页与 UI 映射
 */
public class SearchViewModel extends AndroidViewModel {

    private static final String TAG = "SearchViewModel";
    private static final long DEBOUNCE_MS = 300;
    private static final int PAGE_SIZE = 100;

    private final SearchRepository repository;
    private final AccountDao accountDao;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable suggestionRunnable;

    private String currentUserId;
    private final SearchFilter currentFilter = new SearchFilter();

    private int currentPage = 0;
    private boolean isLastPage = false;
    private boolean isLoading = false;
    private final List<Bill> allLoadedBills = new ArrayList<>();
    private final AtomicInteger searchGeneration = new AtomicInteger(0);

    private final MutableLiveData<List<BillAdapter.BillGroup>> _uiGroups = new MutableLiveData<>();
    public final LiveData<List<BillAdapter.BillGroup>> uiGroups = _uiGroups;

    private final MutableLiveData<List<HomeBillUiModel>> _uiItems = new MutableLiveData<>();
    public final LiveData<List<HomeBillUiModel>> uiItems = _uiItems;

    private final MutableLiveData<SearchSummary> _searchSummary = new MutableLiveData<>();
    public final LiveData<SearchSummary> searchSummary = _searchSummary;

    private final MutableLiveData<List<String>> _suggestions = new MutableLiveData<>();
    public final LiveData<List<String>> suggestions = _suggestions;

    private final MutableLiveData<ApiResponse<String>> _searchState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> searchState = _searchState;

    private final MutableLiveData<Map<String, Account>> _accountMap = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, Account>> accountMap = _accountMap;

    public LiveData<List<SearchHistory>> searchHistory;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repository = new SearchRepository(application);
        accountDao = AppDatabase.getInstance(application).accountDao();

        loadAccounts();
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
            searchHistory = repository.getSearchHistory(currentUserId);
            fetchSuggestions("");
        }
    }

    public void setKeyword(String keyword) {
        currentFilter.setKeyword(keyword);
        fetchSuggestions(keyword);
    }

    public void updateFilter(SearchFilter filter) {
        if (filter == null) return;
        currentFilter.setBillType(filter.getBillType());
        currentFilter.setCategoryId(filter.getCategoryId());
        currentFilter.setAccountIds(filter.getAccountIds());
        currentFilter.setStartDate(filter.getStartDate());
        currentFilter.setEndDate(filter.getEndDate());
        currentFilter.setMinAmount(filter.getMinAmount());
        currentFilter.setMaxAmount(filter.getMaxAmount());
        currentFilter.setIncludeBudget(filter.getIncludeBudget());
        performSearch();
    }

    /**
     * 执行全新搜索
     */
    public void performSearch() {
        if (currentUserId == null) return;
        int generation = searchGeneration.incrementAndGet();

        String keyword = currentFilter.getKeyword();
        if (keyword != null && !keyword.trim().isEmpty()) {
            repository.addSearchHistory(currentUserId, keyword.trim());
        }

        currentPage = 0;
        isLastPage = false;
        isLoading = false;
        allLoadedBills.clear();
        _uiGroups.setValue(new ArrayList<>());
        _uiItems.setValue(new ArrayList<>());

        _searchState.setValue(ApiResponse.loading());

        handler.post(() -> {
            repository.getSearchSummary(currentUserId, currentFilter, response -> {
                if (response.isSuccess()) {
                    _searchSummary.postValue(response.data);
                }
            });

            loadNextPageInternal(generation);
        });
    }

    public void loadNextPage() {
        if (isLoading || isLastPage || currentUserId == null) return;
        loadNextPageInternal(searchGeneration.get());
    }

    private void loadNextPageInternal(int generation) {
        isLoading = true;
        repository.searchBillsPaged(currentUserId, currentFilter, PAGE_SIZE, currentPage * PAGE_SIZE, response -> {
            if (generation != searchGeneration.get()) return;
            isLoading = false;
            if (response.isSuccess()) {
                List<Bill> newBills = response.data;
                if (newBills == null || newBills.isEmpty()) {
                    isLastPage = true;
                    if (currentPage == 0) {
                        _searchState.setValue(ApiResponse.empty("未找到相关账单"));
                    }
                } else {
                    allLoadedBills.addAll(newBills);
                    currentPage++;
                    if (newBills.size() < PAGE_SIZE) isLastPage = true;

                    processBillsToUiItems(new ArrayList<>(allLoadedBills), generation);
                    _searchState.setValue(ApiResponse.success("已加载"));
                }
            } else {
                _searchState.setValue(ApiResponse.error(response.message));
            }
        });
    }

    private void processBillsToUiItems(List<Bill> bills, int generation) {
        AppExecutors.get().computation().execute(() -> {
            List<BillAdapter.BillGroup> groups = mapBillsToUiGroups(bills);
            List<HomeBillUiModel> items = new ArrayList<>(bills.size() + groups.size());
            for (BillAdapter.BillGroup group : groups) {
                items.add(HomeBillUiModel.header(group.header));
                for (int i = 0; i < group.bills.size(); i++) {
                    items.add(HomeBillUiModel.item(group.bills.get(i), i == group.bills.size() - 1));
                }
            }
            handler.post(() -> {
                if (generation == searchGeneration.get()) {
                    _uiItems.setValue(items);
                }
            });
        });
    }

    private void fetchSuggestions(String keyword) {
        if (suggestionRunnable != null) handler.removeCallbacks(suggestionRunnable);
        if (keyword == null || keyword.trim().isEmpty()) {
            _suggestions.setValue(new ArrayList<>());
            return;
        }

        suggestionRunnable = () -> {
            repository.getSuggestions(currentUserId, keyword, response -> {
                if (response.isSuccess()) {
                    _suggestions.setValue(response.data);
                }
            });
        };
        handler.postDelayed(suggestionRunnable, DEBOUNCE_MS);
    }

    public void deleteHistory(SearchHistory history) { repository.deleteSearchHistory(history); }
    public void clearHistory() { if (currentUserId != null) repository.clearAllHistory(currentUserId); }

    private List<BillAdapter.BillGroup> mapBillsToUiGroups(List<Bill> sorted) {
        if (sorted == null || sorted.isEmpty()) return new ArrayList<>();

        Map<String, Account> accountMap = _accountMap.getValue();
        SimpleDateFormat dateKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat amtFmt = new DecimalFormat("#,##0.00");
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        List<BillAdapter.BillGroup> groups = new ArrayList<>();
        String prevDateKey = null;
        double dayExp = 0, dayInc = 0;
        List<BillUiModel> currentDayBills = new ArrayList<>();

        for (Bill bill : sorted) {
            if (bill.getBillTime() == null) continue;
            String dateKey = dateKeyFmt.format(bill.getBillTime());

            if (prevDateKey != null && !dateKey.equals(prevDateKey)) {
                addGroup(groups, prevDateKey, dayExp, dayInc, currentDayBills, currentYear, weekDays);
                dayExp = 0; dayInc = 0;
                currentDayBills.clear();
            }

            if (bill.getType() == 0) dayExp += bill.getAmount();
            else if (bill.getType() == 1) dayInc += bill.getAmount();

            currentDayBills.add(buildUiModel(bill, accountMap, timeFmt, amtFmt));
            prevDateKey = dateKey;
        }
        if (!currentDayBills.isEmpty()) {
            addGroup(groups, prevDateKey, dayExp, dayInc, currentDayBills, currentYear, weekDays);
        }
        return groups;
    }

    private void addGroup(List<BillAdapter.BillGroup> groups, String dateKey, double exp, double inc,
                          List<BillUiModel> bills, int currentYear, String[] weekDays) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey);
            if (date == null) return;

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            int billYear = cal.get(Calendar.YEAR);

            String pattern = (billYear == currentYear) ? "M月d日" : "yyyy年M月d日";
            String dateDisp = new SimpleDateFormat(pattern, Locale.getDefault()).format(date);

            String disp = dateDisp + "（" + weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1] + "）";
            groups.add(new BillAdapter.BillGroup(
                    new BillAdapter.DateHeader(dateKey, disp, String.format("支 %.2f", exp), String.format("收 %.2f", inc)),
                    new ArrayList<>(bills)
            ));
        } catch (Exception ignored) {}
    }

    private BillUiModel buildUiModel(Bill bill, Map<String, Account> accountMap, SimpleDateFormat timeFmt, DecimalFormat amtFmt) {
        int type = bill.getType();
        int color = getApplication().getColor(type == 0 ? R.color.red : (type == 1 ? R.color.green : R.color.orange_500));
        String amountText = (type == 0 ? "- ¥" : (type == 1 ? "+ ¥" : "¥")) + amtFmt.format(bill.getAmount());

        double discount = bill.getDiscount();
        boolean hasDiscount = discount > 0;
        String originalAmountText = "";
        String discountText = "";
        if (hasDiscount) {
            double originalAmount = bill.getAmount() + discount;
            originalAmountText = "¥" + amtFmt.format(originalAmount);
            discountText = String.format(java.util.Locale.getDefault(), "优惠¥%s", amtFmt.format(discount));
        }

        Account acc = accountMap != null ? accountMap.get(bill.getAccountId()) : null;
        Account toAcc = (accountMap != null && type == 2) ? accountMap.get(bill.getToAccountId()) : null;

        return BillUiModel.builder()
                .localId(bill.getId()).objectId(bill.getObjectId())
                .timeText(timeFmt.format(bill.getBillTime()))
                .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                .categoryIconUrl(bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "")
                .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                .amountText(amountText).amountColor(color)
                .accountName(acc != null ? acc.getName() : "").toAccountName(toAcc != null ? toAcc.getName() : "")
                .billType(type).remarkText(bill.getRemark()).imageUrls(bill.getImageUrls())
                .originalAmountText(originalAmountText)
                .discountText(discountText)
                .hasDiscount(hasDiscount)
                .build();
    }

    private void loadAccounts() {
        AppExecutors.get().diskIO().execute(() -> {
            List<Account> accounts = accountDao.getAllAccountsSync();
            Map<String, Account> map = new HashMap<>();
            if (accounts != null) for (Account a : accounts) map.put(a.getObjectId(), a);
            _accountMap.postValue(map);
        });
    }

    public SearchFilter getCurrentFilter() { return currentFilter; }

    public boolean isLastPage() { return isLastPage; }
    public boolean isLoading() { return isLoading; }
}
