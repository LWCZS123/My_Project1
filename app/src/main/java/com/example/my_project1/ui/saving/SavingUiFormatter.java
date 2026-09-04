package com.example.my_project1.ui.saving;

import com.example.my_project1.data.model.saving.SavingPlan;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 存钱模块UI格式化工具类
 * 负责将数据模型转换为用户界面展示所需的文本格式
 */
public final class SavingUiFormatter {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.#");
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
    private static final SimpleDateFormat DATE_SIMPLE = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);

    private SavingUiFormatter() {}

    /**
     * 格式化金额显示，带¥符号
     */
    public static String money(double amount) {
        return "¥" + MONEY.format(amount);
    }

    /**
     * 计算并格式化计划完成百分比
     */
    public static String percent(SavingPlan plan) {
        if (plan.getTargetAmount() <= 0) return "0%";
        double p = (plan.getCurrentAmount() / plan.getTargetAmount()) * 100;
        if (p > 0 && p < 0.1) return "0.1%"; // 最小显示 0.1%
        return PERCENT.format(p) + "%";
    }

    /**
     * 计算计划进度的数值（0-100）
     */
    public static int progress(SavingPlan plan) {
        if (plan.getTargetAmount() <= 0) return 0;
        int progress = (int) Math.min(100, (plan.getCurrentAmount() * 100 / plan.getTargetAmount()));
        if (progress == 0 && plan.getCurrentAmount() > 0) return 1; // 只要存了钱，进度条至少显示 1%
        return progress;
    }

    /**
     * 格式化计划的日期范围显示
     * 逻辑：根据当前时间显示所属周期的起止时间
     */
    public static String dateRange(SavingPlan plan) {
        if (plan.getStartDate() == null) return "";
        
        Calendar start = Calendar.getInstance();
        start.setTime(plan.getStartDate());
        Calendar end = (Calendar) start.clone();
        
        // 这里的逻辑根据截图调整：如果是52周且刚开始，显示第一周的起止
        // 如果是固定周期，也显示当前周期的起止
        switch (plan.getType()) {
            case SavingPlan.TYPE_52WEEK:
                end.add(Calendar.DAY_OF_YEAR, 7);
                break;
            case SavingPlan.TYPE_365DAY:
                end.add(Calendar.DAY_OF_YEAR, 1);
                break;
            default:
                // 默认显示起始日期到结束日期（如果有的话）
                if (plan.getEndDate() != null) {
                    end.setTime(plan.getEndDate());
                } else {
                    end.add(Calendar.YEAR, 1);
                }
                break;
        }
        
        return DATE.format(start.getTime()) + " - " + DATE.format(end.getTime());
    }
    
    /**
     * 格式化简单日期显示（点分隔）
     */
    public static String dateSimple(Date date) {
        if (date == null) return "";
        return DATE_SIMPLE.format(date);
    }

    /**
     * 根据类型ID获取存钱方法的名称
     */
    public static String getTypeName(int type) {
        switch (type) {
            case SavingPlan.TYPE_FIXED: return "定额存钱";
            case SavingPlan.TYPE_FLEXIBLE: return "灵活存钱";
            case SavingPlan.TYPE_52WEEK: return "52周存钱";
            case SavingPlan.TYPE_365DAY: return "365存钱法";
            default: return "存钱计划";
        }
    }
}
