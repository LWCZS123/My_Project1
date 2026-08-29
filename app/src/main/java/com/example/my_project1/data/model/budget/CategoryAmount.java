package com.example.my_project1.data.model.budget;

import androidx.room.ColumnInfo;

/** Lightweight result returned by the grouped budget-spending query. */
public class CategoryAmount {

    @ColumnInfo(name = "category_id")
    public String categoryId;

    @ColumnInfo(name = "total_amount")
    public double totalAmount;
}
