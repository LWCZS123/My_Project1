package com.example.my_project1.ui.viewmodel.wish;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.repository.wish.WishRepository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.bmob.v3.BmobUser;

/**
 * WishViewModel（优化版）
 * -------------------------------------------------------
 * 优化内容：
 *  1. ✅ 添加 LiveData 缓存机制，避免重复查询
 *  2. ✅ 细粒度的操作状态管理，区分 LOADING / SUCCESS / ERROR
 *  3. ✅ 强化错误处理，提供有意义的错误信息
 *  4. ✅ 优化存钱记录添加逻辑，完整传入日期和账单关联
 *  5. ✅ 减少 Toast 频率，仅在必要时提示
 *  6. ✅ 添加状态重置机制，避免重复展示
 */
public class WishViewModel extends AndroidViewModel {

    private static final String TAG = "WishViewModel";

    private final WishRepository repository;
    private String currentUserId;

    // ✅ 缓存单个愿望的 LiveData，避免重复查询
    private final Map<Long, LiveData<Wish>> wishCache = new HashMap<>();

    // ✅ 操作状态（全局）
    private final MutableLiveData<ApiResponse<String>> _operationState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> operationState = _operationState;

    // ✅ 删除操作专用状态
    private final MutableLiveData<ApiResponse<String>> _deleteState =
            new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> deleteState = _deleteState;

    // ✅ 云同步完成信号（只表示“本次同步结束”）
    private final MutableLiveData<Boolean> _cloudSyncFinished = new MutableLiveData<>();
    public LiveData<Boolean> cloudSyncFinished = _cloudSyncFinished;


    // ✅ Toast 消息（仅用于错误和警告，不用于成功提示）
    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public final LiveData<String> toastMessage = _toastMessage;

    // ✅ 操作进度（0-100）
    private final MutableLiveData<Integer> _operationProgress =
            new MutableLiveData<>(0);
    public final LiveData<Integer> operationProgress = _operationProgress;

    public WishViewModel(Application application) {
        super(application);
        repository = new WishRepository(application);
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        if (user != null) {
            currentUserId = user.getObjectId();
            Log.d(TAG, "当前用户: " + currentUserId);
        } else {
            Log.w(TAG, "用户未登录");
        }
    }

    // ======================== 查询 ========================

    /**
     * ✅ 获取用户所有愿望（使用 LiveData 自动更新）
     */
    public LiveData<List<Wish>> getAllWishes() {
        return repository.getAllWishesByUser(currentUserId);
    }

    /**
     * ✅ 获取单个愿望（带缓存）
     * 避免多次查询相同的愿望
     */
    public LiveData<Wish> getWishById(long id) {
        if (!wishCache.containsKey(id)) {
            LiveData<Wish> liveData = repository.getWishById(id);
            wishCache.put(id, liveData);
            Log.d(TAG, "缓存愿望: id=" + id);
        }
        return wishCache.get(id);
    }

    /**
     * ✅ 按状态获取愿望列表
     */
    public LiveData<List<Wish>> getWishesByStatus(int status) {
        return repository.getWishesByStatus(currentUserId, status);
    }

    /**
     * ✅ 获取愿望的所有存钱记录
     */
    public LiveData<List<WishRecord>> getRecordsForWish(long wishId) {
        return repository.getRecordsByWishId(wishId);
    }

    // ======================== 操作 ========================

    /**
     * ✅ 新增愿望
     */
    public void insertWish(Wish wish) {
        if (currentUserId == null) {
            _operationState.setValue(ApiResponse.error("用户未登录"));
            Log.e(TAG, "insertWish: 用户未登录");
            return;
        }

        if (wish == null) {
            _operationState.setValue(ApiResponse.error("愿望数据为空"));
            return;
        }

        wish.setUserId(currentUserId);
        _operationState.setValue(ApiResponse.loading("正在创建愿望..."));
        _operationProgress.setValue(0);

        repository.insertWish(wish, response -> {
            Log.d(TAG, "insertWish 结果: " + response.getMessage());

            if (response.isSuccess()) {
                _operationProgress.setValue(100);
                _operationState.setValue(ApiResponse.success("愿望已创建"));
            } else {
                _operationState.setValue(ApiResponse.error(
                        response.getMessage() != null ?
                                response.getMessage() : "创建失败"));
            }
        });
    }

