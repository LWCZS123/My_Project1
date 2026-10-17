package com.example.my_project1.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.ui.fragment.DeleteConfirmDialogFragment;
import com.example.my_project1.ui.view.FlowLayout;
import com.example.my_project1.ui.viewmodel.tag.TagViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ArchivedTagsActivity extends AppCompatActivity {

    private TagViewModel tagViewModel;
    private TextView tvEmpty;
    private View cardContainer;
    private FlowLayout flArchivedTags;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_tags);

        ImageView ivBack = findViewById(R.id.iv_back);
        if (ivBack != null) ivBack.setOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tv_empty);
        cardContainer = findViewById(R.id.card_container);
        flArchivedTags = findViewById(R.id.fl_archived_tags);

        tagViewModel = new ViewModelProvider(this).get(TagViewModel.class);

        tagViewModel.getToastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        tagViewModel.getArchivedTags().observe(this, this::renderArchivedTags);
    }

    private void renderArchivedTags(List<TagEntity> tags) {
        if (flArchivedTags == null) return;
        flArchivedTags.removeAllViews();
        if (tags == null || tags.isEmpty()) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            if (cardContainer != null) cardContainer.setVisibility(View.GONE);
            return;
        }

        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (cardContainer != null) cardContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (TagEntity tag : tags) {
            View chipView = inflater.inflate(R.layout.item_tag_chip, flArchivedTags, false);
            MaterialCardView cardTag = chipView.findViewById(R.id.card_tag);
            TextView tvName = chipView.findViewById(R.id.tv_tag_name);

            tvName.setText(tag.getName());

            if (tag.getBgColor() != null && !tag.getBgColor().isEmpty()) {
                try {
                    cardTag.setCardBackgroundColor(Color.parseColor(tag.getBgColor()));
                } catch (Exception e) {
                    cardTag.setCardBackgroundColor(Color.parseColor("#EEEEEE"));
                }
            }

            if (tag.getTextColor() != null && !tag.getTextColor().isEmpty()) {
                try {
                    tvName.setTextColor(Color.parseColor(tag.getTextColor()));
                } catch (Exception e) {
                    tvName.setTextColor(Color.parseColor("#666666"));
                }
            }

            chipView.setOnClickListener(v -> showArchivedTagOptions(tag));

            flArchivedTags.addView(chipView);
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
}
