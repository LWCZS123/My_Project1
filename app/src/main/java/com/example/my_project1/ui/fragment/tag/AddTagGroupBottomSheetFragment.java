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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AddTagGroupBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_GROUP = "arg_group";

    private TagViewModel tagViewModel;
    private TagGroupEntity editingGroup;

    private EditText etGroupName;
    private ImageView ivClear;

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
        return inflater.inflate(R.layout.fragment_add_tag_group_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        TextView tvTitleGroup = view.findViewById(R.id.tv_title_group);
        ImageView btnCancel = view.findViewById(R.id.btn_cancel);
        TextView btnComplete = view.findViewById(R.id.btn_complete);
        etGroupName = view.findViewById(R.id.et_group_name);
        ivClear = view.findViewById(R.id.iv_clear);

        if (editingGroup != null) {
            if (tvTitleGroup != null) tvTitleGroup.setText("修改分组");
            if (etGroupName != null) {
                etGroupName.setText(editingGroup.getName());
                if (editingGroup.getName() != null) {
                    etGroupName.setSelection(editingGroup.getName().length());
                }
            }
            if (ivClear != null) ivClear.setVisibility(View.VISIBLE);
        } else {
            if (tvTitleGroup != null) tvTitleGroup.setText("创建新分组");
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dismiss());
        if (btnComplete != null) btnComplete.setOnClickListener(v -> submit());

        if (ivClear != null) {
            ivClear.setOnClickListener(v -> {
                if (etGroupName != null) etGroupName.setText("");
            });
        }

        if (etGroupName != null) {
            etGroupName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (ivClear != null) {
                        ivClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
            etGroupName.requestFocus();
        }
    }

    private void submit() {
        String name = (etGroupName != null && etGroupName.getText() != null) ? etGroupName.getText().toString().trim() : "";

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
}
