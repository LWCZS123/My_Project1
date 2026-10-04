package com.example.my_project1.ui.viewmodel.billvm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.my_project1.data.model.calendar.DailyStat;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账单数据快照持久化管理器
 * -------------------------------------------------------
 * 职责：
 * 1. 负责从 SharedPreferences 读写首页 Header 统计、日历统计以及账单预加载图等快照数据。
 * 2. 避免主线程阻塞，写操作全部在 AppExecutors diskIO 异步线程池执行。
 */
public class BillSnapshotManager {

    private static final String TAG = "BillSnapshotManager";
    private static final String SP_NAME = "bill_snapshot";
    private static final String KEY_HEADER_SNAPSHOT = "header_data";
    private static final String KEY_BILL_ITEMS_SNAPSHOT = "bill_items";
    private static final String KEY_CALENDAR_SNAPSHOT = "calendar_stats";

    private final Context context;
    private final Gson gson = new Gson();

    public BillSnapshotManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private String snapshotKey(String baseKey, String userId) {
        return userId == null ? baseKey : baseKey + "_" + userId;
    }

    /**
     * 加载 Header 快照
     */
    public HeaderUiModel loadHeaderSnapshot(String userId) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String headerJson = sp.getString(snapshotKey(KEY_HEADER_SNAPSHOT, userId),
                sp.getString(KEY_HEADER_SNAPSHOT, null));
        if (headerJson != null) {
            try {
                return gson.fromJson(headerJson, HeaderUiModel.class);
            } catch (Exception e) {
                Log.e(TAG, "解析 Header 快照失败", e);
            }
        }
        return null;
    }

    /**
     * 加载日历统计快照
     */
    public Map<String, DailyStat> loadCalendarSnapshot(String userId) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String calendarJson = sp.getString(snapshotKey(KEY_CALENDAR_SNAPSHOT, userId),
                sp.getString(KEY_CALENDAR_SNAPSHOT, null));
        if (calendarJson != null) {
            try {
                Type type = new TypeToken<Map<String, DailyStat>>(){}.getType();
                return gson.fromJson(calendarJson, type);
            } catch (Exception e) {
                Log.e(TAG, "解析日历快照失败", e);
            }
        }
        return null;
    }

    /**
     * 加载账单分类图标并提前预加载图片
     */
    public void preloadSnapshotIcons(String userId) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String billItemsJson = sp.getString(snapshotKey(KEY_BILL_ITEMS_SNAPSHOT, userId),
                sp.getString(KEY_BILL_ITEMS_SNAPSHOT, null));
        if (billItemsJson != null) {
            try {
                Type type = new TypeToken<List<HomeBillUiModel>>(){}.getType();
                List<HomeBillUiModel> billItems = gson.fromJson(billItemsJson, type);
                if (billItems != null) {
                    Set<String> urls = new LinkedHashSet<>();
                    for (HomeBillUiModel item : billItems) {
                        if (item != null && item.billItem != null && item.billItem.categoryIconUrl != null) {
                            urls.add(item.billItem.categoryIconUrl);
                            if (urls.size() == 12) break;
                        }
                    }
                    ImageLoaderUtils.preloadHomeBillCategoryIcons(context, new ArrayList<>(urls));
                }
            } catch (Exception e) {
                Log.e(TAG, "预加载图标失败", e);
            }
        }
    }

    /**
     * 异步持久化保存 Header 与日历快照
     */
    public void saveSnapshot(String userId, HeaderUiModel header, Map<String, DailyStat> calendarStats,
                             List<HomeBillUiModel> billItems, AppExecutors executors) {
        if (userId == null) return;

        executors.diskIO().execute(() -> {
            try {
                SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
                sp.edit()
                        .putString(snapshotKey(KEY_HEADER_SNAPSHOT, userId), gson.toJson(header))
                        .putString(snapshotKey(KEY_CALENDAR_SNAPSHOT, userId), gson.toJson(calendarStats))
                        .putString(snapshotKey(KEY_BILL_ITEMS_SNAPSHOT, userId), gson.toJson(billItems))
                        .apply();
                Log.d(TAG, "快照持久化成功");
            } catch (Exception e) {
                Log.e(TAG, "保存快照失败", e);
            }
        });
    }
}