    /**
     * ✅ 更新愿望
     * 修改目标金额或其他信息后，自动重新计算状态
     */
    public void updateWish(Wish wish) {
        if (wish == null) {
            _operationState.setValue(ApiResponse.error("愿望数据为空"));
            return;
        }

        _operationState.setValue(ApiResponse.loading("正在保存修改..."));

        // ✅ 更新前重新计算状态
        wish.setStatus(wish.getRealStatus());

        Log.d(TAG, "updateWish: id=" + wish.getId()
                + ", name=" + wish.getWishName()
                + ", realStatus=" + wish.getRealStatus());

        repository.updateWish(wish, response -> {
            if (response.isSuccess()) {
                _operationState.setValue(ApiResponse.success("已保存"));
                // 清除缓存，强制重新加载
                wishCache.remove(wish.getId());
            } else {
                _operationState.setValue(ApiResponse.error(
                        response.getMessage() != null ?
                                response.getMessage() : "保存失败"));
            }
        });
    }

    /**
     * ✅ 删除愿望
     * 触发级联删除：存钱记录 + 关联账单 + 云端数据
     */
    public void deleteWish(Wish wish) {
        if (wish == null) {
            _deleteState.setValue(ApiResponse.error("愿望数据为空"));
            return;
        }

        _deleteState.setValue(ApiResponse.loading("正在删除愿望..."));
        _operationState.setValue(ApiResponse.loading("正在删除..."));
        _operationProgress.setValue(0);

        Log.d(TAG, "deleteWish 开始: id=" + wish.getId()
                + ", name=" + wish.getWishName());

        repository.deleteWish(wish, response -> {
            Log.d(TAG, "deleteWish 完成: " + response.getMessage());

            if (response.isSuccess()) {
                _operationProgress.setValue(100);
                _deleteState.setValue(ApiResponse.success("已删除"));
                _operationState.setValue(ApiResponse.success("已删除"));

                // 清除缓存
                wishCache.remove(wish.getId());

                Log.d(TAG, "deleteWish 成功，已清除缓存");
            } else {
                String errMsg = response.getMessage() != null ?
                        response.getMessage() : "删除失败";
                _deleteState.setValue(ApiResponse.error(errMsg));
                _operationState.setValue(ApiResponse.error(errMsg));
                _toastMessage.setValue("删除失败: " + errMsg);

                Log.e(TAG, "deleteWish 失败: " + errMsg);
            }
        });
    }

    /**
     * ✅ 添加存钱记录（基础版，兼容旧调用）
     * 日期默认为当前时间，无关联账单
     */
    public void addSavingRecord(long wishId, double amount, String note) {
        addSavingRecord(wishId, amount, note, new Date(), -1);
    }

    /**
     * ✅ 添加存钱记录（完整版）
     *
     * @param wishId        目标愿望本地 ID
     * @param amount        存入金额
     * @param note          备注
     * @param recordDate    存入日期（由 UI 传入，可精确到分钟）
     * @param linkedBillId  关联账单本地 ID，-1 表示无关联
     */
    public void addSavingRecord(long wishId, double amount, String note,
                                Date recordDate, long linkedBillId) {
        if (currentUserId == null) {
            _operationState.setValue(ApiResponse.error("用户未登录"));
            return;
        }

        if (amount <= 0) {
            _operationState.setValue(ApiResponse.error("金额必须大于 0"));
            return;
        }

        _operationState.setValue(ApiResponse.loading("正在添加记录..."));
        _operationProgress.setValue(0);

        Log.d(TAG, "addSavingRecord: wishId=" + wishId
                + ", amount=" + amount
                + ", linkedBillId=" + linkedBillId
                + ", recordDate=" + recordDate);

        repository.addSavingRecord(wishId, amount, note, recordDate, linkedBillId, response -> {
            Log.d(TAG, "addSavingRecord 完成: " + response.getMessage());

            if (response.isSuccess()) {
                _operationProgress.setValue(100);
                _operationState.setValue(ApiResponse.success("记录已添加"));

                // ✅ 同步更新当前愿望的缓存（局部更新）
                if (wishCache.containsKey(wishId)) {
                    // 清除缓存，触发重新加载
                    wishCache.remove(wishId);
                }

            } else {
                String errMsg = response.getMessage() != null ?
                        response.getMessage() : "添加失败";
                _operationState.setValue(ApiResponse.error(errMsg));
                _toastMessage.setValue("添加失败: " + errMsg);

                Log.e(TAG, "addSavingRecord 失败: " + errMsg);
            }
        });
    }

