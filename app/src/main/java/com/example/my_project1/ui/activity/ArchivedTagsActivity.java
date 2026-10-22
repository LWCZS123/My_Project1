package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.databinding.ActivityArchivedTagsBinding;
import com.example.my_project1.databinding.ItemTagChipBinding;
import com.example.my_project1.ui.fragment.DeleteConfirmDialogFragment;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;

import java.util.List;

public class ArchivedTagsActivity extends AppCompatActivity {

    private ActivityArchivedTagsBinding binding;
    private TagViewModel tagViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityArchivedTagsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.ivBack.setOnClickListener(v -> finish());

        tagViewModel = new ViewModelProvider(this).get(TagViewModel.class);

        tagViewModel.getToastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        tagViewModel.getArchivedTags().observe(this, this::renderArchivedTags);
    }

    private void renderArchivedTags(List<TagEntity> tags) {
        if (binding == null) return;
        binding.flArchivedTags.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.cardContainer.setVisibility(View.GONE);
            return;
        }

        binding.tvEmpty.setVisibility(View.GONE);
        binding.cardContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (TagEntity tag : tags) {
            ItemTagChipBinding chipBinding = ItemTagChipBinding.inflate(inflater, binding.flArchivedTags, false);

            String name = tag.getName();
            if (name != null && !name.trim().startsWith("#")) {
                name = "#" + name.trim();
            }
            chipBinding.tvTagName.setText(name);

            if (tag.getBgColor() != null && !tag.getBgColor().isEmpty()) {
                try {
                    chipBinding.cardTag.setCardBackgroundColor(Color.parseColor(tag.getBgColor()));
                } catch (Exception e) {
                    chipBinding.cardTag.setCardBackgroundColor(Color.parseColor("#EEEEEE"));
                }
            }

            if (tag.getTextColor() != null && !tag.getTextColor().isEmpty()) {
                try {
                    chipBinding.tvTagName.setTextColor(Color.parseColor(tag.getTextColor()));
                } catch (Exception e) {
                    chipBinding.tvTagName.setTextColor(Color.parseColor("#666666"));
                }
            }

            chipBinding.getRoot().setOnClickListener(v -> showArchivedTagOptions(tag));

            binding.flArchivedTags.addView(chipBinding.getRoot());
        }
    }

    private void showArchivedTagOptions(TagEntity tag) {
        String[] options = new String[]{"取消归档", "彻底删除"};
        new AlertDialog.Builder(this)
                .setTitle(tag.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        tagViewModel.toggleTagArchive(tag.getId(), false);
                    } else if (which == 1) {
                        DeleteConfirmDialogFragment confirmDialog = DeleteConfirmDialogFragment.newInstance(
                                "彻底删除标签",
                                "确定要彻底删除标签「" + tag.getName() + "」吗？"
                        );
                        confirmDialog.setOnDeleteConfirmListener(() -> tagViewModel.deleteTag(tag.getId()));
                        confirmDialog.show(getSupportFragmentManager(), "DeleteConfirmDialogFragment");
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
