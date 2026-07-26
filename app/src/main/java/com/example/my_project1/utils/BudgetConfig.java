package com.example.my_project1.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class BudgetConfig {
    private static final String PREF_NAME = "budget_settings";
    private static final String KEY_START_DAY = "budget_start_day";

    public static int getStartDay(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getInt(KEY_START_DAY, 1); // Default to 1st
    }

    public static void setStartDay(Context context, int day) {
        if (day < 1 || day > 28) day = 1;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_START_DAY, day).apply();
    }
}
