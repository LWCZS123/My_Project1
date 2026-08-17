package com.example.my_project1.ui.viewmodel.wish;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.repository.wish.WishRepository;

import java.util.List;

public class WishViewModel extends AndroidViewModel {

    private final WishRepository repository;
    private String currentUserId; // 可以在这里初始化或从 Bmob 获取

    public WishViewModel(Application application) {
        super(application);
        this.repository = new WishRepository(application);
        // 示例：初始化用户 ID
        // BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        // if (user != null) currentUserId = user.getObjectId();
    }

    public LiveData<List<Wish>> getAllWishes() {
        return repository.getAllWishesByUser(currentUserId);
    }

    public LiveData<Wish> getWishById(long id) {
        return repository.getWishById(id);
    }

    public void insertWish(Wish wish) {
        if (wish != null) {
            wish.setUserId(currentUserId);
            repository.insertWish(wish);
        }
    }

    public void updateWish(Wish wish) {
        repository.updateWish(wish);
    }

    public void deleteWish(Wish wish) {
        repository.deleteWish(wish);
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
}
