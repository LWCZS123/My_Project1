package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.databinding.ActivityBatchDownloadBinding;
import com.example.my_project1.ui.adapter.IconDownloadAdapter;
import com.example.my_project1.ui.fragment.DownloadCompleteFragment;
import com.example.my_project1.ui.viewmodel.icon.DownloadViewModel;
import com.example.my_project1.utils.DownloadPathManager;

public class BatchDownloadActivity extends AppCompatActivity {

    private ActivityBatchDownloadBinding binding;
    private IconDownloadAdapter adapter;
    private DownloadViewModel viewModel;
    private String filterBatchId = null;
    private DownloadPathManager pathManager;

    private final ActivityResultLauncher<Uri> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    pathManager.saveCustomDownloadDir(uri);
                    updatePathDisplay();
                    Toast.makeText(this, "下载路径已更新", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 开启全屏适配，使底部导航栏颜色与页面背景一致
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        
        binding = ActivityBatchDownloadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 应用 WindowInsets，防止内容被状态栏或导航栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            
            binding.layoutToolbar.setPadding(0, statusBarHeight, 0, 0);
            binding.layoutBottomActions.setPadding(
                    binding.layoutBottomActions.getPaddingLeft(),
                    binding.layoutBottomActions.getPaddingTop(),
                    binding.layoutBottomActions.getPaddingRight(),
                    navBarHeight + 16
            );
            return insets;
        });

        pathManager = DownloadPathManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(DownloadViewModel.class);

        filterBatchId = getIntent().getStringExtra("batchId");
        viewModel.setFilterBatchId(filterBatchId);
        
        if (filterBatchId != null) {
            binding.tvTitle.setText("下载详情");
        }

        initView();
        setupRecyclerView();
        observeViewModel();
    }

    private void initView() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnClose.setOnClickListener(v -> finish());
        
        binding.btnCancelDownload.setOnClickListener(v -> showCancelConfirmDialog());
        binding.btnPauseDownload.setOnClickListener(v -> togglePauseResume());
        
        if (binding.btnClearHistory != null) {
            binding.btnClearHistory.setVisibility(View.GONE);
        }
        binding.btnClearHistoryList.setOnClickListener(v -> showClearHistoryDialog());

        binding.btnToggleCollections.setOnClickListener(v -> {
            binding.btnToggleCollections.setTextColor(Color.parseColor("#6675F5"));
            binding.btnToggleCollections.setTypeface(null, android.graphics.Typeface.BOLD);
            binding.btnToggleSingles.setTextColor(Color.parseColor("#8790A8"));
            binding.btnToggleSingles.setTypeface(null, android.graphics.Typeface.NORMAL);
            adapter.setFilterType(0);
        });

        binding.btnToggleSingles.setOnClickListener(v -> {
            binding.btnToggleSingles.setTextColor(Color.parseColor("#6675F5"));
            binding.btnToggleSingles.setTypeface(null, android.graphics.Typeface.BOLD);
            binding.btnToggleCollections.setTextColor(Color.parseColor("#8790A8"));
            binding.btnToggleCollections.setTypeface(null, android.graphics.Typeface.NORMAL);
            adapter.setFilterType(1);
        });

        updatePathDisplay();

        binding.btnLocationManagement.setOnClickListener(v -> {
            binding.btnLocationManagement.setEnabled(false);
            folderPickerLauncher.launch(null);
            binding.btnLocationManagement.postDelayed(() -> binding.btnLocationManagement.setEnabled(true), 1000);
        });
    }

    private void togglePauseResume() {
        DownloadViewModel.DownloadStats stats = viewModel.getStats().getValue();
        if (stats != null) {
            boolean isPaused = "已暂停".equals(stats.statusText) || binding.btnPauseDownload.getText().toString().contains("恢复");
            if (isPaused) {
                viewModel.resumeDownload(filterBatchId);
            } else {
                viewModel.pauseDownload(filterBatchId);
            }
        }
    }

    private void showCancelConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("取消下载")
                .setMessage("确定要取消当前的下载任务吗？已下载的文件将被保留。")
                .setPositiveButton("确定取消", (dialog, which) -> {
                    viewModel.cancelDownload(filterBatchId);
                })
                .setNegativeButton("继续下载", null)
                .show();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清除下载历史")
                .setMessage("确定要清除下载记录吗？这不会删除您已下载的真实图标文件。")
                .setPositiveButton("清除记录", (dialog, which) -> {
                    viewModel.clearHistory(filterBatchId);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updatePathDisplay() {
        binding.tvLocationDesc.setText(pathManager.getDisplayPath());
    }

    private void setupRecyclerView() {
        adapter = new IconDownloadAdapter();
        adapter.setListener(new IconDownloadAdapter.OnItemActionListener() {
            @Override
            public void onRetryIcon(String iconId) {
                viewModel.retryIconDownload(iconId);
            }
            @Override
            public void onRetryCollection(String batchId) {
                viewModel.resumeDownload(batchId);
            }
            @Override
            public void onOpenFolder(String subDir, String treeUri) {
                pathManager.openDownloadFolder(subDir, treeUri);
            }
        });
        binding.rvIconList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvIconList.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getRecords().observe(this, records -> {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.layoutContent.setVisibility(View.VISIBLE);
            binding.layoutBottomActions.setVisibility(View.VISIBLE);
            adapter.setItems(records);
        });

        viewModel.getStats().observe(this, stats -> {
            if (stats == null) return;

            binding.tvListTotal.setText("共 " + stats.totalCount + " 枚");
            binding.tvIconTotal.setText(String.valueOf(stats.totalCount));
            binding.tvCompletedTotal.setText(String.valueOf(stats.completedCount));
            binding.tvCollectionTotal.setText(stats.collectionCount + " 套");
            
            binding.tvDownloadedCount.setText(stats.completedCount + " / " + stats.totalCount + " 枚");
            binding.tvDownloadedSize.setText(stats.sizeText);

            binding.progressCircular.setProgress(stats.progress);
            binding.tvProgressPercent.setText(stats.progress + "%");
            binding.progressDownload.setProgress(stats.progress);
            binding.tvProgressValue.setText(stats.completedCount + " / " + stats.totalCount);
            
            binding.tvProgressStatus.setText(stats.statusText);
            
            // 更新按钮交互状态
            if (stats.totalCount > 0 && stats.progress == 100) {
                binding.btnPauseDownload.setEnabled(false);
                binding.btnPauseDownload.setText("下载完成");
                binding.btnCancelDownload.setEnabled(false);
            } else {
                binding.btnPauseDownload.setEnabled(true);
                binding.btnCancelDownload.setEnabled(true);
                if ("已暂停".equals(stats.statusText)) {
                    binding.btnPauseDownload.setText("恢复下载");
                } else {
                    binding.btnPauseDownload.setText("暂停下载");
                }
            }
        });

        viewModel.getCompletionEvent().observe(this, event -> {
            if (event != null) {
                DownloadCompleteFragment fragment = DownloadCompleteFragment.newInstance(
                        event.total, event.success, event.failed, 
                        pathManager.getDisplayPath(), event.collectionName, event.treeUri);
                fragment.show(getSupportFragmentManager(), "DownloadComplete");
                viewModel.markSummaryShown(event.batchId);
            }
        });
    }
}
