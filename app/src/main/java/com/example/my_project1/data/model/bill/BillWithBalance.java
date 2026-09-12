package com.example.my_project1.data.model.bill;

import androidx.room.Embedded;
import androidx.room.Ignore;

public class BillWithBalance {
    @Embedded
    public Bill bill;
    
    public double balanceAfter;

    public BillWithBalance() {}

    @Ignore
    public BillWithBalance(Bill bill, double balanceAfter) {
        this.bill = bill;
        this.balanceAfter = balanceAfter;
    }
}
