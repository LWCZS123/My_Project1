package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.databinding.ActivityBatchDownloadBinding;
import com.example.my_project1.ui.adapter.IconDownloadAdapter;

import java.util.ArrayList;
import java.util.List;

public class BatchDownloadActivity extends AppCompatActivity {

    private ActivityBatchDownloadBinding binding;
    private IconDownloadAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBatchDownloadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initView();
        setupRecyclerView();
        loadDummyData();
    }

    private void initView() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnClose.setOnClickListener(v -> finish());
        binding.btnCancelDownload.setOnClickListener(v -> finish());
        binding.btnPauseDownload.setOnClickListener(v -> {
            // 处理暂停下载逻辑
        });
        
        // 设置初始下载进度
        binding.progressCircular.setProgress(46);
        binding.tvProgressPercent.setText("46%");
        binding.progressDownload.setProgress(46);
    }

    private void setupRecyclerView() {
        adapter = new IconDownloadAdapter();
        binding.rvIconList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvIconList.setAdapter(adapter);
    }

    private void loadDummyData() {
        List<IconItem> items = new ArrayList<>();
        // 模拟数据加载
        for (int i = 0; i < 20; i++) {
            IconItem item = new IconItem();
            item.setName("Icon " + (i + 1));
            items.add(item);
        }

        updateUIState(items);
    }

    private void updateUIState(List<IconItem> items) {
        if (items == null || items.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.layoutContent.setVisibility(View.GONE); // 需要在布局中添加这个ID
            binding.layoutBottomActions.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);
            binding.layoutBottomActions.setVisibility(View.VISIBLE);
            
            adapter.setItems(items);
            binding.tvListTotal.setText("共 " + items.size() + " 枚");
            binding.tvCompletedTotal.setText(String.valueOf(items.size() / 2));
        }
    }
}