package com.example.my_project1.ui.viewmodel.user;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.ui.viewmodel.billvm.ImageUploadViewModel;
import com.example.my_project1.utils.OssUploadUtil;

import io.reactivex.annotations.NonNull;

/**
 * UserProfileViewModel - 用户信息ViewModel (完整优化版)
 * -------------------------------------------------------
 * ✅ 使用 ApiResponse 统一状态管理
 * ✅ 委托 ImageUploadViewModel 处理图片上传
 * ✅ 🔑 不显示上传进度，直接返回成功/失败
 * ✅ 支持离线编辑，自动同步
 */
public class UserProfileViewModel extends AndroidViewModel {

    private static final String TAG = "UserProfileViewModel";

    private final UserProfileRepository repository;
    private final ImageUploadViewModel imageUploadViewModel;

    // ==================== LiveData ====================

    private LiveData<UserProfile> userProfileLiveData;

    // 更新状态
    private final MutableLiveData<ApiResponse<Void>> _updateState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<Void>> updateState = _updateState;

    // 头像上传状态
    private final MutableLiveData<ApiResponse<String>> _avatarUploadState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<String>> avatarUploadState = _avatarUploadState;

    // 背景图上传状态
    private final MutableLiveData<ApiResponse<String>> _backgroundUploadState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<String>> backgroundUploadState = _backgroundUploadState;

    // 刷新状态
    private final MutableLiveData<ApiResponse<Void>> _refreshState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<Void>> refreshState = _refreshState;

    // 退出登录状态
    private final MutableLiveData<ApiResponse<Void>> _logoutState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<Void>> logoutState = _logoutState;

    // ==================== 构造函数 ====================

    public UserProfileViewModel(@NonNull Application application) {
        super(application);
        repository = UserProfileRepository.getInstance(application);
        imageUploadViewModel = new ImageUploadViewModel(application);
    }

    // ==================== 查询操作 ====================

    /**
     * 获取用户信息
     */
    public LiveData<UserProfile> getUserProfile(String userId) {
        if (userProfileLiveData == null) {
            userProfileLiveData = repository.getUserProfile(userId);
        }
        return userProfileLiveData;
    }

    // ==================== 更新操作 ====================

