package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.databinding.FragmentAddTagGroupBottomSheetBinding;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AddTagGroupBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_GROUP = "arg_group";

    private FragmentAddTagGroupBottomSheetBinding binding;
    private TagViewModel tagViewModel;
    private TagGroupEntity editingGroup;

    public static AddTagGroupBottomSheetFragment newInstance() {
        return new AddTagGroupBottomSheetFragment();
    }

    public static AddTagGroupBottomSheetFragment newInstance(TagGroupEntity group) {
        AddTagGroupBottomSheetFragment fragment = new AddTagGroupBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_GROUP, group);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        if (getArguments() != null) {
            editingGroup = (TagGroupEntity) getArguments().getSerializable(ARG_GROUP);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                if (bottomSheet.getParent() instanceof View) {
                    ((View) bottomSheet.getParent()).setBackgroundColor(Color.TRANSPARENT);
                }
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            BottomSheetNavigationBarUtils.setup(dialog);
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddTagGroupBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        if (editingGroup != null) {
            binding.tvTitleGroup.setText("修改分组");
            binding.etGroupName.setText(editingGroup.getName());
            if (editingGroup.getName() != null) {
                binding.etGroupName.setSelection(editingGroup.getName().length());
            }
            binding.ivClear.setVisibility(View.VISIBLE);
        } else {
            binding.tvTitleGroup.setText("创建新分组");
        }

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnComplete.setOnClickListener(v -> submit());

        binding.ivClear.setOnClickListener(v -> binding.etGroupName.setText(""));

        binding.etGroupName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.ivClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        binding.etGroupName.requestFocus();
    }

    private void submit() {
        String name = binding.etGroupName.getText() != null ? binding.etGroupName.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "请输入分组名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingGroup != null) {
            tagViewModel.updateGroupName(editingGroup, name);
        } else {
            tagViewModel.createGroup(name);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
