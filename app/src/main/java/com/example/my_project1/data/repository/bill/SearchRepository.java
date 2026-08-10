package com.example.my_project1.data.repository.bill;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SearchHistoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SearchRepository - 搜索功能数据仓库 (重构版)
 */
public class SearchRepository {

    private static final String TAG = "SearchRepository";

    private final BillDao billDao;
    private final SearchHistoryDao searchHistoryDao;
    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final AppExecutors executors;

    public SearchRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.billDao = db.billDao();
        this.searchHistoryDao = db.searchHistoryDao();
        this.categoryDao = db.categoryDao();
        this.accountDao = db.accountDao();
        this.executors = AppExecutors.get();
    }

    public interface OperationCallback<T> {
        void onComplete(ApiResponse<T> result);
    }

    /**
     * 🔍 搜索账单 (高级筛选)
     */
    public void searchBills(String userId, SearchFilter filter, OperationCallback<List<Bill>> callback) {
        executors.diskIO().execute(() -> {
            try {
                String keyword = filter.getKeyword();
                String searchPattern = (keyword == null || keyword.trim().isEmpty()) ? null : "%" + keyword.trim() + "%";
                
                List<String> accountIds = filter.getAccountIds();
                int accountIdsCount = accountIds != null ? accountIds.size() : 0;

                List<Bill> results = billDao.searchBillsAdvanced(
                        userId,
                        searchPattern,
                        filter.getBillType(),
                        filter.getCategoryId(),
                        accountIds,
                        accountIdsCount,
                        filter.getStartDate(),
                        filter.getEndDate(),
                        filter.getMinAmount(),
                        filter.getMaxAmount()
                );
                
                // 内存过滤计入收支和预算逻辑
                List<Bill> finalResults = new ArrayList<>();
                for (Bill b : results) {
                    boolean matchBudget = filter.getIncludeBudget() == null || (b.isExcludeBudget() != filter.getIncludeBudget());
                    // 注意：Bill 实体中并没有 includeIncomeExpense 对应的字段，这里假设是指 type
                    // 如果需要更复杂的逻辑请告知。目前先处理 budget 过滤。
                    if (matchBudget) {
                        finalResults.add(b);
                    }
                }

                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.success(finalResults)));
            } catch (Exception e) {
                Log.e(TAG, "❌ 搜索账单异常", e);
                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 获取搜索建议
     */
    public void getSuggestions(String userId, String keyword, OperationCallback<List<String>> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<String> suggestions = new ArrayList<>();
                
                if (keyword == null || keyword.trim().isEmpty()) {
                    // Default suggestions when no keyword: common category names
                    List<Category> categories = categoryDao.getSystemPresets("expense");
                    for (int i = 0; i < Math.min(categories.size(), 8); i++) {
                        suggestions.add(categories.get(i).getName());
                    }
                } else {
                    String pattern = "%" + keyword.trim() + "%";

                    // 1. 备注建议
                    suggestions.addAll(billDao.getRemarkSuggestions(userId, pattern));
                    
                    // 2. 分类建议
                    List<Category> categories = categoryDao.searchCategories(userId, pattern);
                    for (Category c : categories) suggestions.add(c.getName());

                    // 3. 账户建议
                    List<Account> accounts = accountDao.searchAccounts(userId, pattern);
                    for (Account a : accounts) suggestions.add(a.getName());
                    
                    // 4. 地点建议
                    suggestions.addAll(billDao.getLocationSuggestions(userId, pattern));
                }

                // 去重
                List<String> uniqueSuggestions = new ArrayList<>();
                for (String s : suggestions) {
                    if (s != null && !s.isEmpty() && !uniqueSuggestions.contains(s)) {
                        uniqueSuggestions.add(s);
                    }
                }

                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.success(uniqueSuggestions)));
            } catch (Exception e) {
                Log.e(TAG, "❌ 获取建议异常", e);
                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    // ==================== 搜索历史管理 ====================

    public LiveData<List<SearchHistory>> getSearchHistory(String userId) {
        return searchHistoryDao.getSearchHistory(userId);
    }

    public void addSearchHistory(String userId, String keyword) {
        executors.diskIO().execute(() -> {
            try {
                if (keyword == null || keyword.trim().isEmpty()) return;
                String trimmed = keyword.trim();
                SearchHistory history = new SearchHistory(userId, trimmed);
                searchHistoryDao.insert(history);
            } catch (Exception e) {
                Log.e(TAG, "❌ 添加搜索历史异常", e);
            }
        });
    }

    public void deleteSearchHistory(SearchHistory history) {
        executors.diskIO().execute(() -> searchHistoryDao.delete(history));
    }

    public void clearAllHistory(String userId) {
        executors.diskIO().execute(() -> searchHistoryDao.clearHistory(userId));
    }
}
