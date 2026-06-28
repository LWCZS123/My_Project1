package com.example.my_project1.data.model.wish;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

/**
 * 关联查询类：一个愿望及其所有存钱记录
 */
public class WishWithRecords {
    @Embedded
    public Wish wish;

    @Relation(parentColumn = "id", entityColumn = "wish_id")
    public List<WishRecord> records;
}