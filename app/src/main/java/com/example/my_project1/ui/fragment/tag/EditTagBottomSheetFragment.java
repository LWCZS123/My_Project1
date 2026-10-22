package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.databinding.FragmentEditTagBottomSheetBinding;
import com.example.my_project1.ui.dialog.CustomColorPickerDialog;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.example.my_project1.utils.BottomSheetNavigationBarUtils;
import com.example.my_project1.utils.TagColorUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class EditTagBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_TAG = "arg_tag";
    private static final String ARG_GROUP_ID = "arg_group_id";

    private FragmentEditTagBottomSheetBinding binding;
    private TagViewModel tagViewModel;

    private TagEntity editingTag;
    private long preselectedGroupId = -1;

    private long selectedGroupId = -1;
    private String selectedBgColor = "#FFF3E0";
    private String selectedTextColor = "#FF9800";
    private boolean isCustomColor = false;

    public static EditTagBottomSheetFragment newInstance() {
        return new EditTagBottomSheetFragment();
    }

    public static EditTagBottomSheetFragment newInstance(TagEntity tag) {
        EditTagBottomSheetFragment fragment = new EditTagBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    public static EditTagBottomSheetFragment newInstanceForGroup(long groupId) {
        EditTagBottomSheetFragment fragment = new EditTagBottomSheetFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        if (getArguments() != null) {
            editingTag = (TagEntity) getArguments().getSerializable(ARG_TAG);
            preselectedGroupId = getArguments().getLong(ARG_GROUP_ID, -1);
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
        binding = FragmentEditTagBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tagViewModel = new ViewModelProvider(requireActivity()).get(TagViewModel.class);

        initViews();
        loadGroupChips();
        loadColorPalette();
    }

    private void initViews() {
        if (editingTag != null) {
            binding.tvTitleHeader.setText("修改标签");
            binding.etTagName.setText(editingTag.getName());
            if (editingTag.getName() != null) {
                binding.etTagName.setSelection(editingTag.getName().length());
                binding.tvPreviewName.setText(editingTag.getName());
                binding.ivClear.setVisibility(View.VISIBLE);
            }
            selectedGroupId = editingTag.getGroupId();
            if (editingTag.getBgColor() != null) {
                selectedBgColor = editingTag.getBgColor();
            }
            if (editingTag.getTextColor() != null) {
                selectedTextColor = editingTag.getTextColor();
            } else {
                selectedTextColor = TagColorUtils.calculateTextColor(selectedBgColor);
            }
        } else {
            binding.tvTitleHeader.setText("添加标签");
            selectedGroupId = preselectedGroupId;
            List<TagColorUtils.ColorOption> presets = TagColorUtils.getPresetColors();
            if (!presets.isEmpty()) {
                selectedBgColor = presets.get(0).bgColor;
                selectedTextColor = presets.get(0).textColor;
            }
        }

        updatePreviewColor();

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnComplete.setOnClickListener(v -> saveTag());
        binding.btnSaveTag.setOnClickListener(v -> saveTag());

        binding.ivClear.setOnClickListener(v -> {
            binding.etTagName.setText("");
            binding.tvPreviewName.setText("预览标签");
        });

        binding.etTagName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s != null ? s.length() : 0;
                binding.ivClear.setVisibility(len > 0 ? View.VISIBLE : View.GONE);
                if (len > 0) {
                    binding.tvPreviewName.setText(s.toString());
                } else {
                    binding.tvPreviewName.setText("预览标签");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnModeSystem.setOnClickListener(v -> {
            isCustomColor = false;
            binding.btnModeSystem.setBackgroundResource(R.drawable.bg_button_blue_rounded);
            binding.btnModeSystem.setTextColor(Color.WHITE);
            binding.btnModeCustom.setBackground(null);
            binding.btnModeCustom.setTextColor(Color.parseColor("#666666"));
            loadColorPalette();
        });

        binding.btnModeCustom.setOnClickListener(v -> {
            isCustomColor = true;
            binding.btnModeCustom.setBackgroundResource(R.drawable.bg_button_blue_rounded);
            binding.btnModeCustom.setTextColor(Color.WHITE);
            binding.btnModeSystem.setBackground(null);
            binding.btnModeSystem.setTextColor(Color.parseColor("#666666"));
            showCustomColorDialog();
        });
    }

    private void updatePreviewColor() {
        if (binding == null) return;
        try {
            int bg = Color.parseColor(selectedBgColor);
            int txt = Color.parseColor(selectedTextColor);
            binding.cardTagPreview.setCardBackgroundColor(bg);
            binding.ivTagPreviewIcon.setColorFilter(txt);
            binding.tvPreviewName.setTextColor(txt);
        } catch (Exception e) {
            binding.cardTagPreview.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
            binding.ivTagPreviewIcon.setColorFilter(Color.parseColor("#FF9800"));
            binding.tvPreviewName.setTextColor(Color.parseColor("#FF9800"));
        }
    }

    private void loadGroupChips() {
        tagViewModel.getActiveGroups().observe(getViewLifecycleOwner(), groups -> {
            if (binding == null || binding.llGroupChips == null) return;
            binding.llGroupChips.removeAllViews();
            if (groups == null || groups.isEmpty()) return;

            if (selectedGroupId <= 0) {
                if (preselectedGroupId > 0) {
                    selectedGroupId = preselectedGroupId;
                } else {
                    selectedGroupId = groups.get(0).getId();
                }
            }

            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (TagGroupEntity group : groups) {
                TextView chip = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, binding.llGroupChips, false);
                chip.setText(group.getName());
                chip.setTextSize(13f);
                chip.setPadding(36, 16, 36, 16);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(20);
                chip.setLayoutParams(lp);

                boolean isSelected = group.getId() == selectedGroupId;
                if (isSelected) {
                    chip.setBackgroundResource(R.drawable.bg_button_blue_rounded);
                    chip.setTextColor(Color.WHITE);
                } else {
                    chip.setBackgroundResource(R.drawable.circle_bg_white);
                    chip.setTextColor(Color.parseColor("#666666"));
                }

                chip.setOnClickListener(v -> {
                    selectedGroupId = group.getId();
                    loadGroupChips();
                });

                binding.llGroupChips.addView(chip);
            }
        });
    }

    private void loadColorPalette() {
        if (binding == null || binding.llColorPalette == null) return;
        binding.llColorPalette.removeAllViews();
        List<TagColorUtils.ColorOption> presets = TagColorUtils.getPresetColors();
        int density = (int) getResources().getDisplayMetrics().density;
        int size = 42 * density;

        // 1st Item: Rainbow Color Wheel Circle Icon
        MaterialCardView rainbowCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams rainbowLp = new LinearLayout.LayoutParams(size, size);
        rainbowLp.setMarginEnd(12 * density);
        rainbowCard.setLayoutParams(rainbowLp);
        rainbowCard.setRadius(size / 2f);
        rainbowCard.setCardElevation(0);
        rainbowCard.setStrokeWidth(0);

        ImageView ivRainbow = new ImageView(requireContext());
        ivRainbow.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Drawable rainbowDrawable = TagColorUtils.getRainbowDrawable(requireContext(), size);
        ivRainbow.setImageDrawable(rainbowDrawable);
        rainbowCard.addView(ivRainbow);

        if (isCustomColor) {
            rainbowCard.setStrokeColor(Color.parseColor("#2196F3"));
            rainbowCard.setStrokeWidth(3 * density);
        }

        rainbowCard.setOnClickListener(v -> {
            isCustomColor = true;
            binding.btnModeCustom.setBackgroundResource(R.drawable.bg_button_blue_rounded);
            binding.btnModeCustom.setTextColor(Color.WHITE);
            binding.btnModeSystem.setBackground(null);
            binding.btnModeSystem.setTextColor(Color.parseColor("#666666"));
            showCustomColorDialog();
        });

        binding.llColorPalette.addView(rainbowCard);

        // Preset Colors
        for (TagColorUtils.ColorOption option : presets) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(12 * density);
            card.setLayoutParams(lp);
            card.setRadius(size / 2f);
            card.setCardElevation(0);

            try {
                card.setCardBackgroundColor(Color.parseColor(option.bgColor));
            } catch (Exception e) {
                card.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
            }

            boolean isSelected = !isCustomColor && option.bgColor.equalsIgnoreCase(selectedBgColor);
            if (isSelected) {
                try {
                    card.setStrokeColor(Color.parseColor(option.textColor));
                } catch (Exception e) {
                    card.setStrokeColor(Color.parseColor("#2196F3"));
                }
                card.setStrokeWidth(3 * density);
            } else {
                card.setStrokeWidth(0);
            }

            card.setOnClickListener(v -> {
                isCustomColor = false;
                binding.btnModeSystem.setBackgroundResource(R.drawable.bg_button_blue_rounded);
                binding.btnModeSystem.setTextColor(Color.WHITE);
                binding.btnModeCustom.setBackground(null);
                binding.btnModeCustom.setTextColor(Color.parseColor("#666666"));

                selectedBgColor = option.bgColor;
                selectedTextColor = option.textColor;
                updatePreviewColor();
                loadColorPalette();
            });

            binding.llColorPalette.addView(card);
        }
    }

    private void showCustomColorDialog() {
        CustomColorPickerDialog dialog = CustomColorPickerDialog.newInstance(selectedBgColor);
        dialog.setOnColorSelectedListener(hexColor -> {
            selectedBgColor = hexColor;
            selectedTextColor = TagColorUtils.calculateTextColor(selectedBgColor);
            isCustomColor = true;
            updatePreviewColor();
            loadColorPalette();
        });
        dialog.show(getChildFragmentManager(), "CustomColorPickerDialog");
    }

    private void saveTag() {
        String name = (binding != null && binding.etTagName.getText() != null) ? binding.etTagName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "请输入标签名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingTag == null) {
            editingTag = new TagEntity();
            editingTag.setUserId(tagViewModel.getCurrentUserId());
        }

        if (selectedTextColor == null || selectedTextColor.trim().isEmpty()) {
            selectedTextColor = TagColorUtils.calculateTextColor(selectedBgColor);
        }

        editingTag.setName(name);
        editingTag.setGroupId(selectedGroupId);
        editingTag.setBgColor(selectedBgColor);
        editingTag.setTextColor(selectedTextColor);

        tagViewModel.saveTag(editingTag);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
