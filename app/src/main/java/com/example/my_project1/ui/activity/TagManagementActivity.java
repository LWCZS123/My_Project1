package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.databinding.ActivityTagManagementBinding;
import com.example.my_project1.databinding.LayoutAddTagPopupBinding;
import com.example.my_project1.ui.adapter.tag.TagGroupAdapter;
import com.example.my_project1.ui.fragment.tag.AddTagGroupBottomSheetFragment;
import com.example.my_project1.ui.fragment.tag.EditTagBottomSheetFragment;
import com.example.my_project1.ui.fragment.tag.TagActionBottomSheetFragment;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;

public class TagManagementActivity extends AppCompatActivity {

    public static final String PREF_TAGS_ENABLED = "pref_tags_enabled";

    private ActivityTagManagementBinding binding;
    private TagViewModel tagViewModel;
    private TagGroupAdapter adapter;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 状态栏保持和背景色一致（透明+深色图标）
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(true);
        }

        binding = ActivityTagManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, top, 0, 0);
            return insets;
        });

        sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE);

        initViews();
        initViewModel();
    }

    private void initViews() {
        binding.ivBack.setOnClickListener(v -> finish());

        boolean isEnabled = sharedPreferences.getBoolean(PREF_TAGS_ENABLED, true);
        binding.switchEnableTags.setChecked(isEnabled);
        binding.switchEnableTags.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(PREF_TAGS_ENABLED, isChecked).apply();
            Toast.makeText(this, isChecked ? "已开启标签" : "已关闭标签", Toast.LENGTH_SHORT).show();
        });

        binding.btnAdd.setOnClickListener(this::showAddMenuPopupWindow);

        adapter = new TagGroupAdapter();
        adapter.setOnTagGroupClickListener(new TagGroupAdapter.OnTagGroupClickListener() {
            @Override
            public void onTagClick(TagEntity tag) {
                TagActionBottomSheetFragment fragment = TagActionBottomSheetFragment.newInstance(tag);
                fragment.show(getSupportFragmentManager(), "TagActionBottomSheetFragment");
            }

            @Override
            public void onAddTagClick(TagGroupEntity group) {
                EditTagBottomSheetFragment fragment = EditTagBottomSheetFragment.newInstanceForGroup(group.getId());
                fragment.show(getSupportFragmentManager(), "EditTagBottomSheetFragment");
            }

            @Override
            public void onGroupFoldToggle(TagGroupEntity group, boolean isFolded) {
                tagViewModel.updateGroupFoldState(group.getId(), isFolded);
            }

            @Override
            public void onGroupMoreClick(TagGroupEntity group, View anchorView) {
                TagActionBottomSheetFragment fragment = TagActionBottomSheetFragment.newInstance(group);
                fragment.show(getSupportFragmentManager(), "TagActionBottomSheetFragment");
            }
        });

        binding.rvTagGroups.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTagGroups.setAdapter(adapter);

        binding.tvArchivedTags.setOnClickListener(v -> {
            Intent intent = new Intent(this, ArchivedTagsActivity.class);
            startActivity(intent);
        });
    }

    private void initViewModel() {
        tagViewModel = new ViewModelProvider(this).get(TagViewModel.class);

        tagViewModel.getToastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        tagViewModel.getGroupsWithTags().observe(this, groups -> {
            if (groups != null) {
                adapter.setGroups(groups);
            }
        });
    }

    private void showAddMenuPopupWindow(View anchorView) {
        LayoutAddTagPopupBinding popupBinding = LayoutAddTagPopupBinding.inflate(getLayoutInflater());

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.setOutsideTouchable(true);

        popupBinding.tvAddTag.setOnClickListener(v -> {
            popupWindow.dismiss();
            EditTagBottomSheetFragment fragment = EditTagBottomSheetFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "EditTagBottomSheetFragment");
        });

        popupBinding.tvAddGroup.setOnClickListener(v -> {
            popupWindow.dismiss();
            AddTagGroupBottomSheetFragment fragment = AddTagGroupBottomSheetFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "AddTagGroupBottomSheetFragment");
        });

        popupBinding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupBinding.getRoot().getMeasuredWidth();
        int density = (int) getResources().getDisplayMetrics().density;
        int xOffset = anchorView.getWidth() - popupWidth;
        popupWindow.showAsDropDown(anchorView, xOffset, 8 * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
