package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.bill.SearchHistory;

import java.util.List;

/**
 * SearchHistoryDao - 搜索历史数据访问层 (优化版)
 * -------------------------------------------------------
 * 搜索历史的数据库操作
 *
 * ✅ 优化点:
 * 1. 支持更新已存在的记录(相同关键词)
 * 2. 自动按时间倒序排列(最新的在前)
 */
@Dao
public interface SearchHistoryDao {

    /**
     * 插入搜索历史
     * 使用REPLACE策略: 如果关键词已存在会替换(需要unique索引配合)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SearchHistory searchHistory);

    /**
     * 更新搜索历史
     */
    @Update
    int update(SearchHistory searchHistory);

    /**
     * 删除单条搜索历史
     */
    @Delete
    int delete(SearchHistory searchHistory);

    /**
     * 根据ID删除搜索历史
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    int deleteById(long id);

    /**
     * 清空某用户的所有搜索历史
     */
    @Query("DELETE FROM search_history WHERE user_id = :userId")
    int clearHistory(String userId);

    /**
     * 获取某用户的搜索历史(按时间倒序,最多10条)
     * 按search_time倒序排列,确保最新的在最前面
     */
    @Query("SELECT * FROM search_history WHERE user_id = :userId ORDER BY search_time DESC LIMIT 10")
    LiveData<List<SearchHistory>> getSearchHistory(String userId);

    /**
     * 关键方法: 根据用户ID和关键词查找已存在的记录
     * 用于判断是更新还是插入
     */
    @Query("SELECT * FROM search_history WHERE user_id = :userId AND keyword = :keyword LIMIT 1")
    SearchHistory findByKeyword(String userId, String keyword);

    /**
     * 已废弃: 使用新的更新逻辑替代
     * 删除重复的关键词(保留最新的)
     */
    @Deprecated
    @Query("DELETE FROM search_history WHERE user_id = :userId AND keyword = :keyword AND id NOT IN (SELECT id FROM search_history WHERE user_id = :userId AND keyword = :keyword ORDER BY search_time DESC LIMIT 1)")
    int deleteDuplicates(String userId, String keyword);
}