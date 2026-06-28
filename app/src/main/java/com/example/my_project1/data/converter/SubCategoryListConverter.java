package com.example.my_project1.data.converter;

import androidx.room.TypeConverter;

import com.example.my_project1.data.model.SubCategory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class SubCategoryListConverter {

    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromSubCategoryList(List<SubCategory> list) {
        return list == null ? null : gson.toJson(list);
    }

    @TypeConverter
    public static List<SubCategory> toSubCategoryList(String json) {
        if (json == null) return Collections.emptyList();
        Type type = new TypeToken<List<SubCategory>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
