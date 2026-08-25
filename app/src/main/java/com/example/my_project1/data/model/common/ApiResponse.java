package com.example.my_project1.data.model.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 通用 API 响应封装类
 * <p>
 * 用于统一封装 Repository / ViewModel 层的操作结果，
 * 支持 IDLE / LOADING / SUCCESS / ERROR / EMPTY 五种状态。
 */
public class ApiResponse<T> {

    // ==================== 状态枚举 ====================

    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR,
        EMPTY
    }

    // ==================== 字段 ====================

    @NonNull
    public final Status status;

    @Nullable
    public final T data;

    @Nullable
    public final String message;

    // ==================== 构造器 ====================

    private ApiResponse(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    // ==================== 工厂方法 ====================

    @NonNull
    public static <T> ApiResponse<T> idle() {
        return new ApiResponse<>(Status.IDLE, null, null);
    }

    @NonNull
    public static <T> ApiResponse<T> loading() {
        return new ApiResponse<>(Status.LOADING, null, null);
    }

    @NonNull
    public static <T> ApiResponse<T> loading(@Nullable String message) {
        return new ApiResponse<>(Status.LOADING, null, message);
    }

    @NonNull
    public static <T> ApiResponse<T> success(@Nullable T data) {
        return new ApiResponse<>(Status.SUCCESS, data, null);
    }

    @NonNull
    public static <T> ApiResponse<T> success(@Nullable T data, @Nullable String message) {
        return new ApiResponse<>(Status.SUCCESS, data, message);
    }

    /**
     * 仅携带消息的成功响应（data 为 null）
     * 适用于 UI 层只需要提示文字、不需要数据的场景
     */
    @NonNull
    public static <T> ApiResponse<T> successMessage(@NonNull String message) {
        return new ApiResponse<>(Status.SUCCESS, null, message);
    }

    @NonNull
    public static <T> ApiResponse<T> error(@NonNull String message) {
        return new ApiResponse<>(Status.ERROR, null, message);
    }

    @NonNull
    public static <T> ApiResponse<T> error(@NonNull Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
        return new ApiResponse<>(Status.ERROR, null, msg);
    }

    @NonNull
    public static <T> ApiResponse<T> empty() {
        return new ApiResponse<>(Status.EMPTY, null, null);
    }

    @NonNull
    public static <T> ApiResponse<T> empty(@Nullable String message) {
        return new ApiResponse<>(Status.EMPTY, null, message);
    }

    // ==================== Getter ====================

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    // ==================== 状态判断 ====================

    public boolean isIdle() {
        return status == Status.IDLE;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isEmpty() {
        return status == Status.EMPTY;
    }

    public boolean hasData() {
        return status == Status.SUCCESS && data != null;
    }

    /**
     * 成功且携带了非空消息
     */
    public boolean isSuccessWithMessage() {
        return status == Status.SUCCESS && message != null && !message.isEmpty();
    }

    /**
     * 是否处于终态（SUCCESS / ERROR / EMPTY），即操作已完成
     */
    public boolean isTerminal() {
        return status == Status.SUCCESS || status == Status.ERROR || status == Status.EMPTY;
    }

    // ==================== ⭐ 泛型映射（核心优化） ====================

    /**
     * 将当前响应映射为另一种泛型的"纯消息"响应
     * <p>
     * 保留原始 status 和 message，丢弃 data。
     * 典型用途：Repository 返回 ApiResponse&lt;Long&gt;，
     * ViewModel 需要转换为 ApiResponse&lt;String&gt; 给 UI 观察。
     * <pre>
     * repository.insertWish(wish, response ->
     *     _operationState.setValue(response.mapMessage("保存成功"))
     * );
     * </pre>
     */
    @NonNull
    public <R> ApiResponse<R> mapMessage(@NonNull String successMsg) {
        switch (status) {
            case SUCCESS:
                return ApiResponse.success(null, successMsg);
            case ERROR:
                return ApiResponse.error(message != null ? message : "操作失败");
            case LOADING:
                return ApiResponse.loading(message);
            case EMPTY:
                return ApiResponse.empty(message);
            default:
                return ApiResponse.idle();
        }
    }

    /**
     * 使用默认成功消息的 mapMessage 快捷方式
     */
    @NonNull
    public <R> ApiResponse<R> mapMessage() {
        return mapMessage(message != null ? message : "操作成功");
    }

    /**
     * 对 data 进行类型转换，保留 status 和 message
     * <pre>
     * ApiResponse&lt;User&gt; userResp = ...;
     * ApiResponse&lt;String&gt; nameResp = userResp.mapData(user -> user.getName());
     * </pre>
     */
    @NonNull
    public <R> ApiResponse<R> mapData(@NonNull DataMapper<T, R> mapper) {
        if (status == Status.SUCCESS && data != null) {
            R mapped = mapper.map(data);
            return ApiResponse.success(mapped, message);
        }
        // 非 SUCCESS 或 data 为 null 时，只保留状态和消息
        return new ApiResponse<>(status, null, message);
    }

    /**
     * 数据映射器函数式接口
     */
    public interface DataMapper<F, TO> {
        TO map(F from);
    }

    // ==================== 工具方法 ====================

    @NonNull
    public T getDataOrDefault(@NonNull T defaultValue) {
        return data != null ? data : defaultValue;
    }

    @NonNull
    public String getErrorMessage(@NonNull String defaultMessage) {
        return message != null ? message : defaultMessage;
    }

    /**
     * 根据状态执行不同逻辑（替代大量 if-else）
     */
    public void handle(
            @Nullable Runnable onLoading,
            @Nullable SuccessHandler<T> onSuccess,
            @Nullable ErrorHandler onError,
            @Nullable Runnable onIdle
    ) {
        switch (status) {
            case LOADING:
                if (onLoading != null) onLoading.run();
                break;
            case SUCCESS:
                if (onSuccess != null) onSuccess.onSuccess(data, message);
                break;
            case ERROR:
                if (onError != null) onError.onError(message);
                break;
            case IDLE:
                if (onIdle != null) onIdle.run();
                break;
            case EMPTY:
                // EMPTY 视为一种特殊的 SUCCESS
                if (onSuccess != null) onSuccess.onSuccess(null, message);
                break;
        }
    }

    public interface SuccessHandler<T> {
        void onSuccess(@Nullable T data, @Nullable String message);
    }

    public interface ErrorHandler {
        void onError(@Nullable String message);
    }

    // ==================== Object 方法重写 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiResponse)) return false;
        ApiResponse<?> that = (ApiResponse<?>) o;
        return status == that.status
                && Objects.equals(data, that.data)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, data, message);
    }

    @NonNull
    @Override
    public String toString() {
        return "ApiResponse{" +
                "status=" + status +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }

    // ==================== 回调接口 ====================

    public interface Callback<T> {
        void onComplete(@NonNull ApiResponse<T> response);
    }
}