package com.example.my_project1.data.repository.wish;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.utils.AppExecutors;

import java.util.List;

public class WishRepository {

    private final WishDao wishDao;
    private final AppExecutors executors;

    public WishRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.wishDao = db.wishDao();
        this.executors = AppExecutors.get();
    }

    public void insertWish(Wish wish) {
        executors.diskIO().execute(() -> wishDao.insertWish(wish));
    }

    public void updateWish(Wish wish) {
        executors.diskIO().execute(() -> wishDao.updateWish(wish));
    }

    public void deleteWish(Wish wish) {
        executors.diskIO().execute(() -> wishDao.deleteWish(wish));
    }

    public LiveData<List<Wish>> getAllWishesByUser(String userId) {
        return wishDao.getAllWishesByUser(userId);
    }

    public LiveData<Wish> getWishById(long id) {
        return wishDao.getWishById(id);
    }
}
