package com.example.my_project1.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.FragmentAddWishBinding;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.example.my_project1.ui.wish.WishUiFormatter;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;
import java.util.Date;

public class AddWishFragment extends BottomSheetDialogFragment {

    private static final String ARG_WISH_ID = "wish_id";
    private static final String STATE_SELECTED_DATE = "selected_date";
    private static final String STATE_SELECTED_ICON = "selected_icon";
    private static final String STATE_SELECTED_ICON_COLOR = "selected_icon_color";

    private FragmentAddWishBinding binding;
    private WishViewModel viewModel;
    private long editWishId;
    private Wish loadedWish;
    private Date selectedDate = new Date();
    private String selectedIconUrl;
    private String selectedIconBackgroundColor;
    private boolean populated;
    private boolean saving;

    private final ActivityResultLauncher<Intent> iconPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                selectedIconUrl = result.getData().getStringExtra(
                        IconSelectionActivity.EXTRA_ICON_URI);
                selectedIconBackgroundColor = result.getData().getStringExtra(
                        IconSelectionActivity.EXTRA_ICON_BG_COLOR);
                renderSelectedIcon();
            });

    public static AddWishFragment newInstance() {
        return new AddWishFragment();
    }

    public static AddWishFragment newInstanceForEdit(long wishId) {
        AddWishFragment fragment = new AddWishFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_WISH_ID, wishId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editWishId = getArguments() == null ? 0 : getArguments().getLong(ARG_WISH_ID, 0);
        if (savedInstanceState != null) {
            selectedDate = new Date(savedInstanceState.getLong(
                    STATE_SELECTED_DATE, System.currentTimeMillis()));
            selectedIconUrl = savedInstanceState.getString(STATE_SELECTED_ICON);
            selectedIconBackgroundColor = savedInstanceState.getString(
                    STATE_SELECTED_ICON_COLOR);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddWishBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);
        binding.etStartDate.setText(WishUiFormatter.date(selectedDate));
        binding.etStartDate.setOnClickListener(v -> showDatePicker());
        binding.iconPicker.setOnClickListener(v -> openIconPicker());
        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> save());
        renderSelectedIcon();
        if (editWishId > 0) {
            binding.tvFormTitle.setText("编辑愿望");
            binding.btnSave.setText("保存修改");
            viewModel.getWishById(editWishId).observe(getViewLifecycleOwner(), this::populate);
        }
        viewModel.getOperationState().observe(getViewLifecycleOwner(), state -> {
            if (!saving) return;
            binding.btnSave.setEnabled(!state.isLoading());
            if (state.isSuccess()) {
                saving = false;
                dismiss();
            } else if (state.isError()) {
                saving = false;
            }
        });
    }

    private void populate(Wish wish) {
        if (wish == null || populated) return;
        loadedWish = wish;
        populated = true;
        selectedDate = wish.getStartDate() == null ? new Date() : wish.getStartDate();
        if (TextUtils.isEmpty(selectedIconUrl)) {
            selectedIconUrl = wish.getIconUrl();
        }
        binding.etName.setText(wish.getWishName());
        binding.etTargetAmount.setText(String.valueOf(wish.getTargetAmount()));
        binding.etStartDate.setText(WishUiFormatter.date(selectedDate));
        binding.etRemark.setText(wish.getRemark());
        renderSelectedIcon();
    }

    private void save() {
        String name = textOf(binding.etName.getText());
        String amountText = textOf(binding.etTargetAmount.getText());
        binding.inputName.setError(null);
        binding.inputTarget.setError(null);
        if (TextUtils.isEmpty(name)) {
            binding.inputName.setError("请输入愿望名称");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException exception) {
            binding.inputTarget.setError("请输入有效金额");
            return;
        }
        if (amount <= 0d || Double.isNaN(amount) || Double.isInfinite(amount)) {
            binding.inputTarget.setError("目标金额必须大于 0");
            return;
        }
        Wish wish = new Wish();
        if (loadedWish != null) {
            wish.setId(loadedWish.getId());
            wish.setStatus(loadedWish.getStatus());
        } else {
            wish.setStatus(Wish.STATUS_ACTIVE);
        }
        wish.setWishName(name);
        wish.setIconUrl(selectedIconUrl);
        wish.setTargetAmount(amount);
        wish.setStartDate(selectedDate);
        wish.setRemark(textOf(binding.etRemark.getText()));
        saving = true;
        viewModel.saveWish(wish);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);
        CustomDateTimePickerFragment.show(getChildFragmentManager(), calendar, selected -> {
            // 愿望只保存日期，统一清除选择器返回的具体时间。
            selected.set(Calendar.HOUR_OF_DAY, 0);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
            selectedDate = selected.getTime();
            binding.etStartDate.setText(WishUiFormatter.date(selectedDate));
        });
    }

    private void openIconPicker() {
        Intent intent = new Intent(requireContext(), IconSelectionActivity.class);
        intent.putExtra(IconSelectionActivity.EXTRA_PICK_ONLY, true);
        intent.putExtra(IconSelectionActivity.EXTRA_TITLE, "选择愿望图标");
        intent.putExtra(IconSelectionActivity.EXTRA_ICON_URI, selectedIconUrl);
        intent.putExtra(IconSelectionActivity.EXTRA_ICON_BG_COLOR,
                selectedIconBackgroundColor);
        iconPickerLauncher.launch(intent);
    }

    private void renderSelectedIcon() {
        if (binding == null) return;
        if (TextUtils.isEmpty(selectedIconUrl)) {
            binding.ivWishIcon.setImageResource(R.drawable.ic_piggy_bank);
        } else {
            ImageLoaderUtils.load(requireContext(), selectedIconUrl, binding.ivWishIcon,
                    R.drawable.ic_piggy_bank, R.drawable.ic_piggy_bank);
        }
        if (TextUtils.isEmpty(selectedIconBackgroundColor)) {
            binding.iconPicker.setBackgroundResource(R.drawable.bg_wish_icon_picker);
            return;
        }
        try {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Color.parseColor(selectedIconBackgroundColor));
            background.setStroke(dpToPx(2), Color.parseColor("#C7DCF5"));
            binding.iconPicker.setBackground(background);
        } catch (IllegalArgumentException ignored) {
            binding.iconPicker.setBackgroundResource(R.drawable.bg_wish_icon_picker);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_SELECTED_DATE, selectedDate.getTime());
        outState.putString(STATE_SELECTED_ICON, selectedIconUrl);
        outState.putString(STATE_SELECTED_ICON_COLOR, selectedIconBackgroundColor);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            View sheet = ((BottomSheetDialog) getDialog())
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                applySystemBarColors();
                sheet.setBackgroundResource(android.R.color.transparent);
                sheet.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.requestLayout();
                sheet.post(() -> applyAdaptiveSheetHeight(sheet));
            }
        }
    }

    private void applySystemBarColors() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        android.view.Window window = getDialog().getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#EEF6FF"));
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(
                window, window.getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
    }

    private void applyAdaptiveSheetHeight(View sheet) {
        if (binding == null) return;
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.92f);
        View content = binding.formScroll.getChildAt(0);
        int contentHeight = content == null ? binding.formScroll.getMeasuredHeight()
                : content.getMeasuredHeight();
        int desiredHeight = Math.min(Math.max(contentHeight, dpToPx(420)), maxHeight);
        ViewGroup.LayoutParams layoutParams = sheet.getLayoutParams();
        layoutParams.height = desiredHeight;
        sheet.setLayoutParams(layoutParams);

        // 内容较少时按内容展示，较多时由内部 NestedScrollView 承担滚动。
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setPeekHeight(desiredHeight);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }

    private String textOf(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
