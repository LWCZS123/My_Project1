package com.example.my_project1.ui.viewmodel.billvm;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.utils.OssUploadUtil;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * ImageUploadViewModel - 图片上传ViewModel (优化版)
 * -------------------------------------------------------
 * ✅ 使用 ApiResponse 统一状态管理
 * ✅ 优化单图上传性能 (新增专用方法)
 * ✅ 支持批量上传
 * ✅ 符合 MVVM 架构
 */
public class ImageUploadViewModel extends AndroidViewModel {

    private static final String TAG = "ImageUploadViewModel";

    // ==================== 上传进度数据类 ====================

    public static class UploadProgress {
        public final int current;
        public final int total;
        public final int percentage;

        public UploadProgress(int current, int total, int percentage) {
            this.current = current;
            this.total = total;
            this.percentage = percentage;
        }

        public String getProgressText() {
            return current + "/" + total + " (" + percentage + "%)";
        }

        public boolean isCompleted() {
            return current == total && total > 0;
        }
    }

    // ==================== LiveData ====================

    // 批量上传状态 - 使用 ApiResponse<List<String>>
    private final MutableLiveData<ApiResponse<List<String>>> _batchUploadState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<List<String>>> batchUploadState = _batchUploadState;

    // 单图上传状态 - 使用 ApiResponse<String> (性能优化)
    private final MutableLiveData<ApiResponse<String>> _singleUploadState =
            new MutableLiveData<>(ApiResponse.idle());
    public LiveData<ApiResponse<String>> singleUploadState = _singleUploadState;

    // 上传进度
    private final MutableLiveData<UploadProgress> _uploadProgress = new MutableLiveData<>();
    public LiveData<UploadProgress> uploadProgress = _uploadProgress;

    // ==================== 构造函数 ====================

    public ImageUploadViewModel(@NonNull Application application) {
        super(application);
    }

    // ==================== 单图上传 (性能优化) ====================

