package com.example.my_project1.ui.viewmodel.wish;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.repository.wish.WishRepository;

import java.util.List;

import cn.bmob.v3.BmobUser;

public class WishViewModel extends AndroidViewModel {

    private final WishRepository repository;
    private String currentUserId;

    //观察操作状态
    private final MutableLiveData<ApiResponse<String>> _operationState =
            new MutableLiveData<>(ApiResponse.idle());


    public LiveData<ApiResponse<String>> getOperationState() {
        return _operationState;
    }

    public WishViewModel(Application application) {
        super(application);
        this.repository = new WishRepository(application);
        // 初始化当前用户 ID
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        if (user != null) {
            currentUserId = user.getObjectId();
        }

    }

    public LiveData<List<Wish>> getAllWishes() {
        return repository.getAllWishesByUser(currentUserId);
    }

    public LiveData<Wish> getWishById(long id) {
        return repository.getWishById(id);
    }

    public void insertWish(Wish wish) {

        if (wish == null) {
            _operationState.setValue(
                    ApiResponse.error("愿望数据不能为空")
            );
            return;
        }

        if (currentUserId == null || currentUserId.isEmpty()) {
            _operationState.setValue(
                    ApiResponse.error("用户未登录")
            );
            return;
        }

        wish.setUserId(currentUserId);

        _operationState.setValue(
                ApiResponse.loading("正在保存愿望")
        );

        repository.insertWish(wish, response ->{
            _operationState.setValue(response.mapMessage("愿望保存成功"));
        });
    }

    /**
     * 处理完操作后，重置状态为 idle，防止 UI 重复提示
     */
    public void resetOperationState() {
        _operationState.setValue(ApiResponse.idle());
    }


    public void updateWish(Wish wish) {
        if (wish == null) {
            _operationState.setValue(
                    ApiResponse.error("更新失败")
            );
            return;
        }
        if (currentUserId == null || currentUserId.isEmpty()) {
            _operationState.setValue(
                    ApiResponse.error("用户未登录")
            );
            return;
        }
        wish.setUserId(currentUserId);
        _operationState.setValue(
                ApiResponse.loading("正在更新")
        );
        repository.updateWish(wish,response -> {
            _operationState.setValue(response.mapMessage("愿望更新成功"));
        });


    }

    public void deleteWish(Wish wish) {
//        repository.deleteWish(wish);
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
}
