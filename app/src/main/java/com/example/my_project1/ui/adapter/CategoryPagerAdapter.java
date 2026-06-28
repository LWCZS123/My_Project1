package com.example.my_project1.ui.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.my_project1.ui.fragment.ExpenseCategoryFragment;
import com.example.my_project1.ui.fragment.IncomeCategoryFragment;

import io.reactivex.annotations.NonNull;

public class CategoryPagerAdapter extends FragmentStateAdapter {

    public CategoryPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? new ExpenseCategoryFragment() : new IncomeCategoryFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
