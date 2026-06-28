package com.example.my_project1.data.remote.model.cloudwish;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.BmobPointerUtil;

import java.util.Date;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

public class BmobWishApiImpl {

    private static final String TAG = "BmobWishApiImpl";

    private final Context context;
    private final AppDatabase db;

    public BmobWishApiImpl(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    // ======================== 同步上传 ========================

    public boolean uploadWishSync(Wish local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) return false;

            CloudWish cloud = CloudWish.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));

            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                String id = cloud.saveSync();
                local.setObjectId(id);
            } else {
                cloud.updateSync(local.getObjectId());
            }

            local.setSyncState(SyncState.SYNCED);
            local.setUpdatedAt(new Date());

            AppExecutors.get().diskIO().execute(() ->
                    db.wishDao().updateWish(local)
            );

            return true;

        } catch (Exception e) {
            Log.e(TAG, "uploadWishSync error", e);
            return false;
        }
    }

    public boolean uploadRecordSync(WishRecord local) {
        try {
            String userId = getCurrentUserId();
            if (userId == null) return false;

            CloudWishRecord cloud = CloudWishRecord.fromLocal(local);
            cloud.setUser(BmobPointerUtil.user(userId));

            if (local.getObjectId() == null || local.getObjectId().isEmpty()) {
                String id = cloud.saveSync();
                local.setObjectId(id);
            } else {
                cloud.updateSync(local.getObjectId());
            }

            local.setSyncState(SyncState.SYNCED);

            AppExecutors.get().diskIO().execute(() ->
                    db.wishDao().updateRecord(local)
            );

            return true;

        } catch (Exception e) {
            Log.e(TAG, "uploadRecordSync error", e);
            return false;
        }
    }

    // ======================== 删除 ========================

    public void deleteWish(String objectId, UpdateListener listener) {
        if (objectId == null) {
            listener.done(new BmobException(902, "objectId 为空"));
            return;
        }

        CloudWish cloud = new CloudWish();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    public void deleteRecord(String objectId, UpdateListener listener) {
        if (objectId == null) {
            listener.done(new BmobException(902, "objectId 为空"));
            return;
        }

        CloudWishRecord cloud = new CloudWishRecord();
        cloud.setObjectId(objectId);
        cloud.delete(listener);
    }

    // ======================== 工具 ========================

    private String getCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        return user != null ? user.getObjectId() : null;
    }
}