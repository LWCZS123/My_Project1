package com.example.my_project1.data.model.calendar;

import java.util.Date;

/**
 * 日历日期数据模型
 */
public class CalendarDay {

    private int year;               // 年
    private int month;              // 月
    private int day;                // 日
    private Date date;              // 完整日期
    private boolean isCurrentMonth; // 是否是当前月份
    private boolean isToday;        // 是否是今天
    private boolean isSelected;     // 是否被选中
    private int holidayType = 0;

    // 账单相关
    private double totalIncome;     // 当日总收入
    private double totalExpense;    // 当日总支出
    private int billCount;          // 当日账单数量

    // 农历相关
    private String lunarText;       // 农历显示文本（初一、节日、节气等）
    private boolean isLunarFestival; // 是否是农历节日
    private boolean isSolarTerm;    // 是否是节气

    public CalendarDay(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.isCurrentMonth = true;
        this.isToday = false;
        this.isSelected = false;
        this.totalIncome = 0.0;
        this.totalExpense = 0.0;
        this.billCount = 0;
        this.lunarText = "";
        this.isLunarFestival = false;
        this.isSolarTerm = false;
    }

    // Getters and Setters

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isCurrentMonth() {
        return isCurrentMonth;
    }

    public void setCurrentMonth(boolean currentMonth) {
        isCurrentMonth = currentMonth;
    }

    public boolean isToday() {
        return isToday;
    }

    public void setToday(boolean today) {
        isToday = today;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(double totalIncome) {
        this.totalIncome = totalIncome;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(double totalExpense) {
        this.totalExpense = totalExpense;
    }

    public int getBillCount() {
        return billCount;
    }

    public void setBillCount(int billCount) {
        this.billCount = billCount;
    }

    public String getLunarText() {
        return lunarText;
    }

    public void setLunarText(String lunarText) {
        this.lunarText = lunarText;
    }

    public boolean isLunarFestival() {
        return isLunarFestival;
    }

    public void setLunarFestival(boolean lunarFestival) {
        isLunarFestival = lunarFestival;
    }

    public boolean isSolarTerm() {
        return isSolarTerm;
    }

    public void setSolarTerm(boolean solarTerm) {
        isSolarTerm = solarTerm;
    }

    public int getHolidayType() { return holidayType; }
    public void setHolidayType(int holidayType) { this.holidayType = holidayType; }

    /**
     * 是否有账单
     */
    public boolean hasBills() {
        return billCount > 0;
    }

    /**
     * 是否有收入
     */
    public boolean hasIncome() {
        return totalIncome > 0;
    }

    /**
     * 是否有支出
     */
    public boolean hasExpense() {
        return totalExpense > 0;
    }
}