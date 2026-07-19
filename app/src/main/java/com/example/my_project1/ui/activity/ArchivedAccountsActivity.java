package com.example.my_project1.ui.activity;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ActivityAccountListBinding;
import com.example.my_project1.ui.adapter.account.AccountSubAdapter;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.ArrayList;
import java.util.List;

public class ArchivedAccountsActivity extends AppCompatActivity {

    private ActivityAccountListBinding binding;
    private AccountViewModel viewModel;
    private AccountSubAdapter adapter;

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
        binding.tvTitle.setText("归档账户");

        adapter = new AccountSubAdapter();
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAccounts.setAdapter(adapter);

        adapter.setOnAccountClickListener(new AccountSubAdapter.OnAccountClickListener() {
            @Override public void onAccountClick(Account account) {}
            @Override public void onAccountDelete(Account account) {
                viewModel.deleteAccount(account);
            }
            @Override public void onAccountHide(Account account) {
                account.setIncludeInTotal(false);
                viewModel.updateAccount(account);
            }
            @Override public void onAccountArchive(Account account) {
                // Here "Archive" click on an already archived account means "Unarchive"
                account.setCanBeSelected(true);
                viewModel.updateAccount(account);
                SnackbarUtils.showSuccess(binding.getRoot(), "已恢复账户：" + account.getName());
            }
            @Override public void onAccountEdit(Account account) {}
        });
    }

    private void observeData() {
        viewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                List<Account> archived = new ArrayList<>();
                for (Account acc : accounts) {
                    if (!acc.isCanBeSelected()) {
                        archived.add(acc);
                    }
                }
                adapter.setAccounts(archived);
            }
        });
    }
}
