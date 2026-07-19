package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ActivityAccountListBinding;
import com.example.my_project1.ui.adapter.account.HiddenAccountAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.ArrayList;
import java.util.List;

public class HiddenAccountsActivity extends AppCompatActivity {

    private ActivityAccountListBinding binding;
    private AccountViewModel viewModel;
    private HiddenAccountAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        setupUI();
        observeData();
    }

    private void setupUI() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.tvTitle.setText("隐藏账户");

        adapter = new HiddenAccountAdapter();
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAccounts.setAdapter(adapter);

        binding.tvAction.setOnClickListener(v -> {
            boolean isAllSelected = adapter.getSelectedCount() == adapter.getItemCount();
            adapter.selectAll(!isAllSelected);
        });

        binding.tvCancelHide.setOnClickListener(v -> {
            List<Account> selected = adapter.getSelectedAccounts();
            if (selected.isEmpty()) return;

            for (Account acc : selected) {
                acc.setIncludeInTotal(true);
                viewModel.updateAccount(acc);
            }
            
            SnackbarUtils.showSuccess(binding.getRoot(), "已取消隐藏 " + selected.size() + " 个账户");
            adapter.setMultiSelectMode(false);
        });

        adapter.setOnSelectionChangeListener((selectedCount, isMultiSelectMode) -> {
            if (isMultiSelectMode) {
                binding.tvAction.setVisibility(View.VISIBLE);
                binding.layoutBottomAction.setVisibility(View.VISIBLE);
                binding.tvAction.setText(selectedCount == adapter.getItemCount() ? "取消全选" : "全选");
                binding.tvTitle.setText("已选择 " + selectedCount + " 个账户");
            } else {
                binding.tvAction.setVisibility(View.GONE);
                binding.layoutBottomAction.setVisibility(View.GONE);
                binding.tvTitle.setText("隐藏账户");
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Use a flag or check adapter state if it exists
        // Since we don't have a public getter for isMultiSelectMode, we can add one or check selectedCount
        if (binding.layoutBottomAction.getVisibility() == View.VISIBLE) {
            adapter.setMultiSelectMode(false);
        } else {
            super.onBackPressed();
        }
    }

    private void observeData() {
        viewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                List<Account> hidden = new ArrayList<>();
                for (Account acc : accounts) {
                    if (!acc.isIncludeInTotal()) {
                        hidden.add(acc);
                    }
                }
                adapter.setAccounts(hidden);
            }
        });
    }
}
