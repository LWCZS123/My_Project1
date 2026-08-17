package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.wish.Wish;

import java.util.List;

@Dao
public interface WishDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertWish(Wish wish);

    @Update
    int updateWish(Wish wish);

    @Delete
    int deleteWish(Wish wish);

    @Query("SELECT * FROM wishes WHERE user_id = :userId ORDER BY created_at DESC")
    LiveData<List<Wish>> getAllWishesByUser(String userId);

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    LiveData<Wish> getWishById(long id);

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    Wish getWishByIdSync(long id);
}
