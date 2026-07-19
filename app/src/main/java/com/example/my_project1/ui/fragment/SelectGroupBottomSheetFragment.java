package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentSelectGroupBottomSheetBinding;
import com.example.my_project1.ui.adapter.account.GroupAdapter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import io.reactivex.annotations.NonNull;

public class SelectGroupBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_LIST = "group_list";
    private static final String ARG_SELECTED_ID = "selected_id";
    private static final String ARG_IS_DELETE_MODE = "is_delete_mode";
    private static final String ARG_FILTER_CUSTOM = "filter_custom"; // 🔴 新增过滤标志

    private FragmentSelectGroupBottomSheetBinding binding;

    private List<AccountGroup> groupList;
    private String selectedGroupId;

    private OnGroupSelectedListener listener;

    public interface OnGroupSelectedListener {
        void onGroupSelected(AccountGroup group);
    }

    public void setOnGroupSelectedListener(OnGroupSelectedListener listener) {
        this.listener = listener;
    }
    public static SelectGroupBottomSheetFragment newInstance(List<AccountGroup> list,
                                                             String selectId) {
        return newInstance(list, selectId, false, false);
    }

    public static SelectGroupBottomSheetFragment newInstance(List<AccountGroup> list,
                                                             String selectId,
                                                             boolean isDeleteMode) {
        return newInstance(list, selectId, isDeleteMode, false);
    }

    public static SelectGroupBottomSheetFragment newInstance(List<AccountGroup> list,
                                                             String selectId,
                                                             boolean isDeleteMode,
                                                             boolean filterCustom) {
        SelectGroupBottomSheetFragment fragment = new SelectGroupBottomSheetFragment();
        Bundle b = new Bundle();
        b.putSerializable(ARG_LIST, (java.io.Serializable) list);
        b.putString(ARG_SELECTED_ID, selectId);
        b.putBoolean(ARG_IS_DELETE_MODE, isDeleteMode);
        b.putBoolean(ARG_FILTER_CUSTOM, filterCustom);
        fragment.setArguments(b);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSelectGroupBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupList = (List<AccountGroup>) getArguments().getSerializable(ARG_LIST);
        selectedGroupId = getArguments().getString(ARG_SELECTED_ID);
        boolean isDeleteMode = getArguments().getBoolean(ARG_IS_DELETE_MODE, false);
        boolean filterCustom = getArguments().getBoolean(ARG_FILTER_CUSTOM, false);

        // 🔴 逻辑优化：过滤逻辑
        if (groupList != null) {
            java.util.List<AccountGroup> filtered = new java.util.ArrayList<>();
            for (AccountGroup g : groupList) {
                // 1. 如果开启了 filterCustom，排除默认分组
                if (filterCustom && isDefaultGroupName(g.getName())) {
                    continue;
                }
                // 2. 🔑 排除自身：移动账户时排除账户当前所属的分组
                if (selectedGroupId != null && selectedGroupId.equals(g.getObjectId())) {
                    continue;
                }
                filtered.add(g);
            }
            groupList = filtered;
        }

        GroupAdapter adapter = new GroupAdapter(groupList);
        binding.recyclerGroup.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerGroup.setAdapter(adapter);

        adapter.setSelectedGroup(selectedGroupId);

        if (isDeleteMode) {
            binding.cardWarn.setVisibility(View.VISIBLE);
        } else {
            binding.cardWarn.setVisibility(View.GONE);
        }
        
        // 默认隐藏提示和按钮
        binding.btnCancel.setVisibility(View.GONE);
        binding.btnConfirm.setVisibility(View.GONE);

        adapter.setOnGroupSelectListener(group -> {
            if (listener != null) listener.onGroupSelected(group);
            dismiss();
        });
    }

    private boolean isDefaultGroupName(String name) {
        return "资金账户".equals(name) || "信用账户".equals(name) || "充值账户".equals(name);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(dlg -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {

                // 设置圆角背景
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );

                // 一打开就全展开
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return dialog;
    }




    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
