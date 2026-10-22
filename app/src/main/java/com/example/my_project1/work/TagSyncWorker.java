package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.repository.tag.TagRepository;

import cn.bmob.v3.BmobUser;

public class TagSyncWorker extends Worker {

    private static final String TAG = "TagSyncWorker";

    public TagSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static Constraints getDefaultConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TagSyncWorker.class)
                .setConstraints(getDefaultConstraints())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork("TagSync", ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Executing TagSyncWorker background sync...");
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            String userId = user.getObjectId();
            TagRepository repository = new TagRepository(getApplicationContext());
            repository.uploadPendingChanges(userId);
        }
        return Result.success();
    }
}
