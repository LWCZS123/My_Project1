package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.ActivityAddAccountGroupBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.SnackbarUtils;

import java.util.Arrays;
import java.util.List;

import cn.bmob.v3.BmobUser;

public class AddAccountGroupActivity extends AppCompatActivity {

    private ActivityAddAccountGroupBinding binding;
    private AccountViewModel viewModel;
    private String editGroupId;
    private String selectedIcon = "ic_category";

    private final List<String> groupIcons = Arrays.asList(
            "ic_category", "ic_wallet", "ic_card", "ic_money",
            "ic_assets", "ic_bill", "ic_home", "ic_user"
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddAccountGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        editGroupId = getIntent().getStringExtra("editGroup");

        setupUI();
        setupIconPicker();
    }

    private void setupUI() {
        binding.ivBack.setOnClickListener(v -> finish());
        binding.tvSave.setOnClickListener(v -> saveGroup());

        if (editGroupId != null) {
            binding.tvTitle.setText("编辑分组");
            loadGroupData();
        }
    }

    private void loadGroupData() {
        // Implementation for loading group data if editing
    }

    private void setupIconPicker() {
        binding.rvIcons.setLayoutManager(new GridLayoutManager(this, 4));
        binding.rvIcons.setAdapter(new IconAdapter());
    }

    private void saveGroup() {
        String name = binding.etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            SnackbarUtils.showWarning(binding.getRoot(), "请输入分组名称");
            return;
        }

        AccountGroup group = new AccountGroup();
        group.setName(name);
        group.setUserId(BmobUser.getCurrentUser().getObjectId());
        group.setIconUrl(selectedIcon);

        if (editGroupId != null) {
            group.setObjectId(editGroupId);
            viewModel.updateAccountGroup(group, (success, message) -> {
                if (success) {
                    finish();
                } else {
                    SnackbarUtils.showError(binding.getRoot(), message);
                }
            });
        } else {
            viewModel.insertAccountGroup(group, (success, message) -> {
                if (success) {
                    finish();
                } else {
                    SnackbarUtils.showError(binding.getRoot(), message);
                }
            });
        }
    }

    private class IconAdapter extends RecyclerView.Adapter<IconAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(AddAccountGroupActivity.this);
            int size = (int) (48 * getResources().getDisplayMetrics().density);
            int padding = (int) (8 * getResources().getDisplayMetrics().density);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new ViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String iconName = groupIcons.get(position);
            int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
            if (resId != 0) {
                ((ImageView) holder.itemView).setImageResource(resId);
            }
            
            holder.itemView.setAlpha(selectedIcon.equals(iconName) ? 1.0f : 0.4f);
            holder.itemView.setOnClickListener(v -> {
                selectedIcon = iconName;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return groupIcons.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
