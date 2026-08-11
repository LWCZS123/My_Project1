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
import com.example.my_project1.data.model.bill.SearchSummary;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * SearchRepository - 搜索功能数据仓库 (优化版)
 * -------------------------------------------------------
 * ✅ 支持分页查询
 * ✅ 支持数据库聚合计算汇总
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
     * 🔍 搜索账单汇总 (高性能聚合查询)
     */
    public void getSearchSummary(String userId, SearchFilter filter, OperationCallback<SearchSummary> callback) {
        executors.diskIO().execute(() -> {
            try {
                String keyword = filter.getKeyword();
                String searchPattern = (keyword == null || keyword.trim().isEmpty()) ? null : "%" + keyword.trim() + "%";
                List<String> accountIds = filter.getAccountIds();
                int accountIdsCount = accountIds != null ? accountIds.size() : 0;

                SearchSummary summary = billDao.searchBillsSummaryAdvanced(
                        userId, searchPattern, filter.getBillType(), filter.getCategoryId(),
                        accountIds, accountIdsCount, filter.getStartDate(), filter.getEndDate(),
                        filter.getMinAmount(), filter.getMaxAmount(), filter.getIncludeBudget()
                );
                
                if (summary == null) summary = new SearchSummary(0, 0, 0, 0, 0);
                
                SearchSummary finalSummary = summary;
                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.success(finalSummary)));
            } catch (Exception e) {
                Log.e(TAG, "❌ 获取搜索汇总异常", e);
                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.error(e)));
            }
        });
    }

    /**
     * 🔍 分页搜索账单
     */
    public void searchBillsPaged(String userId, SearchFilter filter, int limit, int offset, OperationCallback<List<Bill>> callback) {
        executors.diskIO().execute(() -> {
            try {
                String keyword = filter.getKeyword();
                String searchPattern = (keyword == null || keyword.trim().isEmpty()) ? null : "%" + keyword.trim() + "%";
                List<String> accountIds = filter.getAccountIds();
                int accountIdsCount = accountIds != null ? accountIds.size() : 0;

                List<Bill> results = billDao.searchBillsAdvancedPaged(
                        userId, searchPattern, filter.getBillType(), filter.getCategoryId(),
                        accountIds, accountIdsCount, filter.getStartDate(), filter.getEndDate(),
                        filter.getMinAmount(), filter.getMaxAmount(), filter.getIncludeBudget(),
                        limit, offset
                );
                
                executors.mainThread().execute(() -> callback.onComplete(ApiResponse.success(results)));
            } catch (Exception e) {
                Log.e(TAG, "❌ 分页搜索账单异常", e);
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
                    List<Category> categories = categoryDao.getSystemPresets("expense");
                    for (int i = 0; i < Math.min(categories.size(), 8); i++) {
                        suggestions.add(categories.get(i).getName());
                    }
                } else {
                    String pattern = "%" + keyword.trim() + "%";
                    suggestions.addAll(billDao.getRemarkSuggestions(userId, pattern));
                    List<Category> categories = categoryDao.searchCategories(userId, pattern);
                    for (Category c : categories) suggestions.add(c.getName());
                    List<Account> accounts = accountDao.searchAccounts(userId, pattern);
                    for (Account a : accounts) suggestions.add(a.getName());
                    suggestions.addAll(billDao.getLocationSuggestions(userId, pattern));
                }

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

    public LiveData<List<SearchHistory>> getSearchHistory(String userId) {
        return searchHistoryDao.getSearchHistory(userId);
    }

    public void addSearchHistory(String userId, String keyword) {
        executors.diskIO().execute(() -> {
            try {
                if (keyword == null || keyword.trim().isEmpty()) return;
                searchHistoryDao.insert(new SearchHistory(userId, keyword.trim()));
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
