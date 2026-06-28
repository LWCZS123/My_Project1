package com.example.my_project1.data.model;

/**
 * 同步状态枚举（用于标记本地记录与云端同步的状态）
 *
 * 说明：
 *  - SYNCED: 本地记录已与云端一致（无需上传）
 *  - TO_CREATE: 本地新建，需要上传到云端（创建）
 *  - TO_UPDATE: 本地被修改，需要更新云端
 *  - TO_DELETE: 本地被删除（或标记删除），需要在云端删除后再本地清理
 *  - SYNC_FAILED: 上一次同步失败，等待重试（WorkManager/手动触发）
 */
public enum SyncState {
    SYNCED(0),
    TO_CREATE(1),
    TO_UPDATE(2),
    TO_DELETE(3),
    SYNC_FAILED(4);

    private final int value;

    SyncState(int value) { this.value = value; }

    public int getValue() { return value; }

    public static SyncState fromInt(int v) {
        for (SyncState s : values()) {
            if (s.value == v) return s;
        }
        return SYNC_FAILED;
    }
}
