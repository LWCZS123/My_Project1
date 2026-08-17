package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.databinding.FragmentAddSavingRecordBinding;
import com.example.my_project1.ui.viewmodel.wish.WishViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

public class AddSavingRecordFragment extends BottomSheetDialogFragment {

    private FragmentAddSavingRecordBinding binding;
    private WishViewModel wishViewModel;
    private long wishId;

    public static AddSavingRecordFragment newInstance(Wish wish) {
        AddSavingRecordFragment f = new AddSavingRecordFragment();
        Bundle args = new Bundle();
        args.putLong("wish_id", wish.getId());
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            wishId = getArguments().getLong("wish_id", -1);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddSavingRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wishViewModel = new ViewModelProvider(requireActivity()).get(WishViewModel.class);
        
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.btnSave.setOnClickListener(v -> {
            // 保存存钱记录逻辑
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
