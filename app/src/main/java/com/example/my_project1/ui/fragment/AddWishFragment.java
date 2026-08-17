package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.databinding.FragmentAddWishBinding;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

public class AddWishFragment extends BottomSheetDialogFragment {

    private FragmentAddWishBinding binding;
    private WishViewModel viewModel;
    private long editWishId = -1;

    public static AddWishFragment newInstance() {
        return new AddWishFragment();
    }

    public static AddWishFragment newInstanceForEdit(long wishId) {
        AddWishFragment fragment = new AddWishFragment();
        Bundle args = new Bundle();
        args.putLong("wish_id", wishId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            editWishId = getArguments().getLong("wish_id", -1);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddWishBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);
        
        setupClickListeners();
        if (editWishId != -1) {
            // 加载编辑数据
        }
    }

    private void setupClickListeners() {
        binding.btnSave.setOnClickListener(v -> {
            // 保存逻辑
            dismiss();
        });
        binding.tvCancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
