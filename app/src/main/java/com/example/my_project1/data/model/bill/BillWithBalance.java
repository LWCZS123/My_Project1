package com.example.my_project1.data.model.bill;

import androidx.room.Embedded;

public class BillWithBalance {
    @Embedded
    public Bill bill;
    
    public double balanceAfter;

    public BillWithBalance() {}

    public BillWithBalance(Bill bill, double balanceAfter) {
        this.bill = bill;
        this.balanceAfter = balanceAfter;
    }
}
