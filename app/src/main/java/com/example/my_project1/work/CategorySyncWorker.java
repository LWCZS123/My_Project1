package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.utils.AppExecutors;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

/**
 * 分类同步 Worker
 * 处理一级分类的上传、更新和删除，并级联触发子分类同步
 */
public class CategorySyncWorker extends Worker {

    private static final String TAG = "CategorySyncWorker";
    private final AppDatabase db;
    private final BmobApiImpl api;

    public CategorySyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobApiImpl();
    }

    @NonNull
    @Override
    public Result doWork() {
        List<Category> pendingList = db.categoryDao().getPendingSyncCategories();
        if (pendingList == null || pendingList.isEmpty()) {
            return Result.success();
        }

        for (Category category : pendingList) {
            try {
                boolean success;
                int state = category.getSyncState();

                if (state == SyncState.TO_CREATE.getValue()) {
                    success = uploadCategorySync(category);
                } else if (state == SyncState.TO_UPDATE.getValue()) {
                    if (category.getCloudId() == null || category.getCloudId().isEmpty()) {
                        success = uploadCategorySync(category);
                    } else {
                        success = updateCategorySync(category);
                    }
                } else if (state == SyncState.TO_DELETE.getValue()) {
                    success = deleteCategorySync(category);
                } else {
                    continue;
                }

                if (!success) {
                    return Result.retry();
                }

                category.setSyncState(SyncState.SYNCED.getValue());
                AppExecutors.get().diskIO().execute(() -> db.categoryDao().update(category));

            } catch (Exception e) {
                Log.e(TAG, "Category sync error: " + e.getMessage());
                return Result.retry();
            }
        }

        // 触发子分类同步
        SubCategorySyncWorker.enqueue(getApplicationContext());

        return Result.success();
    }

    private boolean uploadCategorySync(Category category) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.uploadCategory(category, new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null && objectId != null) {
                    category.setCloudId(objectId);
                    category.setSyncState(SyncState.SYNCED.getValue());

                    AppExecutors.get().diskIO().execute(() -> {
                        db.categoryDao().updateCloudIdById(
                                category.getId(), objectId, SyncState.SYNCED.getValue());
                        propagateParentCloudIdToChildren(category.getId(), objectId);
                    });
                    success.set(true);
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    private void propagateParentCloudIdToChildren(long parentLocalId, String parentCloudId) {
        List<SubCategory> children = db.subCategoryDao().getByParentCategoryId(parentLocalId);
        if (children == null || children.isEmpty()) return;

        for (SubCategory child : children) {
            if (parentCloudId.equals(child.getParentCloudId())) continue;
            child.setParentCloudId(parentCloudId);
            db.subCategoryDao().update(child);
        }
    }

    private boolean updateCategorySync(Category category) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.updateCategory(category, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    success.set(true);
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    private boolean deleteCategorySync(Category category) {
        if (category.getCloudId() == null || category.getCloudId().isEmpty()) {
            AppExecutors.get().diskIO().execute(() -> db.categoryDao().deleteById(category.getId()));
            return true;
        }

        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.deleteCategory(category.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    AppExecutors.get().diskIO().execute(() -> db.categoryDao().deleteById(category.getId()));
                    success.set(true);
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        if (success.get()) {
            SubCategorySyncWorker.enqueue(getApplicationContext());
        }
        return success.get();
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CategorySyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }
}
