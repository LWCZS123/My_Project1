package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchFilter;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.bill.SearchRepository;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * SearchViewModel - 搜索功能ViewModel (重构版 - 使用 ApiResponse)
 * -------------------------------------------------------
 * ✅ 功能:
 * 1. 关键词搜索
 * 2. 筛选条件搜索
 * 3. 管理搜索历史
 * 4. 状态管理
 *
 * ✅ 重构优化:
 * 1. 使用统一的 ApiResponse 替代 UiState
 * 2. 代码更简洁，状态管理更清晰
 * 3. 减少重复代码
 */
public class SearchViewModel extends AndroidViewModel {

    private static final String TAG = "SearchViewModel";

    // Repository
    private final SearchRepository repository;

    // 当前用户ID
    private String currentUserId;

    // 🔥 当前筛选条件
    private SearchFilter currentFilter;

    // ==================== LiveData ====================

    // 搜索结果
    private final MutableLiveData<List<Bill>> _searchResults = new MutableLiveData<>();
    public final LiveData<List<Bill>> searchResults = _searchResults;

    // 搜索历史
    private LiveData<List<SearchHistory>> searchHistory;

    // 搜索状态 (使用 ApiResponse)
    private final MutableLiveData<ApiResponse<String>> _searchState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> searchState = _searchState;

    // Toast消息
    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    // ==================== 构造函数 ====================

    public SearchViewModel(@NonNull Application application) {
        super(application);

        repository = new SearchRepository(application);

        // 获取当前用户ID
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
            // 初始化搜索历史
            searchHistory = repository.getSearchHistory(currentUserId);
        } else {
            currentUserId = null;
            Log.w(TAG, "⚠️ 用户未登录");
        }
    }

    // ==================== 公开方法 ====================

    /**
     * 获取搜索历史
     */
    public LiveData<List<SearchHistory>> getSearchHistory() {
        return searchHistory;
    }

    /**
     * 🔍 搜索账单 (关键词搜索)
     */
    public void searchBills(String keyword) {
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            return;
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            // 清空搜索结果
            _searchResults.setValue(null);
            _searchState.setValue(ApiResponse.idle());
            return;
        }

        // 保存搜索历史
        addSearchHistory(keyword.trim());

        _searchState.setValue(ApiResponse.loading());
        Log.d(TAG, "🔍 开始搜索: keyword=" + keyword);

        repository.searchBills(currentUserId, keyword, response -> {
            if (response.isSuccess()) {
                List<Bill> bills = response.data;

                if (bills == null || bills.isEmpty()) {
                    _searchResults.setValue(null);
                    _searchState.setValue(ApiResponse.empty("未找到相关结果"));
                    Log.d(TAG, "🔍 搜索结果为空");
                } else {
                    _searchResults.setValue(bills);
                    _searchState.setValue(ApiResponse.success("找到 " + bills.size() + " 条结果"));
                    Log.d(TAG, "✅ 搜索成功: " + bills.size() + " 条结果");
                }
            } else {
                _searchState.setValue(ApiResponse.error(response.message));
                _toastMessage.setValue("搜索失败: " + response.message);
                Log.e(TAG, "❌ 搜索失败: " + response.message);
            }
        });
    }

    /**
     * 🔍 搜索账单 (带筛选条件)
     */
    public void searchBillsWithFilter(String keyword, SearchFilter filter) {
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            return;
        }

        // 保存筛选条件
        this.currentFilter = filter;

        // 如果有关键词，保存搜索历史
        if (keyword != null && !keyword.trim().isEmpty()) {
            addSearchHistory(keyword.trim());
        }

        _searchState.setValue(ApiResponse.loading("正在筛选..."));
        Log.d(TAG, "🔍 开始筛选搜索: keyword=" + keyword + ", filter=" + filter);

        repository.searchBillsWithFilter(currentUserId, keyword, filter, response -> {
            if (response.isSuccess()) {
                List<Bill> bills = response.data;

                if (bills == null || bills.isEmpty()) {
                    _searchResults.setValue(null);
                    _searchState.setValue(ApiResponse.empty("未找到符合条件的结果"));
                    Log.d(TAG, "🔍 筛选结果为空");
                } else {
                    _searchResults.setValue(bills);

                    // 构建结果提示信息
                    String message = buildResultMessage(bills.size(), filter);
                    _searchState.setValue(ApiResponse.success(message));
                    Log.d(TAG, "✅ 筛选搜索成功: " + bills.size() + " 条结果");
                }
            } else {
                _searchState.setValue(ApiResponse.error(response.message));
                _toastMessage.setValue("搜索失败: " + response.message);
                Log.e(TAG, "❌ 筛选搜索失败: " + response.message);
            }
        });
    }

    /**
     * 🔥 构建搜索结果提示信息
     */
    private String buildResultMessage(int count, SearchFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(count).append(" 条结果");

        if (filter != null && filter.hasAnyFilter()) {
            sb.append(" (");

            List<String> conditions = new ArrayList<>();
            if (filter.getStartDate() != null || filter.getEndDate() != null) {
                conditions.add("日期");
            }
            if (filter.getMinAmount() != null || filter.getMaxAmount() != null) {
                conditions.add("金额");
            }
            if (filter.getAccountIds() != null && !filter.getAccountIds().isEmpty()) {
                conditions.add(filter.getAccountIds().size() + "个账户");
            }
            if (filter.getRemarkKeyword() != null) {
                conditions.add("备注");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sb.append(String.join(", ", conditions));
            }
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * 🔥 获取当前筛选条件
     */
    public SearchFilter getCurrentFilter() {
        return currentFilter;
    }

    /**
     * 添加搜索历史
     */
    private void addSearchHistory(String keyword) {
        repository.addSearchHistory(currentUserId, keyword, response -> {
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 保存搜索历史: " + keyword);
            } else {
                Log.e(TAG, "❌ 保存搜索历史失败: " + response.message);
            }
        });
    }

    /**
     * 删除单条搜索历史
     */
    public void deleteSearchHistory(SearchHistory history) {
        repository.deleteSearchHistory(history, response -> {
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 删除搜索历史成功");
            } else {
                _toastMessage.setValue("删除失败");
                Log.e(TAG, "❌ 删除搜索历史失败: " + response.message);
            }
        });
    }

    /**
     * 清空所有搜索历史
     */
    public void clearAllHistory() {
        if (currentUserId == null) {
            _toastMessage.setValue("请先登录");
            return;
        }

        repository.clearAllHistory(currentUserId, response -> {
            if (response.isSuccess()) {
                _toastMessage.setValue("已清空搜索历史");
                Log.d(TAG, "✅ 清空搜索历史成功");
            } else {
                _toastMessage.setValue("清空失败");
                Log.e(TAG, "❌ 清空搜索历史失败: " + response.message);
            }
        });
    }

    /**
     * 重置搜索状态
     */
    public void resetSearchState() {
        _searchState.setValue(ApiResponse.idle());
        _searchResults.setValue(null);
        currentFilter = null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "🧹 ViewModel cleared");
    }
}