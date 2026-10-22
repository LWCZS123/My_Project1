package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.databinding.FragmentTagActionBottomSheetBinding;
import com.example.my_project1.ui.activity.SearchActivity;
import com.example.my_project1.ui.fragment.DeleteConfirmDialogFragment;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class TagActionBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TAG = "arg_tag";
    private static final String ARG_GROUP = "arg_group";

    private FragmentTagActionBottomSheetBinding binding;
    private TagEntity tag;
    private TagGroupEntity group;
    private TagViewModel tagViewModel;

    public static TagActionBottomSheetFragment newInstance(TagEntity tag) {
        TagActionBottomSheetFragment fragment = new TagActionBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    public static TagActionBottomSheetFragment newInstance(TagGroupEntity group) {
        TagActionBottomSheetFragment fragment = new TagActionBottomSheetFragment();
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
            tag = (TagEntity) getArguments().getSerializable(ARG_TAG);
            group = (TagGroupEntity) getArguments().getSerializable(ARG_GROUP);
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
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTagActionBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        if (tag != null) {
            setupForTag();
        } else if (group != null) {
            setupForGroup();
        } else {
            dismiss();
        }
    }

    private void setupForTag() {
        binding.tvTagTitle.setText(tag.getName());
        binding.btnActionEdit.setText("修改");
        binding.btnActionSort.setText("排序");
        binding.btnActionMoveGroup.setText("移动分组");
        binding.btnActionArchive.setText(tag.isArchived() ? "取消归档" : "归档");

        binding.btnActionEdit.setOnClickListener(v -> {
            dismiss();
            EditTagBottomSheetFragment editFragment = EditTagBottomSheetFragment.newInstance(tag);
            editFragment.show(getParentFragmentManager(), "EditTagBottomSheetFragment");
        });

        binding.btnActionSort.setOnClickListener(v -> {
            dismiss();
            tagViewModel.sortTagsInGroup(tag.getGroupId());
        });

        binding.btnActionMoveGroup.setOnClickListener(v -> showMoveGroupDialog());

        binding.btnActionViewBills.setOnClickListener(v -> {
            dismiss();
            Intent intent = new Intent(requireContext(), SearchActivity.class);
            intent.putExtra("search_tag_id", tag.getId());
            intent.putExtra("search_tag_name", tag.getName());
            startActivity(intent);
        });

        binding.btnActionArchive.setOnClickListener(v -> {
            dismiss();
            tagViewModel.toggleTagArchive(tag.getId(), !tag.isArchived());
        });

        binding.btnActionDelete.setOnClickListener(v -> {
            dismiss();
            showDeleteConfirmDialog();
        });
    }

    private void setupForGroup() {
        binding.tvTagTitle.setText(group.getName());
        binding.btnActionEdit.setText("修改分组");
        binding.btnActionSort.setText("标签排序");
        binding.btnActionMoveGroup.setText("新建标签");

        binding.btnActionViewBills.setVisibility(View.GONE);
        binding.btnActionArchive.setVisibility(View.GONE);

        binding.btnActionEdit.setOnClickListener(v -> {
            dismiss();
            AddTagGroupBottomSheetFragment addGroupFragment = AddTagGroupBottomSheetFragment.newInstance(group);
            addGroupFragment.show(getParentFragmentManager(), "AddTagGroupBottomSheetFragment");
        });

        binding.btnActionSort.setOnClickListener(v -> {
            dismiss();
            tagViewModel.sortTagsInGroup(group.getId());
        });

        binding.btnActionMoveGroup.setOnClickListener(v -> {
            dismiss();
            EditTagBottomSheetFragment editTagFragment = EditTagBottomSheetFragment.newInstanceForGroup(group.getId());
            editTagFragment.show(getParentFragmentManager(), "EditTagBottomSheetFragment");
        });

        if (group.isDefault()) {
            binding.btnActionDelete.setVisibility(View.GONE);
        } else {
            binding.btnActionDelete.setText("删除分组");
            binding.btnActionDelete.setOnClickListener(v -> {
                dismiss();
                showDeleteGroupConfirmDialog();
            });
        }
    }

    private void showMoveGroupDialog() {
        tagViewModel.getActiveGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups == null || groups.isEmpty()) return;
            SelectTagGroupBottomSheetFragment fragment = SelectTagGroupBottomSheetFragment.newInstance(
                    groups,
                    tag != null ? tag.getGroupId() : -1
            );
            fragment.setOnGroupSelectedListener(selectedGroup -> {
                tagViewModel.moveTagGroup(tag.getId(), selectedGroup.getId());
                dismiss();
            });
            fragment.show(getParentFragmentManager(), "SelectTagGroupBottomSheetFragment");
        });
    }

    private void showDeleteGroupConfirmDialog() {
        DeleteConfirmDialogFragment dialog = DeleteConfirmDialogFragment.newInstance(
                "删除分组",
                "确定删除分组「" + group.getName() + "」吗？关联标签将移至默认分组。"
        );
        dialog.setOnDeleteConfirmListener(() -> tagViewModel.deleteGroup(group));
        dialog.show(getParentFragmentManager(), "DeleteConfirmDialogFragment");
    }

    private void showDeleteConfirmDialog() {
        DeleteConfirmDialogFragment dialog = DeleteConfirmDialogFragment.newInstance(
                "删除标签",
                "确定要删除标签「" + tag.getName() + "」吗？"
        );
        dialog.setOnDeleteConfirmListener(() -> tagViewModel.deleteTag(tag.getId()));
        dialog.show(getParentFragmentManager(), "DeleteConfirmDialogFragment");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
