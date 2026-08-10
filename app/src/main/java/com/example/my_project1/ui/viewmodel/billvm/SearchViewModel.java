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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * SearchViewModel - 搜索功能ViewModel (完整重构版)
 */
public class SearchViewModel extends AndroidViewModel {

    private static final String TAG = "SearchViewModel";
    private static final long DEBOUNCE_MS = 300;

    private final SearchRepository repository;
    private final AccountDao accountDao;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable suggestionRunnable;

    private String currentUserId;
    private final SearchFilter currentFilter = new SearchFilter();

    // ==================== LiveData ====================

    private final MutableLiveData<List<Bill>> _searchResults = new MutableLiveData<>();
    public final LiveData<List<Bill>> searchResults = _searchResults;

    private final MutableLiveData<SearchSummary> _searchSummary = new MutableLiveData<>();
    public final LiveData<SearchSummary> searchSummary = _searchSummary;

    private final MutableLiveData<List<String>> _suggestions = new MutableLiveData<>();
    public final LiveData<List<String>> suggestions = _suggestions;

    private final MutableLiveData<ApiResponse<String>> _searchState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> searchState = _searchState;

    private final MutableLiveData<Map<String, Account>> _accountMap = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, Account>> accountMap = _accountMap;

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    public LiveData<List<SearchHistory>> searchHistory;

    // ==================== 构造函数 ====================

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repository = new SearchRepository(application);
        accountDao = AppDatabase.getInstance(application).accountDao();

        loadAccounts();
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
            searchHistory = repository.getSearchHistory(currentUserId);
            fetchSuggestions(""); // Fetch default suggestions
        }
    }

    // ==================== 搜索逻辑 ====================

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
        performSearch();
    }

    public void performSearch() {
        if (currentUserId == null) return;

        String keyword = currentFilter.getKeyword();
        if (keyword != null && !keyword.trim().isEmpty()) {
            repository.addSearchHistory(currentUserId, keyword.trim());
        }

        _searchState.setValue(ApiResponse.loading());
        repository.searchBills(currentUserId, currentFilter, response -> {
            if (response.isSuccess()) {
                List<Bill> bills = response.data;
                _searchResults.setValue(bills);
                calculateSummary(bills);
                if (bills == null || bills.isEmpty()) {
                    _searchState.setValue(ApiResponse.empty("未找到相关账单"));
                } else {
                    _searchState.setValue(ApiResponse.success("找到 " + bills.size() + " 条记录"));
                }
            } else {
                _searchState.setValue(ApiResponse.error(response.message));
            }
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

    private void calculateSummary(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) {
            _searchSummary.setValue(new SearchSummary(0, 0, 0, 0, 0));
            return;
        }

        double income = 0, expense = 0;
        Set<String> days = new HashSet<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Bill b : bills) {
            if (b.getType() == 0) expense += b.getAmount();
            else if (b.getType() == 1) income += b.getAmount();
            if (b.getBillTime() != null) days.add(fmt.format(b.getBillTime()));
        }

        _searchSummary.setValue(new SearchSummary(income, expense, income - expense, bills.size(), days.size()));
    }

    // ==================== 历史管理 ====================

    public void deleteHistory(SearchHistory history) { repository.deleteSearchHistory(history); }
    public void clearHistory() { if (currentUserId != null) repository.clearAllHistory(currentUserId); }

    // ==================== UI 映射 ====================

    public List<BillAdapter.BillGroup> mapBillsToUiGroups(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) return new ArrayList<>();

        List<Bill> sorted = new ArrayList<>(bills);
        Collections.sort(sorted, (b1, b2) -> {
            Date t1 = b1.getBillTime(), t2 = b2.getBillTime();
            if (t1 == null || t2 == null) return 0;
            int res = t2.compareTo(t1);
            return res != 0 ? res : Long.compare(b2.getId(), b1.getId());
        });

        Map<String, Account> accountMap = _accountMap.getValue();
        SimpleDateFormat dateKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat dateDispFmt = new SimpleDateFormat("M月d日", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        DecimalFormat amtFmt = new DecimalFormat("#,##0.00");
        String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

        List<BillAdapter.BillGroup> groups = new ArrayList<>();
        String prevDateKey = null;
        double dayExp = 0, dayInc = 0;
        List<BillUiModel> currentDayBills = new ArrayList<>();

        for (Bill bill : sorted) {
            if (bill.getBillTime() == null) continue;
            String dateKey = dateKeyFmt.format(bill.getBillTime());

            if (prevDateKey != null && !dateKey.equals(prevDateKey)) {
                addGroup(groups, prevDateKey, dayExp, dayInc, currentDayBills, dateDispFmt, weekDays);
                dayExp = 0; dayInc = 0;
                currentDayBills.clear();
            }

            if (bill.getType() == 0) dayExp += bill.getAmount();
            else if (bill.getType() == 1) dayInc += bill.getAmount();

            currentDayBills.add(buildUiModel(bill, accountMap, timeFmt, amtFmt));
            prevDateKey = dateKey;
        }
        if (!currentDayBills.isEmpty()) {
            addGroup(groups, prevDateKey, dayExp, dayInc, currentDayBills, dateDispFmt, weekDays);
        }
        return groups;
    }

    private void addGroup(List<BillAdapter.BillGroup> groups, String dateKey, double exp, double inc,
                          List<BillUiModel> bills, SimpleDateFormat dispFmt, String[] weekDays) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey);
            Calendar cal = Calendar.getInstance(); cal.setTime(date);
            String disp = dispFmt.format(date) + "（" + weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1] + "）";
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
}
