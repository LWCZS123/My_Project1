package com.example.my_project1.ui.viewmodel.billvm;

/**
 * PagingState - FooterAdapter 展示用的分页状态
 */
public enum PagingState {
    /** 空闲，列表末尾不展示任何 Footer */
    IDLE,
    /** 正在加载下一页 */
    LOADING,
    /** 已加载全部，无更多数据 */
    NO_MORE,
    /** 加载失败，展示"重试"按钮 */
    ERROR
}