    /**
     * 上传单张图片 (性能优化版)
     * ✅ 专门针对单图优化，减少不必要的 List 操作
     * ✅ 直接返回 objectKey 字符串，避免包装
     * ✅ 适用于头像、背景图等单图场景
     *
     * @param imageUri 图片 URI
     * @param userId 用户 ID
     * @param scene 上传场景
     * @param callback 上传回调 (可选，用于外部监听)
     */
    public void uploadSingleImage(
            Uri imageUri,
            String userId,
            OssUploadUtil.UploadScene scene,
            SingleUploadCallback callback) {

        if (imageUri == null) {
            ApiResponse<String> error = ApiResponse.error("图片不能为空");
            _singleUploadState.setValue(error);
            if (callback != null) callback.onFailure("图片不能为空");
            return;
        }

        Log.d(TAG, "🚀 开始单图上传: " + scene.name());

        _singleUploadState.setValue(ApiResponse.loading("正在上传..."));
        _uploadProgress.setValue(new UploadProgress(0, 1, 0));

        // 使用 List 包装，复用批量上传接口
        List<Uri> uris = new ArrayList<>(1);
        uris.add(imageUri);

        OssUploadUtil.uploadFiles(
                getApplication(),
                uris,
                userId,
                scene,
                new OssUploadUtil.BatchUploadCallback() {
                    @Override
                    public void onProgress(int current, int total, int percentage) {
                        UploadProgress progress = new UploadProgress(current, total, percentage);
                        _uploadProgress.postValue(progress);

                        // 外部回调
                        if (callback != null) {
                            callback.onProgress(percentage);
                        }

                        Log.d(TAG, "📊 上传进度: " + percentage + "%");
                    }

                    @Override
                    public void onSuccess(List<OssUploadUtil.UploadResult> results) {
                        if (results != null && !results.isEmpty()) {
                            String objectKey = results.get(0).objectKey;

                            Log.d(TAG, "✅ 单图上传成功: " + objectKey);

                            ApiResponse<String> success = ApiResponse.success(
                                    objectKey,
                                    "上传成功"
                            );
                            _singleUploadState.postValue(success);

                            // 外部回调
                            if (callback != null) {
                                callback.onSuccess(objectKey);
                            }
                        } else {
                            String errorMsg = "上传结果为空";
                            _singleUploadState.postValue(ApiResponse.error(errorMsg));
                            if (callback != null) {
                                callback.onFailure(errorMsg);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        String message = e != null ? e.getMessage() : "上传失败";

                        Log.e(TAG, "❌ 单图上传失败: " + message);

                        _singleUploadState.postValue(ApiResponse.error(message));

                        // 外部回调
                        if (callback != null) {
                            callback.onFailure(message);
                        }
                    }
                }
        );
    }

    /**
     * 上传单张图片 (简化版，无回调)
     */
    public void uploadSingleImage(Uri imageUri, String userId, OssUploadUtil.UploadScene scene) {
        uploadSingleImage(imageUri, userId, scene, null);
    }

    // ==================== 批量上传 ====================

    /**
     * 批量上传图片
     * ✅ 适用于多图场景 (如账单图片、相册等)
     *
     * @param imageUris 图片 URI 列表
     * @param userId 用户 ID
     * @param scene 上传场景
     */
    public void uploadBatchImages(
            List<Uri> imageUris,
            String userId,
            OssUploadUtil.UploadScene scene) {

        if (imageUris == null || imageUris.isEmpty()) {
            _batchUploadState.setValue(ApiResponse.error("图片列表为空"));
            return;
        }

        Log.d(TAG, "🚀 开始批量上传: " + imageUris.size() + " 张图片");

        _batchUploadState.setValue(ApiResponse.loading("正在上传..."));
        _uploadProgress.setValue(new UploadProgress(0, imageUris.size(), 0));

        OssUploadUtil.uploadFiles(
                getApplication(),
                imageUris,
                userId,
                scene,
                new OssUploadUtil.BatchUploadCallback() {
                    @Override
                    public void onProgress(int current, int total, int percentage) {
                        UploadProgress progress = new UploadProgress(current, total, percentage);
                        _uploadProgress.postValue(progress);
                        Log.d(TAG, "📊 批量上传进度: " + current + "/" + total + " (" + percentage + "%)");
                    }

                    @Override
                    public void onSuccess(List<OssUploadUtil.UploadResult> results) {
                        List<String> objectKeys = new ArrayList<>();
                        for (OssUploadUtil.UploadResult result : results) {
                            objectKeys.add(result.objectKey);
                        }

                        Log.d(TAG, "✅ 批量上传成功: " + objectKeys.size() + " 张");

                        _batchUploadState.postValue(
                                ApiResponse.success(objectKeys, "上传成功")
                        );
                    }

                    @Override
                    public void onFailure(Exception e) {
                        String message = e != null ? e.getMessage() : "上传失败";
                        Log.e(TAG, "❌ 批量上传失败: " + message);
                        _batchUploadState.postValue(ApiResponse.error(message));
                    }
                }
        );
    }

    /**
     * 批量上传图片 (便捷方法 - 从单个 URI 开始)
     */
    public void uploadBatchImages(Uri imageUri, String userId, OssUploadUtil.UploadScene scene) {
        List<Uri> uris = new ArrayList<>();
        uris.add(imageUri);
        uploadBatchImages(uris, userId, scene);
    }

    // ==================== 状态管理 ====================

    /**
     * 重置单图上传状态
     */
    public void resetSingleUploadState() {
        _singleUploadState.setValue(ApiResponse.idle());
        _uploadProgress.setValue(new UploadProgress(0, 0, 0));
    }

    /**
     * 重置批量上传状态
     */
    public void resetBatchUploadState() {
        _batchUploadState.setValue(ApiResponse.idle());
        _uploadProgress.setValue(new UploadProgress(0, 0, 0));
    }

    /**
     * 重置所有状态
     */
    public void resetAllStates() {
        resetSingleUploadState();
        resetBatchUploadState();
    }

    // ==================== 回调接口 ====================

    /**
     * 单图上传回调接口
     */
    public interface SingleUploadCallback {
        void onProgress(int percentage);
        void onSuccess(String objectKey);
        void onFailure(String errorMessage);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "🧹 ViewModel cleared");
    }
}