    /**
     * 更新用户信息
     */
    public void updateUserProfile(UserProfile profile) {
        _updateState.setValue(ApiResponse.loading("正在更新..."));

        repository.updateUserProfile(profile, new UserProfileRepository.UpdateCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ 用户信息更新成功");
                _updateState.postValue(ApiResponse.success(null, "更新成功"));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "❌ 用户信息更新失败: " + message);
                _updateState.postValue(ApiResponse.error(message));
            }
        });
    }

    // ==================== 图片上传操作（优化版 - 无进度提示） ====================

    /**
     * 上传并更新头像
     * 🔑 不显示上传进度，直接返回成功/失败
     */
    public void uploadAndUpdateAvatar(Uri imageUri, String userId) {
        if (imageUri == null) {
            _avatarUploadState.setValue(ApiResponse.error("图片不能为空"));
            return;
        }

        // 🔑 关键：直接显示上传中，不显示具体进度
        _avatarUploadState.setValue(ApiResponse.loading("正在上传头像..."));
        Log.d(TAG, "🚀 开始上传头像");

        // 使用 ImageUploadViewModel 上传单张图片
        imageUploadViewModel.uploadSingleImage(
                imageUri,
                userId,
                OssUploadUtil.UploadScene.AVATAR,
                new ImageUploadViewModel.SingleUploadCallback() {
                    @Override
                    public void onProgress(int percentage) {
                        // 🔑 关键：不显示进度，保持"正在上传"状态
                        Log.d(TAG, "📊 头像上传进度: " + percentage + "% (不显示给用户)");
                    }

                    @Override
                    public void onSuccess(String objectKey) {
                        Log.d(TAG, "✅ 头像上传成功: " + objectKey);

                        // 更新到数据库
                        repository.updateAvatar(userId, objectKey,
                                new UserProfileRepository.UpdateCallback() {
                                    @Override
                                    public void onSuccess() {
                                        // 🔑 直接显示成功，不显示中间过程
                                        _avatarUploadState.postValue(
                                                ApiResponse.success(objectKey, "头像更新成功")
                                        );
                                    }

                                    @Override
                                    public void onError(String message) {
                                        _avatarUploadState.postValue(
                                                ApiResponse.error("数据库更新失败: " + message)
                                        );
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "❌ 头像上传失败: " + errorMessage);
                        _avatarUploadState.postValue(ApiResponse.error("上传失败: " + errorMessage));
                    }
                });
    }

    /**
     * 上传并更新背景图
     * 🔑 不显示上传进度，直接返回成功/失败
     */
    public void uploadAndUpdateBackground(Uri imageUri, String userId) {
        if (imageUri == null) {
            _backgroundUploadState.setValue(ApiResponse.error("图片不能为空"));
            return;
        }

        // 🔑 关键：直接显示上传中，不显示具体进度
        _backgroundUploadState.setValue(ApiResponse.loading("正在上传背景图..."));
        Log.d(TAG, "🚀 开始上传背景图");

        // 使用 ImageUploadViewModel 上传单张图片
        imageUploadViewModel.uploadSingleImage(
                imageUri,
                userId,
                OssUploadUtil.UploadScene.BACKGROUND,
                new ImageUploadViewModel.SingleUploadCallback() {
                    @Override
                    public void onProgress(int percentage) {
                        // 🔑 关键：不显示进度，保持"正在上传"状态
                        Log.d(TAG, "📊 背景图上传进度: " + percentage + "% (不显示给用户)");
                    }

                    @Override
                    public void onSuccess(String objectKey) {
                        Log.d(TAG, "✅ 背景图上传成功: " + objectKey);

                        // 更新到数据库
                        repository.updateBackground(userId, objectKey,
                                new UserProfileRepository.UpdateCallback() {
                                    @Override
                                    public void onSuccess() {
                                        // 🔑 直接显示成功，不显示中间过程
                                        _backgroundUploadState.postValue(
                                                ApiResponse.success(objectKey, "背景图更新成功")
                                        );
                                    }

                                    @Override
                                    public void onError(String message) {
                                        _backgroundUploadState.postValue(
                                                ApiResponse.error("数据库更新失败: " + message)
                                        );
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "❌ 背景图上传失败: " + errorMessage);
                        _backgroundUploadState.postValue(ApiResponse.error("上传失败: " + errorMessage));
                    }
                });
    }

    /**
     * 强制刷新用户信息
     * 🎯 使用场景：用户手动下拉刷新
     */
    public void forceRefresh() {
        _refreshState.setValue(ApiResponse.loading("正在刷新..."));

        repository.forceRefresh(response -> {
            _refreshState.postValue(response);
        });
    }

    /**
     * 退出登录
     * 🔑 清除本地数据和登录状态
     */
    public void logout() {
        _logoutState.setValue(ApiResponse.loading("正在退出..."));

        repository.logout(response -> {
            if (response.isSuccess()) {
                Log.d(TAG, "✅ 退出登录成功");
                _logoutState.postValue(ApiResponse.success(null, "退出成功"));
            } else if (response.isError()) {
                Log.e(TAG, "❌ 退出登录失败: " + response.message);
                _logoutState.postValue(ApiResponse.error(response.message));
            }
        });
    }

    // ==================== 状态管理 ====================

    /**
     * 重置更新状态
     */
    public void resetUpdateState() {
        _updateState.setValue(ApiResponse.idle());
    }

    /**
     * 重置头像上传状态
     */
    public void resetAvatarUploadState() {
        _avatarUploadState.setValue(ApiResponse.idle());
    }

    /**
     * 重置背景图上传状态
     */
    public void resetBackgroundUploadState() {
        _backgroundUploadState.setValue(ApiResponse.idle());
    }

    /**
     * 重置刷新状态
     */
    public void resetRefreshState() {
        _refreshState.setValue(ApiResponse.idle());
    }

    /**
     * 重置退出登录状态
     */
    public void resetLogoutState() {
        _logoutState.setValue(ApiResponse.idle());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "🧹 ViewModel cleared");
    }
}