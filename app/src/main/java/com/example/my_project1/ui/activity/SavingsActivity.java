package com.example.my_project1.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.databinding.ActivitySavingsBinding;
import com.example.my_project1.ui.adapter.desire.WishHistoryAdapter;
import com.example.my_project1.ui.fragment.AddSavingRecordFragment;
import com.example.my_project1.ui.fragment.AddWishFragment;
import com.example.my_project1.ui.dialog.ConfirmDialog;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.example.my_project1.ui.wish.WishUiFormatter;

import java.util.Collections;

public class SavingsActivity extends AppCompatActivity {

    private ActivitySavingsBinding binding;
    private WishViewModel viewModel;
    private WishHistoryAdapter historyAdapter;
    private long wishId;
    private Wish currentWish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySavingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#F7F6FA"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F7F6FA"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
        wishId = getIntent().getLongExtra(SavingsOverviewActivity.EXTRA_WISH_ID, -1);
        if (wishId <= 0) {
            finish();
            return;
        }
        viewModel = new ViewModelProvider(this).get(WishViewModel.class);
        setupList();
        setupActions();
        observe();
    }

    private void setupList() {
        historyAdapter = new WishHistoryAdapter(new WishHistoryAdapter.Listener() {
            @Override
            public void onEdit(WishRecord record) {
                AddSavingRecordFragment.newInstanceForEdit(wishId, record.getId())
                        .show(getSupportFragmentManager(), "record_form");
            }

            @Override
            public void onDelete(WishRecord record) {
                confirmRecordDelete(record);
            }
        });
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvHistory.setAdapter(historyAdapter);
    }

    private void setupActions() {
        binding.btnEdit.setOnClickListener(v ->
                AddWishFragment.newInstanceForEdit(wishId)
                        .show(getSupportFragmentManager(), "wish_form"));
        binding.btnDelete.setOnClickListener(v -> confirmWishDelete());
        binding.fabAdd.setOnClickListener(v -> {
            if (currentWish != null) {
                AddSavingRecordFragment.newInstance(wishId)
                        .show(getSupportFragmentManager(), "record_form");
            }
        });
    }

    private void observe() {
        viewModel.getWishById(wishId).observe(this, wish -> {
            if (wish == null) {
                if (currentWish != null) finish();
                return;
            }
            currentWish = wish;
            renderWish(wish);
        });
        viewModel.getRecords(wishId).observe(this, records -> {
            if (records == null) records = Collections.emptyList();
            historyAdapter.submitList(records);
            binding.tvHistoryEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getOperationState().observe(this, state -> {
            if (state.isTerminal() && state.getMessage() != null) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                binding.getRoot().post(viewModel::resetOperationState);
            }
        });
    }

    private void renderWish(Wish wish) {
        binding.tvTitle.setText(wish.getWishName());
        
        // 设置状态及图标
        binding.tvStatus.setText(WishUiFormatter.status(wish));
        int statusIcon;
        switch (wish.getStatus()) {
            case Wish.STATUS_COMPLETED:
                statusIcon = R.drawable.ic_check_circle;
                break;
            case Wish.STATUS_ABANDONED:
                statusIcon = R.drawable.ic_cancel;
                break;
            default:
                statusIcon = R.drawable.ic_clock;
                break;
        }

        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, statusIcon);
        if (drawable != null) {
            int size = (int) (14 * getResources().getDisplayMetrics().density);
            drawable.setBounds(0, 0, size, size);
            binding.tvStatus.setCompoundDrawables(drawable, null, null, null);
        }
        binding.tvStatus.setCompoundDrawablePadding(8);
        
        int progress = WishUiFormatter.progress(wish);
        // 如果是首次加载（没有旧数据），则执行动画
        if (binding.donutProgress.getProgress() == 0 && progress > 0) {
            binding.donutProgress.setProgressWithAnimation(progress, 1200);
        } else {
            binding.donutProgress.setProgress(progress);
        }

        binding.tvPercent.setText(WishUiFormatter.percent(wish));
        binding.tvAmount.setText(WishUiFormatter.money(wish.getCurrentAmount()));
        binding.tvGoalAmount.setText(WishUiFormatter.money(wish.getTargetAmount()));
        binding.tvRemaining.setText(WishUiFormatter.money(
                Math.max(0d, wish.getTargetAmount() - wish.getCurrentAmount())));
        binding.tvStartDate.setText(WishUiFormatter.date(wish.getStartDate()));
        binding.tvDays.setText(String.valueOf(WishUiFormatter.elapsedDays(wish.getStartDate())));
    }

    private void confirmWishDelete() {
        new ConfirmDialog(this)
                .setTitle("删除愿望")
                .setMessage("该愿望及全部记录将被删除，确定继续吗？")
                .setConfirmListener(() -> viewModel.deleteWish(wishId))
                .show();
    }

    private void confirmRecordDelete(WishRecord record) {
        new ConfirmDialog(this)
                .setTitle("删除记录")
                .setMessage("删除后愿望进度会自动重新计算。")
                .setConfirmListener(() -> viewModel.deleteRecord(record.getId()))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
