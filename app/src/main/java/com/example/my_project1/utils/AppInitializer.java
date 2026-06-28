package com.example.my_project1.utils;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.provider.SystemPresetDataProvider;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.work.CategoryDownloadWorker;

import java.util.List;

/**
 * AppInitializer
 * -------------------------------------------
 * 用于在应用启动或新用户注册后初始化系统预设分类。
 *
 * 逻辑：
 *  1. 检查云端是否已有分类；
 *     - 有 → 下载同步到本地。
 *     - 无 → 上传系统预设分类，然后再下载同步。
 * -------------------------------------------
 */
public class AppInitializer {
    private static final String TAG = "AppInitializer";

    public interface OnInitCompleteListener {
        void onInitComplete(boolean success);
    }

    /**
     * 初始化系统分类（首次启动或注册后调用）
     */
    public static void initSystemCategories(Context context, String userId, OnInitCompleteListener listener) {
        AppExecutors.get().execute(() -> {
            boolean success = false;
            try {
                BmobApiImpl api = new BmobApiImpl(context);

                // ✅ 核心：检查云端是否已有分类
                List<CloudCategory> cloudCategories = api.getAllCategoriesSync(userId);

                if (cloudCategories != null && !cloudCategories.isEmpty()) {
                    // 📥 云端有数据 → 下载到本地
                    Log.i(TAG, "☁️ 云端已有 " + cloudCategories.size() + " 个分类，开始同步下载");
                    CategoryDownloadWorker.enqueueSync(context, userId);
                    success = true;
                } else {
                    // 📤 云端无数据 → 首次初始化，上传系统预设
                    Log.i(TAG, "☁️ 云端无数据，开始上传系统预设分类");
                    success = uploadSystemPresets(context, userId, api);

                    if (success) {
                        // 上传完成后再下载一次，确保本地与云端一致
                        CategoryDownloadWorker.enqueueSync(context, userId);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "初始化失败: " + e.getMessage(), e);
            }

            notifyListener(listener, success);
        });
    }

    /**
     * 上传系统预设分类到云端（仅首次使用）
     */
    private static boolean uploadSystemPresets(Context context, String userId, BmobApiImpl api) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);

            // 获取系统预设数据
            List<Category> expensePresets = SystemPresetDataProvider.getExpensePresets(context, userId);
            List<Category> incomePresets = SystemPresetDataProvider.getIncomePresets(context, userId);

            // 插入一级分类（支出 / 收入）
            long[] expenseIds = db.categoryDao().insertCategories(expensePresets);
            long[] incomeIds = db.categoryDao().insertCategories(incomePresets);

            // 设置父分类 ID，并插入子分类
            for (int i = 0; i < expenseIds.length; i++) {
                Category cat = expensePresets.get(i);
                cat.setId(expenseIds[i]);

                if (cat.getSubCategories() != null) {
                    for (SubCategory sub : cat.getSubCategories()) {
                        sub.setParentCategoryId(expenseIds[i]);
                    }

                    long[] subIds = db.subCategoryDao().insertSubCategories(cat.getSubCategories());
                    for (int j = 0; j < subIds.length && j < cat.getSubCategories().size(); j++) {
                        cat.getSubCategories().get(j).setId(subIds[j]);
                    }
                }
            }

            for (int i = 0; i < incomeIds.length; i++) {
                Category cat = incomePresets.get(i);
                cat.setId(incomeIds[i]);

                if (cat.getSubCategories() != null) {
                    for (SubCategory sub : cat.getSubCategories()) {
                        sub.setParentCategoryId(incomeIds[i]);
                    }

                    long[] subIds = db.subCategoryDao().insertSubCategories(cat.getSubCategories());
                    for (int j = 0; j < subIds.length && j < cat.getSubCategories().size(); j++) {
                        cat.getSubCategories().get(j).setId(subIds[j]);
                    }
                }
            }

            // 上传到云端
            for (Category c : expensePresets) {
                boolean uploaded = api.uploadCategorySync(c);
                if (!uploaded) {
                    Log.e(TAG, "上传分类失败: " + c.getName());
                    return false;
                }
            }

            for (Category c : incomePresets) {
                boolean uploaded = api.uploadCategorySync(c);
                if (!uploaded) {
                    Log.e(TAG, "上传分类失败: " + c.getName());
                    return false;
                }
            }

            Log.i(TAG, "✅ 系统预设已成功上传至云端。");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "上传系统预设失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 可选：无回调版本
     */
    public static void initSystemCategories(Context context, String userId) {
        initSystemCategories(context, userId, null);
    }

    /**
     * 通知回调
     */
    private static void notifyListener(OnInitCompleteListener listener, boolean success) {
        if (listener != null) {
            AppExecutors.get().mainThread().execute(() -> listener.onInitComplete(success));
        }
    }
}
