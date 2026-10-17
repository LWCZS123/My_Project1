package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.ui.activity.TagManagementActivity;
import com.example.my_project1.ui.view.FlowLayout;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectTagBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_SELECTED_TAGS = "arg_selected_tags";

    private TagViewModel tagViewModel;

    private final Set<Long> selectedTagIds = new HashSet<>();
    private final List<TagEntity> selectedTagList = new ArrayList<>();
    private OnTagsSelectedListener listener;
    private String currentSearchQuery = "";

    private LinearLayout llContainer;
    private EditText etSearch;
    private ImageView btnClose;
    private TextView btnDone;
    private TextView tvGoToTagManage;

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
        return inflater.inflate(R.layout.fragment_select_tag_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        btnClose = view.findViewById(R.id.btn_close);
        btnDone = view.findViewById(R.id.btn_done);
        tvGoToTagManage = view.findViewById(R.id.tv_go_to_tag_manage);
        etSearch = view.findViewById(R.id.et_search_tag);
        llContainer = view.findViewById(R.id.ll_select_tag_groups_container);

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTagsSelected(new ArrayList<>(selectedTagList));
                }
                dismiss();
            });
        }

        if (tvGoToTagManage != null) {
            tvGoToTagManage.setOnClickListener(v -> {
                dismiss();
                startActivity(new Intent(requireContext(), TagManagementActivity.class));
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
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
        }

        tagViewModel.getGroupsWithTags().observe(getViewLifecycleOwner(), groups -> {
            cachedGroupsWithTags = groups;
            renderTagGroups(groups);
        });
    }

    private void renderTagGroups(List<TagGroupWithTags> groupsWithTags) {
        if (llContainer == null) return;
        llContainer.removeAllViews();

        if (groupsWithTags == null || groupsWithTags.isEmpty()) {
            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("暂无可用标签，点击下方「标签管理」创建");
            tvEmpty.setTextColor(Color.parseColor("#999999"));
            tvEmpty.setTextSize(14f);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            tvEmpty.setPadding(0, 40, 0, 40);
            llContainer.addView(tvEmpty);
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

            View groupHeaderView = inflater.inflate(R.layout.item_tag_group, llContainer, false);
            TextView tvTitle = groupHeaderView.findViewById(R.id.tv_group_title);
            ImageView ivMore = groupHeaderView.findViewById(R.id.iv_group_more);
            ImageView ivFold = groupHeaderView.findViewById(R.id.iv_fold_arrow);
            View titleContainer = groupHeaderView.findViewById(R.id.ll_group_title_container);
            MaterialCardView cardBody = groupHeaderView.findViewById(R.id.card_group_body);
            FlowLayout flTags = groupHeaderView.findViewById(R.id.fl_tags);

            if (ivMore != null) ivMore.setVisibility(View.GONE);
            if (tvTitle != null) tvTitle.setText(group.getName() + " (" + matchingTags.size() + ")");

            boolean isFolded = group.isFolded();
            if (cardBody != null) cardBody.setVisibility(isFolded ? View.GONE : View.VISIBLE);
            if (ivFold != null) ivFold.setRotation(isFolded ? -90f : 0f);

            if (titleContainer != null) {
                titleContainer.setOnClickListener(v -> {
                    boolean newFolded = cardBody != null && cardBody.getVisibility() == View.VISIBLE;
                    group.setFolded(newFolded);
                    if (cardBody != null) cardBody.setVisibility(newFolded ? View.GONE : View.VISIBLE);
                    if (ivFold != null) ivFold.setRotation(newFolded ? -90f : 0f);
                    tagViewModel.updateGroupFoldState(group.getId(), newFolded);
                });
            }

            if (flTags != null) {
                flTags.removeAllViews();
                for (TagEntity tag : matchingTags) {
                    View chipView = inflater.inflate(R.layout.item_tag_chip, flTags, false);
                    MaterialCardView cardTag = chipView.findViewById(R.id.card_tag);
                    TextView tvName = chipView.findViewById(R.id.tv_tag_name);

                    if (tvName != null) tvName.setText(tag.getName());

                    boolean isSelected = isTagSelected(tag);
                    if (cardTag != null) {
                        if (isSelected) {
                            cardTag.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                            cardTag.setStrokeColor(Color.parseColor("#2196F3"));
                            cardTag.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
                            if (tvName != null) tvName.setTextColor(Color.parseColor("#2196F3"));
                        } else {
                            if (tag.getBgColor() != null && !tag.getBgColor().isEmpty()) {
                                try {
                                    cardTag.setCardBackgroundColor(Color.parseColor(tag.getBgColor()));
                                } catch (Exception e) {
                                    cardTag.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                                }
                            } else {
                                cardTag.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                            }
                            cardTag.setStrokeWidth(0);

                            if (tvName != null) {
                                if (tag.getTextColor() != null && !tag.getTextColor().isEmpty()) {
                                    try {
                                        tvName.setTextColor(Color.parseColor(tag.getTextColor()));
                                    } catch (Exception e) {
                                        tvName.setTextColor(Color.parseColor("#333333"));
                                    }
                                } else {
                                    tvName.setTextColor(Color.parseColor("#333333"));
                                }
                            }
                        }
                    }

                    chipView.setOnClickListener(v -> {
                        if (isTagSelected(tag)) {
                            removeTagFromSelectedList(tag);
                        } else {
                            selectedTagIds.add(tag.getId());
                            selectedTagList.add(tag);
                        }
                        renderTagGroups(cachedGroupsWithTags);
                    });

                    flTags.addView(chipView);
                }
            }

            llContainer.addView(groupHeaderView);
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
}
