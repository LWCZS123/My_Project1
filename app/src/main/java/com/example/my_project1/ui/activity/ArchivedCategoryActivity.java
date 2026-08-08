package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.ui.adapter.ArchivedCategoryAdapter;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.viewmodel.CategoryViewModel;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;

public class ArchivedCategoryActivity extends AppCompatActivity {

    private CategoryViewModel viewModel;
    private ArchivedCategoryAdapter adapter;
    private RecyclerView recyclerView;
    private View layoutEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_category);

        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);
        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        setupRecyclerView();
        observeData();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArchivedCategoryAdapter(this);
        recyclerView.setAdapter(adapter);

        adapter.setOnRestoreClickListener(item -> {
            if (item instanceof Category) {
                Category cat = (Category) item;
                showRestoreDialog(cat.getId(), cat.getName(), false);
            } else if (item instanceof SubCategory) {
                SubCategory sub = (SubCategory) item;
                showRestoreDialog(sub.getId(), sub.getName(), true);
            }
        });
    }

    private void observeData() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String userId = currentUser.getObjectId();
        viewModel.getArchivedCategories(userId).observe(this, list -> {
            if (list == null || list.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                
                List<Object> flattenedList = new ArrayList<>();
                for (CategoryWithSubCategories cw : list) {
                    // 如果一级分类已归档，只显示一级分类（子分类会自动归档但这里为了简洁不平铺显示）
                    if (cw.category.getArchiveStatus() != null && cw.category.getArchiveStatus() == 1) {
                        flattenedList.add(cw.category);
                    } else {
                        // 一级分类没归档，但子分类归档了，平铺显示已归档的子分类
                        if (cw.subCategories != null) {
                            for (SubCategory sub : cw.subCategories) {
                                if (sub.getArchiveStatus() != null && sub.getArchiveStatus() == 1) {
                                    flattenedList.add(sub);
                                }
                            }
                        }
                    }
                }
                adapter.submitList(flattenedList);
            }
        });
    }

    private void showRestoreDialog(long id, String name, boolean isSub) {
        new ConfirmDialog(this)
                .setTitle("取消归档")
                .setMessage("是否将分类「" + name + "」取消归档并恢复使用？")
                .setConfirmText("确定")
                .setCancelText("取消")
                .setConfirmListener(() -> {
                    viewModel.restoreCategory(id, isSub);
                    Toast.makeText(this, "已取消归档", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
