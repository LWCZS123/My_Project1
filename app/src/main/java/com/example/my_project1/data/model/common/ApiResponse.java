package com.example.my_project1.data.model.common;

public class ApiResponse<T> {

    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR,
        EMPTY
    }

    public final Status status;
    public final T data;
    public final String message;

    private ApiResponse(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    // ==================== 工厂方法 ====================

    public static <T> ApiResponse<T> idle() {
        return new ApiResponse<>(Status.IDLE, null, null);
    }

    public static <T> ApiResponse<T> loading() {
        return new ApiResponse<>(Status.LOADING, null, null);
    }

    public static <T> ApiResponse<T> loading(String message) {
        return new ApiResponse<>(Status.LOADING, null, message);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Status.SUCCESS, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(Status.SUCCESS, data, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(Status.ERROR, null, message);
    }

    public static <T> ApiResponse<T> error(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "未知错误";
        return new ApiResponse<>(Status.ERROR, null, message);
    }

    public static <T> ApiResponse<T> empty() {
        return new ApiResponse<>(Status.EMPTY, null, null);
    }

    public static <T> ApiResponse<T> empty(String message) {
        return new ApiResponse<>(Status.EMPTY, null, message);
    }

    // ==================== ⭐ 修复点：新增 Getter ====================

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

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

    // ==================== 工具方法 ====================

    public T getDataOrDefault(T defaultValue) {
        return data != null ? data : defaultValue;
    }

    public String getErrorMessage(String defaultMessage) {
        return message != null ? message : defaultMessage;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "status=" + status +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }

    public interface Callback<T> {
        void onComplete(ApiResponse<T> response);
    }
}