package com.example.my_project1.data.model;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

/**
 * Category 与 SubCategory 的一对多关系模型
 * 用于 Room 一次性加载父分类和子分类
 */
public class CategoryWithSubCategories {
    @Embedded
    public Category category;

    @Relation(
            parentColumn = "id",               // Category 的主键
            entityColumn = "parent_category_id"  // SubCategory 表中的外键
    )
    public List<SubCategory> subCategories;
}
