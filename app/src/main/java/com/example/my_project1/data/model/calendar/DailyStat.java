package com.example.my_project1.data.model.calendar;

import java.io.Serializable;

public class DailyStat implements Serializable {
    private static final long serialVersionUID = 1L;

    public double income;
    public double expense;
    public int count;
    public boolean isHoliday;
    public String dayTag; // "休" or "班"

    public DailyStat(double income, double expense, int count) {
        this.income = income;
        this.expense = expense;
        this.count = count;
    }
}
