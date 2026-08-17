package com.example.my_project1.work;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WishSyncWorker extends Worker {

    public WishSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        return Result.success();
    }

    public static void enqueue(Context context) {
        // 同步逻辑占位
    }
}
