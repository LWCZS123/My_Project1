package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.databinding.FragmentSelectTagBottomSheetBinding;
import com.example.my_project1.databinding.ItemTagChipBinding;
import com.example.my_project1.databinding.ItemTagGroupBinding;
import com.example.my_project1.ui.activity.TagManagementActivity;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectTagBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_SELECTED_TAGS = "arg_selected_tags";

    private FragmentSelectTagBottomSheetBinding binding;
    private TagViewModel tagViewModel;

    private final Set<Long> selectedTagIds = new HashSet<>();
    private final List<TagEntity> selectedTagList = new ArrayList<>();
    private OnTagsSelectedListener listener;
    private String currentSearchQuery = "";

    private List<TagGroupWithTags> cachedGroupsWithTags;

    public interface OnTagsSelectedListener {
        void onTagsSelected(List<TagEntity> selectedTags);
    }

    public static SelectTagBottomSheetFragment newInstance(List<TagEntity> initialSelected) {
        SelectTagBottomSheetFragment fragment = new SelectTagBottomSheetFragment();
        Bundle args = new Bundle();
        if (initialSelected != null) {
            args.putSerializable(ARG_SELECTED_TAGS, new ArrayList<>(initialSelected));
        }
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnTagsSelectedListener(OnTagsSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        if (getArguments() != null) {
            List<TagEntity> initial = (List<TagEntity>) getArguments().getSerializable(ARG_SELECTED_TAGS);
            if (initial != null) {
                for (TagEntity tag : initial) {
                    if (tag != null) {
                        selectedTagIds.add(tag.getId());
                        selectedTagList.add(tag);
                    }
                }
            }
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
        binding = FragmentSelectTagBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.btnDone.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagsSelected(new ArrayList<>(selectedTagList));
            }
            dismiss();
        });

        binding.tvGoToTagManage.setOnClickListener(v -> {
            dismiss();
            startActivity(new Intent(requireContext(), TagManagementActivity.class));
        });

        binding.etSearchTag.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                renderTagGroups(cachedGroupsWithTags);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        tagViewModel.getGroupsWithTags().observe(getViewLifecycleOwner(), groups -> {
            cachedGroupsWithTags = groups;
            renderTagGroups(groups);
        });
    }

    private void renderTagGroups(List<TagGroupWithTags> groupsWithTags) {
        if (binding == null || binding.llSelectTagGroupsContainer == null) return;
        binding.llSelectTagGroupsContainer.removeAllViews();

        if (groupsWithTags == null || groupsWithTags.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("暂无可用标签，点击下方「标签管理」创建");
            tvEmpty.setTextColor(Color.parseColor("#999999"));
            tvEmpty.setTextSize(14f);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 40, 0, 40);
            binding.llSelectTagGroupsContainer.addView(tvEmpty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (TagGroupWithTags gwt : groupsWithTags) {
            TagGroupEntity group = gwt.getTagGroup();
            List<TagEntity> tags = gwt.getTags();

            List<TagEntity> matchingTags = new ArrayList<>();
            if (tags != null) {
                for (TagEntity tag : tags) {
                    if (tag.isArchived() || tag.isDeleted()) continue;
                    if (currentSearchQuery.isEmpty() || tag.getName().toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                        matchingTags.add(tag);
                    }
                }
            }

            if (!currentSearchQuery.isEmpty() && matchingTags.isEmpty()) {
                continue;
            }

            ItemTagGroupBinding groupBinding = ItemTagGroupBinding.inflate(inflater, binding.llSelectTagGroupsContainer, false);

            groupBinding.ivGroupMore.setVisibility(View.GONE);
            groupBinding.tvGroupTitle.setText(group.getName() + " (" + matchingTags.size() + ")");

            boolean isFolded = group.isFolded();
            groupBinding.cardGroupBody.setVisibility(isFolded ? View.GONE : View.VISIBLE);
            groupBinding.ivFoldArrow.setRotation(isFolded ? -90f : 0f);

            groupBinding.llGroupTitleContainer.setOnClickListener(v -> {
                boolean newFolded = groupBinding.cardGroupBody.getVisibility() == View.VISIBLE;
                group.setFolded(newFolded);
                groupBinding.cardGroupBody.setVisibility(newFolded ? View.GONE : View.VISIBLE);
                groupBinding.ivFoldArrow.setRotation(newFolded ? -90f : 0f);
                tagViewModel.updateGroupFoldState(group.getId(), newFolded);
            });

            groupBinding.flTags.removeAllViews();
            for (TagEntity tag : matchingTags) {
                ItemTagChipBinding chipBinding = ItemTagChipBinding.inflate(inflater, groupBinding.flTags, false);

                String name = tag.getName();
                if (name != null && !name.trim().startsWith("#")) {
                    name = "#" + name.trim();
                }
                chipBinding.tvTagName.setText(name);

                boolean isSelected = isTagSelected(tag);
                if (isSelected) {
                    chipBinding.cardTag.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                    chipBinding.cardTag.setStrokeColor(Color.parseColor("#2196F3"));
                    chipBinding.cardTag.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
                    chipBinding.tvTagName.setTextColor(Color.parseColor("#2196F3"));
                } else {
                    if (tag.getBgColor() != null && !tag.getBgColor().isEmpty()) {
                        try {
                            chipBinding.cardTag.setCardBackgroundColor(Color.parseColor(tag.getBgColor()));
                        } catch (Exception e) {
                            chipBinding.cardTag.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                        }
                    } else {
                        chipBinding.cardTag.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                    }
                    chipBinding.cardTag.setStrokeWidth(0);

                    if (tag.getTextColor() != null && !tag.getTextColor().isEmpty()) {
                        try {
                            chipBinding.tvTagName.setTextColor(Color.parseColor(tag.getTextColor()));
                        } catch (Exception e) {
                            chipBinding.tvTagName.setTextColor(Color.parseColor("#333333"));
                        }
                    } else {
                        chipBinding.tvTagName.setTextColor(Color.parseColor("#333333"));
                    }
                }

                chipBinding.getRoot().setOnClickListener(v -> {
                    if (isTagSelected(tag)) {
                        removeTagFromSelectedList(tag);
                    } else {
                        selectedTagIds.add(tag.getId());
                        selectedTagList.add(tag);
                    }
                    renderTagGroups(cachedGroupsWithTags);
                });

                groupBinding.flTags.addView(chipBinding.getRoot());
            }

            binding.llSelectTagGroupsContainer.addView(groupBinding.getRoot());
        }
    }

    private boolean isTagSelected(TagEntity tag) {
        if (tag == null) return false;
        if (selectedTagIds.contains(tag.getId())) return true;
        for (TagEntity sel : selectedTagList) {
            if (sel != null && tag.getName() != null && tag.getName().equalsIgnoreCase(sel.getName())) {
                return true;
            }
        }
        return false;
    }

    private void removeTagFromSelectedList(TagEntity tag) {
        if (tag == null) return;
        selectedTagIds.remove(tag.getId());
        for (int i = 0; i < selectedTagList.size(); i++) {
            TagEntity sel = selectedTagList.get(i);
            if (sel != null && (sel.getId() == tag.getId() || (sel.getName() != null && sel.getName().equalsIgnoreCase(tag.getName())))) {
                selectedTagList.remove(i);
                break;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
