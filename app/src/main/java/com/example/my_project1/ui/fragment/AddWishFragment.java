package com.example.my_project1.ui.fragment;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.FragmentAddWishBinding;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * AddWishFragment - 新增愿望页面
 * -------------------------------------------------------
 * 功能:
 * - 填写愿望名称、目标金额、开始日期、备注
 * - 支持编辑模式与新增模式
 * - 修复逻辑：保存成功后自动 dismiss 弹窗
 */
public class AddWishFragment extends BottomSheetDialogFragment {

    private static final String TAG = "AddWishFragment";
    private static final String ARG_WISH_ID = "wish_id";

    private FragmentAddWishBinding binding;
    private WishViewModel viewModel;

    private String selectedIconUrl = null;
    private Date selectedDate = new Date();
    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());

    private long editWishId = -1;
    private Wish editingWish = null;

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
        if (getArguments() != null) {
            editWishId = getArguments().getLong(ARG_WISH_ID, -1);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddWishBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);

        initDefaultDate();
        setupClickListeners();
        observeViewModel();

        if (editWishId != -1) {
            fillEditData();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initDefaultDate() {
        selectedDate = new Date();
        binding.tvDate.setText(dateFormatter.format(selectedDate));
    }

    private void setupClickListeners() {
        binding.tvCancel.setOnClickListener(v -> dismiss());
        binding.layoutSelectIcon.setOnClickListener(v -> openIconPicker());
        binding.layoutDate.setOnClickListener(v -> openDatePicker());
        binding.btnSave.setOnClickListener(v -> onSaveClicked());
    }

    private void observeViewModel() {
        viewModel.operationState.observe(getViewLifecycleOwner(), response -> {
            if (response == null) return;

            switch (response.getStatus()) {
                case LOADING:
                    binding.btnSave.setEnabled(false);
                    binding.btnSave.setText("保存中...");
                    break;

                case SUCCESS:
                    binding.btnSave.setEnabled(true);
                    viewModel.resetOperationState();
                    break;

                case ERROR:
                    binding.btnSave.setEnabled(true);
                    binding.btnSave.setText(editWishId != -1 ? "保存修改" : "确定添加");
                    viewModel.resetOperationState();
                    break;

                default:
                    binding.btnSave.setEnabled(true);
                    break;
            }
        });
    }

    private void fillEditData() {
        viewModel.getAllWishes().observe(getViewLifecycleOwner(), wishes -> {
            if (wishes == null) return;
            for (Wish wish : wishes) {
                if (wish.getId() == editWishId) {
                    editingWish = wish;
                    populateFields(wish);
                    break;
                }
            }
        });
    }

    private void populateFields(Wish wish) {
        binding.etWishName.setText(wish.getWishName());
        binding.etAmount.setText(String.valueOf((int) wish.getTargetAmount()));
        binding.etRemark.setText(wish.getRemark());

        if (wish.getStartDate() != null) {
            selectedDate = wish.getStartDate();
            binding.tvDate.setText(dateFormatter.format(selectedDate));
        }

        if (!TextUtils.isEmpty(wish.getIconUrl())) {
            selectedIconUrl = wish.getIconUrl();
            Glide.with(this)
                    .load(wish.getIconUrl())
                    .placeholder(R.drawable.ic_face)
                    .into(binding.ivWishIcon);
        }
    }

    private void openIconPicker() {
        IconCategoryBottomSheet sheet = new IconCategoryBottomSheet();
        sheet.setOnIconSelectedListener(iconUrl -> {
            selectedIconUrl = iconUrl;
            Glide.with(this)
                    .load(iconUrl)
                    .placeholder(R.drawable.ic_face)
                    .into(binding.ivWishIcon);
        });
        sheet.show(getChildFragmentManager(), "IconPicker");
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);

        new DatePickerDialog(
                requireContext(),
                (picker, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    selectedDate = selected.getTime();
                    binding.tvDate.setText(dateFormatter.format(selectedDate));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void onSaveClicked() {
        if (!validateInput()) return;

        String wishName = binding.etWishName.getText().toString().trim();
        double targetAmount = Double.parseDouble(binding.etAmount.getText().toString().trim());
        String remark = binding.etRemark.getText().toString().trim();

        if (editWishId != -1 && editingWish != null) {
            editingWish.setWishName(wishName);
            editingWish.setTargetAmount(targetAmount);
            editingWish.setStartDate(selectedDate);
            editingWish.setRemark(remark);
            if (selectedIconUrl != null) {
                editingWish.setIconUrl(selectedIconUrl);
            }
            viewModel.updateWish(editingWish);
            dismiss();
        } else {
            Wish wish = new Wish();
            wish.setWishName(wishName);
            wish.setTargetAmount(targetAmount);
            wish.setCurrentAmount(0);
            wish.setStartDate(selectedDate);
            wish.setRemark(remark);
            wish.setIconUrl(selectedIconUrl);
            wish.setStatus(0);
            viewModel.insertWish(wish);
            dismiss();
        }
    }

    private boolean validateInput() {
        String name = binding.etWishName.getText().toString().trim();
        String amountStr = binding.etAmount.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.etWishName.setError("请输入愿望名称");
            return false;
        }
        if (TextUtils.isEmpty(amountStr)) {
            binding.etAmount.setError("请输入目标金额");
            return false;
        }
        try {
            if (Double.parseDouble(amountStr) <= 0) {
                binding.etAmount.setError("金额必须大于 0");
                return false;
            }
        } catch (NumberFormatException e) {
            binding.etAmount.setError("金额格式错误");
            return false;
        }
        return true;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }
}