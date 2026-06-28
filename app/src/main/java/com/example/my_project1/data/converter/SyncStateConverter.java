package com.example.my_project1.data.converter;

import androidx.room.TypeConverter;
import com.example.my_project1.data.model.SyncState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Room TypeConverters：用于将枚举或复杂类型转换为数据库可存储类型（如 int/string）。
 */
public class SyncStateConverter {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromSyncState(SyncState state) {
        return state == null ? null : state.name();
    }

    @TypeConverter
    public static SyncState toSyncState(String name) {
        return name == null ? SyncState.SYNCED : SyncState.valueOf(name);
    }


    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null) return Collections.emptyList();
        Type type = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
