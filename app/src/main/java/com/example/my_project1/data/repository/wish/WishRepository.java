package com.example.my_project1.data.repository.wish;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.utils.AppExecutors;

import java.util.Date;
import java.util.List;

public class WishRepository {

    private final WishDao wishDao;
    private final AppExecutors executors;

    public WishRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.wishDao = db.wishDao();
        this.executors = AppExecutors.get();
    }

    public void insertWish(Wish wish, ApiResponse.Callback<Long> wishCallback) {
        executors.diskIO().execute(() -> {
            try {
                wish.setSyncState(SyncState.TO_CREATE);
                Date now = new Date();
                if (wish.getCreatedAt() == null) {
                    wish.setCreatedAt(now);
                }
                wish.setUpdatedAt(now);
                long wishId = wishDao.insertWish(wish);
                if (wishCallback == null) return;
                if (wishId > 0) {
                    // 写入本地 ID
                    wish.setId(wishId);
                        executors.mainThread().execute(() ->
                                wishCallback.onComplete(
                                        ApiResponse.success(wishId)
                                )
                        );
                } else {
                        executors.mainThread().execute(() ->
                                wishCallback.onComplete(
                                        ApiResponse.error("本地数据库保存失败")
                                )
                        );
                    }
            } catch (Exception e) {
                executors.mainThread().execute(() ->
                        wishCallback.onComplete(
                                ApiResponse.error(e)
                        )
                );
            }
        });
    }

    public void updateWish(Wish wish,ApiResponse.Callback<Integer> wishCallback) {
        executors.diskIO().execute(() -> {
            try{
                Date date = new Date();
                // 如果愿望还没有同步到云端，继续保持 TO_CREATE
                if (!SyncState.TO_CREATE.equals(wish.getSyncState())) {
                    wish.setSyncState(SyncState.TO_UPDATE);
                }
                wish.setUpdatedAt(date);
                int rows = wishDao.updateWish(wish);
                if (wishCallback == null) return;
                if (rows > 0){
                        executors.mainThread().execute(()->{
                            wishCallback.onComplete(ApiResponse
                                    .success(rows,"愿望更新成功"));
                        });
                }else{
                        executors.mainThread().execute(()->{
                            wishCallback.onComplete(ApiResponse.error("愿望更新失败"));
                        });
                    }
            } catch (Exception e) {
                   executors.mainThread().execute(()->{
                       wishCallback.onComplete(ApiResponse.error(e));
                   });
            }
        });
    }

    public void deleteWish(Wish wish,ApiResponse.Callback<Integer>  wishCallback) {
        executors.diskIO().execute(()-> {
                try {
                    Date now = new Date();
                    wish.setSyncState(SyncState.TO_DELETE);
                    wish.setUpdatedAt(now);
                    //实现软删除，将状态标记为待删除
                    int rows = wishDao.updateWish(wish);
                    if (wishCallback == null) return;


                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
        });
    }

    public LiveData<List<Wish>> getAllWishesByUser(String userId) {
        return wishDao.getAllWishesByUser(userId);
    }

    public LiveData<Wish> getWishById(long id) {
        return wishDao.getWishById(id);
    }
}
