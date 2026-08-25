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

    @Query("SELECT * FROM wishes WHERE user_id = :userId AND sync_state != 'TO_DELETE' ORDER BY created_at DESC")
    LiveData<List<Wish>> getAllWishesByUser(String userId);

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    LiveData<Wish> getWishById(long id);

    @Query("SELECT * FROM wishes WHERE id = :id LIMIT 1")
    Wish getWishByIdSync(long id);

    /**  查询需要同步的愿望 (TO_CREATE, TO_UPDATE) */
    @Query("SELECT * FROM wishes WHERE sync_state = 'TO_CREATE' OR sync_state = 'TO_UPDATE'")
    List<Wish> getPendingSyncWishes();

    /** 🔴 查询需要从云端删除的愿望 */
    @Query("SELECT * FROM wishes WHERE sync_state = 'TO_DELETE'")
    List<Wish> getToDeleteWishes();

    /** 🔴 根据 objectId 查询愿望 */
    @Query("SELECT * FROM wishes WHERE object_id = :objectId LIMIT 1")
    Wish getWishByObjectId(String objectId);
}
