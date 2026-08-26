package com.example.my_project1.ui.viewmodel.wish;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.model.wish.WishWithRecords;
import com.example.my_project1.data.repository.wish.WishRepository;

import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * WishViewModel - 愿望模块 ViewModel
 * -------------------------------------------------------
 * 遵循单向数据流 (UDF) 模式：
 * 1. 状态观察：通过 LiveData 观察数据（Repository 负责写，DAO 提供流）。
 * 2. 行为分发：界面触发 save/delete 等动作。
 */
public class WishViewModel extends AndroidViewModel {

    private final WishRepository repository;
    private final WishDao wishDao;
    
    // 状态定义
    private final MutableLiveData<String> currentUserId = new MutableLiveData<>();
    private final MutableLiveData<ApiResponse<Long>> operationState =
            new MutableLiveData<>(ApiResponse.idle());

    public WishViewModel(Application application) {
        super(application);
        repository = new WishRepository(application);
        wishDao = repository.getWishDao();
        
        // 初始化当前用户
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        currentUserId.setValue(user == null ? "" : user.getObjectId());
    }

    // ================== 数据查询 (观察流) ==================

    /**
     * 获取当前用户的所有愿望
     */
    public LiveData<List<Wish>> getAllWishes() {
        return Transformations.switchMap(currentUserId, userId -> 
                wishDao.getAllWishesByUser(userId == null ? "" : userId));
    }

    /**
     * 根据 ID 获取愿望详情
     */
    public LiveData<Wish> getWishById(long id) {
        return wishDao.getWishById(id);
    }

    /**
     * 获取愿望及其关联记录
     */
    public LiveData<WishWithRecords> getWishWithRecords(long id) {
        return wishDao.getWishWithRecords(id);
    }

    /**
     * 获取指定愿望的所有记录
     */
    public LiveData<List<WishRecord>> getRecords(long wishId) {
        return wishDao.getRecordsByWish(wishId);
    }

    /**
     * 获取单条记录详情
     */
    public LiveData<WishRecord> getRecord(long recordId) {
        return wishDao.getRecordById(recordId);
    }

    // ================== 操作分发 (Action) ==================

    public LiveData<ApiResponse<Long>> getOperationState() {
        return operationState;
    }

    public void saveWish(Wish wish) {
        if (!validateWish(wish)) return;
        operationState.setValue(ApiResponse.loading("正在保存"));
        if (wish.getId() == 0) {
            wish.setUserId(currentUserId.getValue());
            repository.insertWish(wish, operationState::setValue);
        } else {
            repository.updateWish(wish, operationState::setValue);
        }
    }

    public void deleteWish(long wishId) {
        operationState.setValue(ApiResponse.loading("正在删除"));
        repository.deleteWish(wishId, operationState::setValue);
    }

    public void saveRecord(WishRecord record) {
        if (record == null || record.getWishId() <= 0) {
            operationState.setValue(ApiResponse.error("记录数据无效"));
            return;
        }
        if (record.getAmount() <= 0) {
            operationState.setValue(ApiResponse.error("存入金额必须大于 0"));
            return;
        }
        operationState.setValue(ApiResponse.loading("正在保存记录"));
        if (record.getId() == 0) {
            repository.insertRecord(record, operationState::setValue);
        } else {
            repository.updateRecord(record, operationState::setValue);
        }
    }

    public void deleteRecord(long recordId) {
        operationState.setValue(ApiResponse.loading("正在删除记录"));
        repository.deleteRecord(recordId, operationState::setValue);
    }

    public void syncNow() {
        repository.syncNow();
    }

    public void resetOperationState() {
        operationState.setValue(ApiResponse.idle());
    }

    /**
     * 用户切换或重新登录时刷新数据
     */
    public void refreshUser() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        currentUserId.setValue(user == null ? "" : user.getObjectId());
    }

    private boolean validateWish(Wish wish) {
        String userId = currentUserId.getValue();
        if (userId == null || userId.trim().isEmpty()) {
            operationState.setValue(ApiResponse.error("请先登录后再管理愿望"));
            return false;
        }
        if (wish == null || wish.getWishName() == null || wish.getWishName().trim().isEmpty()) {
            operationState.setValue(ApiResponse.error("请输入愿望名称"));
            return false;
        }
        if (wish.getTargetAmount() <= 0) {
            operationState.setValue(ApiResponse.error("目标金额必须大于 0"));
            return false;
        }
        if (wish.getStartDate() == null) {
            operationState.setValue(ApiResponse.error("请选择开始日期"));
            return false;
        }
        return true;
    }
}
