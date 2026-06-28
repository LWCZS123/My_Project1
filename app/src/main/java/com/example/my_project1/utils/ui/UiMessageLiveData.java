package com.example.my_project1.utils.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * UiMessageLiveData
 * --------------------------------------
 * 用于在 ViewModel 中向 UI 层发送一次性消息事件，
 * 如 Toast、SnackBar、Dialog 提示等。
 */
public class UiMessageLiveData extends MutableLiveData<String>{

    private final MutableLiveData<Event<String>> messageLiveData = new MutableLiveData<>();

    /** 让 UI 层监听消息事件 */
    public LiveData<Event<String>> getMessage() {
        return messageLiveData;
    }

    /** 在主线程立即发送消息 */
    public void setMessage(String message) {
        messageLiveData.setValue(new Event<>(message));
    }

    /** 在后台线程发送消息 */
    public void postMessage(String message) {
        messageLiveData.postValue(new Event<>(message));
    }
}
