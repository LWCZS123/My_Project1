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
 * 二级分类同步 Worker
 * 处理二级分类的上传、更新和删除
 */
public class SubCategorySyncWorker extends Worker {

    private static final String TAG = "SubCategorySyncWorker";
    private final AppDatabase db;
    private final BmobApiImpl api;

    public SubCategorySyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        api = new BmobApiImpl();
    }

    @NonNull
    @Override
    public Result doWork() {
        List<SubCategory> pendingList = db.subCategoryDao().getPendingSyncSubCategories();
        if (pendingList == null || pendingList.isEmpty()) {
            return Result.success();
        }

        boolean needRetry = false;

        for (SubCategory sub : pendingList) {
            try {
                int state = sub.getSyncState();
                boolean success;

                if (state == SyncState.TO_CREATE.getValue()) {
                    Result guardResult = guardParentCloudId(sub);
                    if (guardResult != null) {
                        needRetry = true;
                        continue;
                    }
                    success = uploadSubCategorySync(sub);
                } else if (state == SyncState.TO_UPDATE.getValue()) {
                    if (sub.getCloudId() == null || sub.getCloudId().isEmpty()) {
                        Result guardResult = guardParentCloudId(sub);
                        if (guardResult != null) {
                            needRetry = true;
                            continue;
                        }
                        success = uploadSubCategorySync(sub);
                    } else {
                        success = updateSubCategorySync(sub);
                    }
                } else if (state == SyncState.TO_DELETE.getValue()) {
                    success = deleteSubCategorySync(sub);
                } else {
                    continue;
                }

                if (!success) {
                    return Result.retry();
                }

                sub.setSyncState(SyncState.SYNCED.getValue());
                AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().update(sub));

            } catch (Exception e) {
                Log.e(TAG, "SubCategory sync error: " + e.getMessage());
                return Result.retry();
            }
        }

        return needRetry ? Result.retry() : Result.success();
    }

    private Result guardParentCloudId(SubCategory sub) {
        if (sub.getParentCloudId() != null && !sub.getParentCloudId().isEmpty()) {
            return null;
        }

        if (sub.getParentCategoryId() > 0) {
            Category parent = db.categoryDao().getCategoryById(sub.getParentCategoryId());
            if (parent != null && parent.getCloudId() != null && !parent.getCloudId().isEmpty()) {
                sub.setParentCloudId(parent.getCloudId());
                AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().update(sub));
                return null;
            }
        }
        return Result.retry();
    }

    private boolean uploadSubCategorySync(SubCategory sub) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.uploadSubCategory(sub, new SaveListener<String>() {
            @Override
            public void done(String objectId, BmobException e) {
                if (e == null && objectId != null) {
                    sub.setCloudId(objectId);
                    sub.setSyncState(SyncState.SYNCED.getValue());

                    AppExecutors.get().diskIO().execute(() -> 
                        db.subCategoryDao().updateSubCloudIdById(sub.getId(), objectId, SyncState.SYNCED.getValue())
                    );
                    success.set(true);
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
        return success.get();
    }

    private boolean updateSubCategorySync(SubCategory sub) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.updateSubCategory(sub, new UpdateListener() {
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

    private boolean deleteSubCategorySync(SubCategory sub) {
        if (sub.getCloudId() == null || sub.getCloudId().isEmpty()) {
            AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().deleteSubById(sub.getId()));
            return true;
        }

        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        api.deleteSubCategory(sub.getCloudId(), new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    AppExecutors.get().diskIO().execute(() -> db.subCategoryDao().deleteSubById(sub.getId()));
                    success.set(true);
                }
                latch.countDown();
            }
        });

        awaitLatch(latch);
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
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SubCategorySyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }
}