    /**
     * ✅ 删除存钱记录
     * 同时级联删除关联的账单
     */
    public void deleteSavingRecord(WishRecord record) {
        if (record == null) {
            _toastMessage.setValue("记录数据为空");
            return;
        }

        Log.d(TAG, "deleteSavingRecord: id=" + record.getId()
                + ", linkedBillId=" + record.getLinkedBillId());

        repository.deleteSavingRecord(record, response -> {
            if (response.isSuccess()) {
                // ✅ 仅在成功时显示 Snackbar，不使用 Toast
                _toastMessage.setValue("✓ 记录已删除");

                // 清除关联愿望的缓存，触发重新加载
                if (record.getWishId() > 0) {
                    wishCache.remove(record.getWishId());
                }

                Log.d(TAG, "deleteSavingRecord 成功");
            } else {
                String errMsg = response.getMessage() != null ?
                        response.getMessage() : "删除失败";
                _toastMessage.setValue("删除失败: " + errMsg);

                Log.e(TAG, "deleteSavingRecord 失败: " + errMsg);
            }
        });
    }

    /**
     * ✅ 从云端同步愿望数据
     */
    public void syncWishesFromCloud() {
        if (currentUserId == null) {
            Log.w(TAG, "syncWishesFromCloud: 用户未登录");
            return;
        }

        _operationProgress.postValue(50);
        _cloudSyncFinished.postValue(false); // 同步开始

        repository.syncWishesFromCloud(currentUserId, response -> {
            Log.d(TAG, "云端同步结果: " + response.getMessage());

            if (response.isSuccess()) {
                _operationProgress.postValue(100);
                wishCache.clear();

                // ✅ 通知：云 → 本地 同步完成
                _cloudSyncFinished.postValue(true);
            } else {
                Log.e(TAG, "云端同步失败: " + response.getMessage());
                _cloudSyncFinished.postValue(false);
            }
        });
    }

    /**
     * ✅ 从云端同步愿望存钱记录
     */
    public void syncAllFromCloud() {
        if (currentUserId == null) return;

        _cloudSyncFinished.postValue(false); // 同步开始
        _operationState.postValue(ApiResponse.loading("同步中..."));

        repository.syncWishesFromCloud(currentUserId, wishRes -> {

            if (!wishRes.isSuccess()) {
                _operationState.postValue(ApiResponse.error("愿望同步失败"));
                _cloudSyncFinished.postValue(false);
                return;
            }

            // ✅ Wishes 同步完成后，再同步 Records
            repository.syncRecordsFromCloud(currentUserId, recordRes -> {

                if (recordRes.isSuccess()) {
                    wishCache.clear();
                    _operationState.postValue(ApiResponse.success("同步完成"));

                    // ✅ 只有这里，才算“真正完成”
                    _cloudSyncFinished.postValue(true);

                } else {
                    _operationState.postValue(ApiResponse.error("记录同步失败"));
                    _cloudSyncFinished.postValue(false);
                }
            });
        });
    }

    /**
     * ✅ 重置操作状态
     * 清空当前的加载/错误状态，恢复到空闲
     */
    public void resetOperationState() {
        _operationState.setValue(ApiResponse.idle());
        _deleteState.setValue(ApiResponse.idle());
        _operationProgress.setValue(0);
        Log.d(TAG, "操作状态已重置");
    }

    /**
     * ✅ 清除所有缓存
     * 在用户切换或数据大变更时调用
     */
    public void clearCache() {
        wishCache.clear();
        Log.d(TAG, "所有缓存已清除");
    }

    /**
     * ✅ 预加载单个愿望数据
     * 在 Activity 创建时调用，缩短首次显示时间
     */
    public void preloadWishData(long wishId) {
        if (!wishCache.containsKey(wishId)) {
            getWishById(wishId); // 触发缓存加载
        }
    }
}