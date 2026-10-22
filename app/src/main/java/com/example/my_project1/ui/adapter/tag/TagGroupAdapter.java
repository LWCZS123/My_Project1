package com.example.my_project1.ui.adapter.tag;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.tag.TagEntity;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.data.model.tag.TagGroupWithTags;
import com.example.my_project1.databinding.ItemAddTagChipBinding;
import com.example.my_project1.databinding.ItemTagChipBinding;
import com.example.my_project1.databinding.ItemTagGroupBinding;
import com.example.my_project1.ui.view.FlowLayout;

import java.util.ArrayList;
import java.util.List;

public class TagGroupAdapter extends RecyclerView.Adapter<TagGroupAdapter.ViewHolder> {

    private final List<TagGroupWithTags> groups = new ArrayList<>();
    private OnTagGroupClickListener listener;

    public interface OnTagGroupClickListener {
        void onTagClick(TagEntity tag);
        void onAddTagClick(TagGroupEntity group);
        void onGroupFoldToggle(TagGroupEntity group, boolean isFolded);
        void onGroupMoreClick(TagGroupEntity group, View anchorView);
    }

    public void setOnTagGroupClickListener(OnTagGroupClickListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<TagGroupWithTags> newGroups) {
        this.groups.clear();
        if (newGroups != null) {
            this.groups.addAll(newGroups);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTagGroupBinding binding = ItemTagGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TagGroupWithTags groupWithTags = groups.get(position);
        holder.bind(groupWithTags, listener);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTagGroupBinding binding;

        public ViewHolder(@NonNull ItemTagGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TagGroupWithTags groupWithTags, OnTagGroupClickListener listener) {
            TagGroupEntity group = groupWithTags.getTagGroup();
            List<TagEntity> tags = groupWithTags.getTags();

            int activeCount = 0;
            if (tags != null) {
                for (TagEntity t : tags) {
                    if (!t.isArchived() && !t.isDeleted()) {
                        activeCount++;
                    }
                }
            }

            binding.tvGroupTitle.setText(group.getName() + "(" + activeCount + ")");

            boolean isFolded = group.isFolded();
            binding.cardGroupBody.setVisibility(isFolded ? View.GONE : View.VISIBLE);
            binding.ivFoldArrow.setRotation(isFolded ? -90f : 0f);

            binding.llGroupTitleContainer.setOnClickListener(v -> {
                boolean newFolded = !group.isFolded();
                group.setFolded(newFolded);
                binding.cardGroupBody.setVisibility(newFolded ? View.GONE : View.VISIBLE);
                binding.ivFoldArrow.setRotation(newFolded ? -90f : 0f);
                if (listener != null) {
                    listener.onGroupFoldToggle(group, newFolded);
                }
            });

            binding.ivGroupMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupMoreClick(group, v);
                }
            });

            FlowLayout flTags = binding.flTags;
            flTags.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(flTags.getContext());

            if (tags != null) {
                for (TagEntity tag : tags) {
                    if (tag.isArchived() || tag.isDeleted()) continue;

                    ItemTagChipBinding chipBinding = ItemTagChipBinding.inflate(inflater, flTags, false);

                    String name = tag.getName();
                    if (name != null && !name.trim().startsWith("#")) {
                        name = "#" + name.trim();
                    }
                    chipBinding.tvTagName.setText(name);

                    if (tag.getBgColor() != null && !tag.getBgColor().isEmpty()) {
                        try {
                            chipBinding.cardTag.setCardBackgroundColor(Color.parseColor(tag.getBgColor()));
                        } catch (Exception e) {
                            chipBinding.cardTag.setCardBackgroundColor(Color.parseColor("#FFEEEF"));
                        }
                    }

                    if (tag.getTextColor() != null && !tag.getTextColor().isEmpty()) {
                        try {
                            chipBinding.tvTagName.setTextColor(Color.parseColor(tag.getTextColor()));
                        } catch (Exception e) {
                            chipBinding.tvTagName.setTextColor(Color.parseColor("#FF5252"));
                        }
                    }

                    chipBinding.getRoot().setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onTagClick(tag);
                        }
                    });

                    flTags.addView(chipBinding.getRoot());
                }
            }

            ItemAddTagChipBinding addChipBinding = ItemAddTagChipBinding.inflate(inflater, flTags, false);
            addChipBinding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddTagClick(group);
                }
            });
            flTags.addView(addChipBinding.getRoot());
        }
    }
}
