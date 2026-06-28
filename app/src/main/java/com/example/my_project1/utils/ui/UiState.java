package com.example.my_project1.utils.ui;

/**
 * UiState<T>
 * --------------------------------------
 * 通用状态包装类，用于表示 Loading、Success、Error 三种 UI 状态。
 */
public class UiState<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;
    public final String message;

    private UiState(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }


    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null, null);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(Status.SUCCESS, data, null);
    }

    public static <T> UiState<T> error(String message) {
        return new UiState<>(Status.ERROR, null, message);
    }
    public boolean isLoading() { return status == Status.LOADING; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError()   { return status == Status.ERROR; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "UiState{" +
                "status=" + status +
                ", data=" + data +
                ", message='" + message + '\'' +
                '}';
    }
}
