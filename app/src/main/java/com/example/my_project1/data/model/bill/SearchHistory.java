package com.example.my_project1.data.model.bill;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * SearchHistory - 搜索历史实体
 * -------------------------------------------------------
 * 用于保存用户的搜索记录
 */
@Entity(tableName = "search_history")
public class SearchHistory {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "user_id")
    private String userId;        // 所属用户

    @ColumnInfo(name = "keyword")
    private String keyword;       // 搜索关键词

    @ColumnInfo(name = "search_time")
    private Date searchTime;      // 搜索时间

    // ==================== 构造函数 ====================

    public SearchHistory() {}

    @Ignore
    public SearchHistory(String userId, String keyword) {
        this.userId = userId;
        this.keyword = keyword;
        this.searchTime = new Date();
    }

    // ==================== Getter / Setter ====================

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Date getSearchTime() {
        return searchTime;
    }

    public void setSearchTime(Date searchTime) {
        this.searchTime = searchTime;
    }

    @Override
    public String toString() {
        return "SearchHistory{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", keyword='" + keyword + '\'' +
                ", searchTime=" + searchTime +
                '}';
    }
}