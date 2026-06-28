package com.example.my_project1.data.provider;

import android.content.Context;
import android.util.Log;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.utils.ImageUriUtils;

import java.util.ArrayList;
import java.util.List;

public class SystemPresetDataProvider {

    public static List<Category> getExpensePresets(Context context, String userId) {
        List<Category> list = new ArrayList<>();
        Log.d("SystemPresetDataProvider", "当前用户"+userId.toString());

        // 购物
        list.add(createCategory(context, "CHI", R.drawable.ic_expense_shopping, userId, "expense",
                createSubs(context,userId, new String[]{"衣服", "日用品", "数码产品"},
                        new int[]{R.drawable.ic_sub_clothes, R.drawable.ic_sub_daily, R.drawable.ic_sub_electronics})));
        /*
        // 食品
        list.add(createCategory(context, "食品", R.drawable.ic_expense_food, userId, "expense",
                createSubs(context, new String[]{"早餐", "午餐", "晚餐"},
                        new int[]{R.drawable.ic_sub_breakfast, R.drawable.ic_sub_lunch, R.drawable.ic_sub_dinner})));

        // 出行
        list.add(createCategory(context, "出行", R.drawable.ic_expense_transport, userId, "expense",
                createSubs(context, new String[]{"公交", "打车", "地铁"},
                        new int[]{R.drawable.ic_sub_bus, R.drawable.ic_sub_taxi, R.drawable.ic_sub_metro})));

        // 娱乐
        list.add(createCategory(context, "娱乐", R.drawable.ic_expense_entertainment, userId, "expense",
                createSubs(context, new String[]{"电影", "游戏", "聚会"},
                        new int[]{R.drawable.ic_sub_movie, R.drawable.ic_sub_game, R.drawable.ic_sub_party})));

        // 生活
        list.add(createCategory(context, "生活", R.drawable.ic_expense_life, userId, "expense",
                createSubs(context, new String[]{"房租", "水电", "物业"},
                        new int[]{R.drawable.ic_sub_rent, R.drawable.ic_sub_water, R.drawable.ic_sub_property})));

        // 送礼
        list.add(createCategory(context, "送礼", R.drawable.ic_expense_gift, userId, "expense",
                createSubs(context, new String[]{"生日礼物", "节日礼物", "婚礼礼物"},
                        new int[]{R.drawable.ic_sub_birthday_gift, R.drawable.ic_sub_festival_gift, R.drawable.ic_sub_wedding_gift})));

        */

        return list;

    }

    public static List<Category> getIncomePresets(Context context, String userId) {
        List<Category> list = new ArrayList<>();

        // 中奖
        list.add(createCategory(context, "ZHJ", R.drawable.ic_income_lottery, userId, "income",
                createSubs(context,userId, new String[]{"彩票中奖", "抽奖活动", "游戏奖励"},
                        new int[]{R.drawable.ic_sub_lottery_ticket, R.drawable.ic_sub_raffle, R.drawable.ic_sub_game_reward})));

        /*
        // 理财
        list.add(createCategory(context, "理财", R.drawable.ic_income_investment, userId, "income",
                createSubs(context, new String[]{"股票分红", "基金收益", "利息收入"},
                        new int[]{R.drawable.ic_sub_stock, R.drawable.ic_sub_fund, R.drawable.ic_sub_interest})));

        // 人情
        list.add(createCategory(context, "人情", R.drawable.ic_income_gift, userId, "income",
                createSubs(context, new String[]{"收红包", "朋友请客", "礼金"},
                        new int[]{R.drawable.ic_sub_redpacket, R.drawable.ic_sub_dinner, R.drawable.ic_sub_giftmoney})));

        // 奖金
        list.add(createCategory(context, "奖金", R.drawable.ic_income_bonus, userId, "income",
                createSubs(context, new String[]{"年终奖", "绩效奖", "提成"},
                        new int[]{R.drawable.ic_sub_year_bonus, R.drawable.ic_sub_performance, R.drawable.ic_sub_commission})));

        // 工资
        list.add(createCategory(context, "工资", R.drawable.ic_income_salary, userId, "income",
                createSubs(context, new String[]{"基本工资", "加班费", "津贴"},
                        new int[]{R.drawable.ic_sub_basic_salary, R.drawable.ic_sub_overtime, R.drawable.ic_sub_allowance})));

        // 外快
        list.add(createCategory(context, "外快", R.drawable.ic_income_side, userId, "income",
                createSubs(context, new String[]{"兼职", "代购", "二手交易"},
                        new int[]{R.drawable.ic_sub_parttime, R.drawable.ic_sub_agent, R.drawable.ic_sub_resale})));

        */

        return list;


    }
    // ---------------- 整合：返回所有系统预设 ----------------
    public static List<Category> getAllSystemPresets(Context context, String userId) {
        List<Category> all = new ArrayList<>();
        all.addAll(getExpensePresets(context, userId));
        all.addAll(getIncomePresets(context, userId));
        return all;
    }


    // ------------------- 通用工具方法 -------------------

    private static Category createCategory(Context context, String name, int iconRes,
                                           String userId, String type, List<SubCategory> subList) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setOwnerId(userId);
        c.setSystemPreset(true);

        c.setIconUri(ImageUriUtils.getResourceUri(context, iconRes).toString());

        for (SubCategory sub : subList) {
            sub.setSystemPreset(true);
        }
        c.setSubCategories(subList);
        return c;
    }

    private static List<SubCategory> createSubs(Context context, String userId, String[] names, int[] icons) {
        List<SubCategory> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            SubCategory sub = new SubCategory();
            sub.setName(names[i]);
            sub.setOwnerId(userId);
            sub.setIconUri(ImageUriUtils.getResourceUri(context, icons[i]).toString());
            list.add(sub);


        }
        return list;
    }
}
