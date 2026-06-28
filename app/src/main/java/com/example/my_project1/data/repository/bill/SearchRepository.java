package com.example.my_project1.data.repository.bill;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.SearchHistoryDao;
import com.example.my_project1.data.database.AppDatabase;
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
 * -------------------------------------------------------
 * ✅ 功能:
 * 1. 关键词搜索账单(模糊查询)
 * 2. 筛选条件搜索账单
 * 3. 管理搜索历史记录
 *
 * ✅ 重构:
 * 使用统一的 ApiResponse 封装类
 */
public class SearchRepository {

    private static final String TAG = "SearchRepository";

    private final BillDao billDao;
    private final SearchHistoryDao searchHistoryDao;
    private final AppExecutors executors;

    // ==================== 构造函数 ====================

    public SearchRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.billDao = db.billDao();
        this.searchHistoryDao = db.searchHistoryDao();
        this.executors = AppExecutors.get();
    }

    // ==================== 回调接口 ====================

    /**
     * 操作回调接口
     */
    public interface OperationCallback<T> {
        void onComplete(ApiResponse<T> result);
    }

    // ==================== 搜索账单 ====================

    /**
     * 🔍 搜索账单(关键词搜索)
     * 支持搜索: 分类名称、备注、地点
     */
    public void searchBills(String userId, String keyword, OperationCallback<List<Bill>> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (keyword == null || keyword.trim().isEmpty()) {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.success(null))
                    );
                    return;
                }

                String searchPattern = "%" + keyword.trim() + "%";
                Log.d(TAG, "🔍 开始搜索: userId=" + userId + ", keyword=" + keyword);

                // 在后台线程同步查询
                List<Bill> results = billDao.searchBills(userId, searchPattern);

                Log.d(TAG, "✅ 搜索完成: 找到 " + (results != null ? results.size() : 0) + " 条结果");

                List<Bill> finalResults = results;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalResults))
                );
            } catch (Exception e) {
                Log.e(TAG, "❌ 搜索账单异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 🔍 搜索账单 (带筛选条件)
     */
    public void searchBillsWithFilter(String userId, String keyword, SearchFilter filter,
                                      OperationCallback<List<Bill>> callback) {
        executors.diskIO().execute(() -> {
            try {
                Log.d(TAG, "🔍 开始筛选搜索: userId=" + userId + ", keyword=" + keyword);

                // 1. 先通过关键词搜索获取基础结果
                List<Bill> bills;

                if (keyword != null && !keyword.trim().isEmpty()) {
                    String searchPattern = "%" + keyword.trim() + "%";
                    bills = billDao.searchBills(userId, searchPattern);
                } else {
                    // 没有关键词则查询所有账单
                    bills = billDao.getAllBillsSync();

                    // 过滤出当前用户的账单
                    List<Bill> userBills = new ArrayList<>();
                    for (Bill bill : bills) {
                        if (userId.equals(bill.getUserId())) {
                            userBills.add(bill);
                        }
                    }
                    bills = userBills;
                }

                Log.d(TAG, "📊 关键词搜索结果: " + (bills != null ? bills.size() : 0) + " 条");

                // 2. 应用筛选条件
                if (filter != null && filter.hasAnyFilter()) {
                    bills = applyFilters(bills, filter);
                    Log.d(TAG, "📊 筛选后结果: " + (bills != null ? bills.size() : 0) + " 条");
                }

                List<Bill> finalBills = bills;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalBills))
                );

                Log.d(TAG, "✅ 筛选搜索完成: " + (finalBills != null ? finalBills.size() : 0) + " 条结果");

            } catch (Exception e) {
                Log.e(TAG, "❌ 筛选搜索异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 🔥 应用筛选条件 (内存过滤)
     */
    private List<Bill> applyFilters(List<Bill> bills, SearchFilter filter) {
        if (bills == null || bills.isEmpty()) {
            return new ArrayList<>();
        }

        List<Bill> filtered = new ArrayList<>();

        for (Bill bill : bills) {
            // 日期筛选
            if (filter.getStartDate() != null) {
                if (bill.getBillTime().before(filter.getStartDate())) {
                    continue;
                }
            }

            if (filter.getEndDate() != null) {
                if (bill.getBillTime().after(filter.getEndDate())) {
                    continue;
                }
            }

            // 金额筛选
            double amount = bill.getAmount();
            if (filter.getMinAmount() != null) {
                if (amount < filter.getMinAmount()) {
                    continue;
                }
            }

            if (filter.getMaxAmount() != null) {
                if (amount > filter.getMaxAmount()) {
                    continue;
                }
            }

            // 账户筛选 (支持多账户)
            if (filter.getAccountIds() != null && !filter.getAccountIds().isEmpty()) {
                String billAccountId = bill.getAccountId();
                if (billAccountId == null || !filter.getAccountIds().contains(billAccountId)) {
                    continue;
                }
            }

            // 备注筛选
            if (filter.getRemarkKeyword() != null && !filter.getRemarkKeyword().isEmpty()) {
                String remark = bill.getRemark();
                if (remark == null || !remark.contains(filter.getRemarkKeyword())) {
                    continue;
                }
            }

            // 账本筛选
            if (filter.getBookId() != null) {
                if (!filter.getBookId().equals(bill.getBookId())) {
                    continue;
                }
            }

            // 分类筛选
            if (filter.getCategoryId() != null) {
                if (!filter.getCategoryId().equals(bill.getCategoryId())) {
                    continue;
                }
            }

            // 通过所有筛选条件
            filtered.add(bill);
        }

        Log.d(TAG, "🔍 筛选结果: " + bills.size() + " -> " + filtered.size());
        return filtered;
    }

    // ==================== 搜索历史管理 ====================

    /**
     * 获取搜索历史
     */
    public LiveData<List<SearchHistory>> getSearchHistory(String userId) {
        return searchHistoryDao.getSearchHistory(userId);
    }

    /**
     * 添加搜索历史 (优化版)
     */
    public void addSearchHistory(String userId, String keyword, OperationCallback<Long> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (keyword == null || keyword.trim().isEmpty()) {
                    executors.mainThread().execute(() ->
                            callback.onComplete(ApiResponse.error("关键词为空"))
                    );
                    return;
                }

                String trimmedKeyword = keyword.trim();

                // 查询关键词是否已存在
                SearchHistory existingHistory = searchHistoryDao.findByKeyword(userId, trimmedKeyword);

                long resultId;

                if (existingHistory != null) {
                    // 关键词已存在 - 更新search_time
                    existingHistory.setSearchTime(new Date());
                    int rows = searchHistoryDao.update(existingHistory);
                    resultId = existingHistory.getId();

                    Log.d(TAG, "🔄 更新搜索历史: keyword=" + trimmedKeyword +
                            ", id=" + resultId + ", rows=" + rows);

                } else {
                    // 关键词不存在 - 插入新记录
                    SearchHistory newHistory = new SearchHistory(userId, trimmedKeyword);
                    resultId = searchHistoryDao.insert(newHistory);

                    Log.d(TAG, "➕ 新增搜索历史: keyword=" + trimmedKeyword +
                            ", id=" + resultId);
                }

                long finalResultId = resultId;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalResultId))
                );

            } catch (Exception e) {
                Log.e(TAG, "❌ 添加搜索历史异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 删除单条搜索历史
     */
    public void deleteSearchHistory(SearchHistory history, OperationCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                int rows = searchHistoryDao.delete(history);

                Log.d(TAG, "✅ 删除搜索历史: id=" + history.getId() + ", rows=" + rows);

                int finalRows = rows;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalRows))
                );
            } catch (Exception e) {
                Log.e(TAG, "❌ 删除搜索历史异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 根据ID删除搜索历史
     */
    public void deleteSearchHistoryById(long id, OperationCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                int rows = searchHistoryDao.deleteById(id);

                Log.d(TAG, "✅ 删除搜索历史: id=" + id + ", rows=" + rows);

                int finalRows = rows;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalRows))
                );
            } catch (Exception e) {
                Log.e(TAG, "❌ 删除搜索历史异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }

    /**
     * 清空所有搜索历史
     */
    public void clearAllHistory(String userId, OperationCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                int rows = searchHistoryDao.clearHistory(userId);

                Log.d(TAG, "✅ 清空搜索历史: userId=" + userId + ", rows=" + rows);

                int finalRows = rows;
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.success(finalRows))
                );
            } catch (Exception e) {
                Log.e(TAG, "❌ 清空搜索历史异常", e);
                executors.mainThread().execute(() ->
                        callback.onComplete(ApiResponse.error(e))
                );
            }
        });
    }
}