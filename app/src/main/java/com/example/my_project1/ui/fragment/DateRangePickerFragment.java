package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.databinding.FragmentDateRangePickerBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * 日期范围选择器 Fragment
 * 支持选择开始日期和结束日期
 * 使用 DateTimePickerFragment 进行日期选择
 */
public class DateRangePickerFragment extends BottomSheetDialogFragment {

    private FragmentDateRangePickerBinding binding;
    private Calendar startCalendar;
    private Calendar endCalendar;
    private OnDateRangeSelectedListener listener;

    public interface OnDateRangeSelectedListener {
        void onDateRangeSelected(long startTimestamp, long endTimestamp,
                                 String formattedStartDate, String formattedEndDate);
    }

    public void setOnDateRangeSelectedListener(OnDateRangeSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDateRangePickerBinding.inflate(inflater, container, false);

        // 初始化日历
        startCalendar = Calendar.getInstance();
        startCalendar.set(Calendar.DAY_OF_MONTH, 1); // 默认本月第一天

        endCalendar = Calendar.getInstance(); // 默认今天

        setupViews();
        setupListeners();
        updateDateDisplay();

        return binding.getRoot();
    }

    private void setupViews() {
        // 初始化显示
        updateDateDisplay();
    }

    private void setupListeners() {
        // 点击开始日期 - 使用 DateTimePickerFragment
        binding.layoutStartDate.setOnClickListener(v -> showDateTimePicker(true));

        // 点击结束日期 - 使用 DateTimePickerFragment
        binding.layoutEndDate.setOnClickListener(v -> showDateTimePicker(false));

        // 取消按钮
        binding.btnCancel.setOnClickListener(v -> dismiss());

        // 确认按钮
        binding.btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                listener.onDateRangeSelected(
                        startCalendar.getTimeInMillis(),
                        endCalendar.getTimeInMillis(),
                        sdf.format(startCalendar.getTime()),
                        sdf.format(endCalendar.getTime())
                );
            }
            dismiss();
        });
    }

    /**
     * 🎯 显示 DateTimePickerFragment 进行日期选择
     */
    private void showDateTimePicker(boolean isStartDate) {
        DateTimePickerFragment picker = new DateTimePickerFragment();

        // 设置选择回调
        picker.setOnDateTimeSelectedListener((timestamp, formattedDateTime) -> {
            Calendar selectedCalendar = Calendar.getInstance();
            selectedCalendar.setTimeInMillis(timestamp);

            if (isStartDate) {
                // 设置开始日期（保留年月日，时间设为00:00:00）
                startCalendar.set(Calendar.YEAR, selectedCalendar.get(Calendar.YEAR));
                startCalendar.set(Calendar.MONTH, selectedCalendar.get(Calendar.MONTH));
                startCalendar.set(Calendar.DAY_OF_MONTH, selectedCalendar.get(Calendar.DAY_OF_MONTH));
                startCalendar.set(Calendar.HOUR_OF_DAY, 0);
                startCalendar.set(Calendar.MINUTE, 0);
                startCalendar.set(Calendar.SECOND, 0);
                startCalendar.set(Calendar.MILLISECOND, 0);

                // 如果开始日期晚于结束日期，自动调整结束日期
                if (startCalendar.after(endCalendar)) {
                    endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
                    endCalendar.set(Calendar.HOUR_OF_DAY, 23);
                    endCalendar.set(Calendar.MINUTE, 59);
                    endCalendar.set(Calendar.SECOND, 59);
                }
            } else {
                // 设置结束日期（保留年月日，时间设为23:59:59）
                endCalendar.set(Calendar.YEAR, selectedCalendar.get(Calendar.YEAR));
                endCalendar.set(Calendar.MONTH, selectedCalendar.get(Calendar.MONTH));
                endCalendar.set(Calendar.DAY_OF_MONTH, selectedCalendar.get(Calendar.DAY_OF_MONTH));
                endCalendar.set(Calendar.HOUR_OF_DAY, 23);
                endCalendar.set(Calendar.MINUTE, 59);
                endCalendar.set(Calendar.SECOND, 59);
                endCalendar.set(Calendar.MILLISECOND, 999);

                // 如果结束日期早于开始日期，自动调整开始日期
                if (endCalendar.before(startCalendar)) {
                    startCalendar.setTimeInMillis(endCalendar.getTimeInMillis());
                    startCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    startCalendar.set(Calendar.MINUTE, 0);
                    startCalendar.set(Calendar.SECOND, 0);
                    startCalendar.set(Calendar.MILLISECOND, 0);
                }
            }

            // 更新显示
            updateDateDisplay();
        });

        // 显示 DateTimePickerFragment
        picker.show(getParentFragmentManager(), "DateTimePicker");
    }

    /**
     * 更新日期显示
     */
    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());

        binding.tvStartDate.setText(sdf.format(startCalendar.getTime()));
        binding.tvEndDate.setText(sdf.format(endCalendar.getTime()));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 背景圆角
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_bottom_sheet1));

                // 设置为自适应高度
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(false);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setPeekHeight(BottomSheetBehavior.PEEK_HEIGHT_AUTO);
                behavior.setFitToContents(true);
                behavior.setHideable(true);
            }
        });

        return bottomSheetDialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}