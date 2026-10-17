package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.ui.activity.SearchActivity;
import com.example.my_project1.ui.fragment.DeleteConfirmDialogFragment;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class TagActionBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TAG = "arg_tag";
    private static final String ARG_GROUP = "arg_group";

    private TagEntity tag;
    private TagGroupEntity group;
    private TagViewModel tagViewModel;

    private TextView tvTagTitle;
    private TextView btnActionEdit;
    private TextView btnActionSort;
    private TextView btnActionMoveGroup;
    private TextView btnActionViewBills;
    private TextView btnActionArchive;
    private TextView btnActionDelete;

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
        return inflater.inflate(R.layout.fragment_tag_action_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        tvTagTitle = view.findViewById(R.id.tv_tag_title);
        btnActionEdit = view.findViewById(R.id.btn_action_edit);
        btnActionSort = view.findViewById(R.id.btn_action_sort);
        btnActionMoveGroup = view.findViewById(R.id.btn_action_move_group);
        btnActionViewBills = view.findViewById(R.id.btn_action_view_bills);
        btnActionArchive = view.findViewById(R.id.btn_action_archive);
        btnActionDelete = view.findViewById(R.id.btn_action_delete);

        if (tag != null) {
            setupForTag();
        } else if (group != null) {
            setupForGroup();
        } else {
            dismiss();
        }
    }

    private void setupForTag() {
        if (tvTagTitle != null) tvTagTitle.setText(tag.getName());
        if (btnActionEdit != null) btnActionEdit.setText("修改");
        if (btnActionSort != null) btnActionSort.setText("排序");
        if (btnActionMoveGroup != null) btnActionMoveGroup.setText("移动分组");
        if (btnActionArchive != null) btnActionArchive.setText(tag.isArchived() ? "取消归档" : "归档");

        if (btnActionEdit != null) {
            btnActionEdit.setOnClickListener(v -> {
                dismiss();
                EditTagBottomSheetFragment editFragment = EditTagBottomSheetFragment.newInstance(tag);
                editFragment.show(getParentFragmentManager(), "EditTagBottomSheetFragment");
            });
        }

        if (btnActionSort != null) {
            btnActionSort.setOnClickListener(v -> {
                dismiss();
                tagViewModel.sortTagsInGroup(tag.getGroupId());
            });
        }

        if (btnActionMoveGroup != null) btnActionMoveGroup.setOnClickListener(v -> showMoveGroupDialog());

        if (btnActionViewBills != null) {
            btnActionViewBills.setOnClickListener(v -> {
                dismiss();
                Intent intent = new Intent(requireContext(), SearchActivity.class);
                intent.putExtra("search_query", tag.getName());
                startActivity(intent);
            });
        }

        if (btnActionArchive != null) {
            btnActionArchive.setOnClickListener(v -> {
                dismiss();
                tagViewModel.toggleTagArchive(tag.getId(), !tag.isArchived());
            });
        }

        if (btnActionDelete != null) {
            btnActionDelete.setOnClickListener(v -> {
                dismiss();
                showDeleteConfirmDialog();
            });
        }
    }

    private void setupForGroup() {
        if (tvTagTitle != null) tvTagTitle.setText(group.getName());
        if (btnActionEdit != null) btnActionEdit.setText("修改分组");
        if (btnActionSort != null) btnActionSort.setText("标签排序");
        if (btnActionMoveGroup != null) btnActionMoveGroup.setText("新建标签");

        if (btnActionViewBills != null) btnActionViewBills.setVisibility(View.GONE);
        if (btnActionArchive != null) btnActionArchive.setVisibility(View.GONE);

        if (btnActionEdit != null) {
            btnActionEdit.setOnClickListener(v -> {
                dismiss();
                AddTagGroupBottomSheetFragment addGroupFragment = AddTagGroupBottomSheetFragment.newInstance(group);
                addGroupFragment.show(getParentFragmentManager(), "AddTagGroupBottomSheetFragment");
            });
        }

        if (btnActionSort != null) {
            btnActionSort.setOnClickListener(v -> {
                dismiss();
                tagViewModel.sortTagsInGroup(group.getId());
            });
        }

        if (btnActionMoveGroup != null) {
            btnActionMoveGroup.setOnClickListener(v -> {
                dismiss();
                EditTagBottomSheetFragment editTagFragment = EditTagBottomSheetFragment.newInstanceForGroup(group.getId());
                editTagFragment.show(getParentFragmentManager(), "EditTagBottomSheetFragment");
            });
        }

        if (group.isDefault()) {
            if (btnActionDelete != null) btnActionDelete.setVisibility(View.GONE);
        } else {
            if (btnActionDelete != null) {
                btnActionDelete.setText("删除分组");
                btnActionDelete.setOnClickListener(v -> {
                    dismiss();
                    showDeleteGroupConfirmDialog();
                });
            }
        }
    }

    private void showMoveGroupDialog() {
        tagViewModel.getActiveGroups().observe(getViewLifecycleOwner(), groups -> {
            if (groups == null || groups.isEmpty()) return;
            List<String> names = new ArrayList<>();
            int currentGroupIndex = -1;
            for (int i = 0; i < groups.size(); i++) {
                TagGroupEntity g = groups.get(i);
                names.add(g.getName());
                if (g.getId() == tag.getGroupId()) {
                    currentGroupIndex = i;
                }
            }

            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择要移动到的分组")
                    .setSingleChoiceItems(items, currentGroupIndex, (dialog, which) -> {
                        TagGroupEntity selectedGroup = groups.get(which);
                        tagViewModel.moveTagGroup(tag.getId(), selectedGroup.getId());
                        dialog.dismiss();
                        dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
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
}
