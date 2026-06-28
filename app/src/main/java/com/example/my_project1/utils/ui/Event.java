package com.example.my_project1.utils.ui;

/**
 * Event<T>
 * --------------------------------------
 * 用于包装一次性事件（如 Toast、导航等），
 * 避免因 LiveData 重放造成的重复触发。
 */
public class Event<T> {
    private final T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        this.content = content;
    }

    /**
     * 获取内容，如果已被处理则返回 null。
     */
    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    /**
     * 获取内容，即使已经被处理。
     */
    public T peekContent() {
        return content;
    }
}
