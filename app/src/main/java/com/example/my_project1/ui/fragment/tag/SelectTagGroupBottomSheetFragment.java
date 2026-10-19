package com.example.my_project1.ui.fragment.tag;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.tag.TagGroupEntity;
import com.example.my_project1.databinding.FragmentSelectTagGroupBottomSheetBinding;
import com.example.my_project1.databinding.ItemGroupBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SelectTagGroupBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_LIST = "tag_group_list";
    private static final String ARG_SELECTED_ID = "selected_group_id";

    private FragmentSelectTagGroupBottomSheetBinding binding;
    private List<TagGroupEntity> groupList;
    private long selectedGroupId = -1;

    private OnTagGroupSelectedListener listener;

    public interface OnTagGroupSelectedListener {
        void onTagGroupSelected(TagGroupEntity group);
    }

    public void setOnGroupSelectedListener(OnTagGroupSelectedListener listener) {
        this.listener = listener;
    }

    public static SelectTagGroupBottomSheetFragment newInstance(List<TagGroupEntity> list, long selectedGroupId) {
        SelectTagGroupBottomSheetFragment fragment = new SelectTagGroupBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LIST, (Serializable) list);
        args.putLong(ARG_SELECTED_ID, selectedGroupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSelectTagGroupBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            groupList = (List<TagGroupEntity>) getArguments().getSerializable(ARG_LIST);
            selectedGroupId = getArguments().getLong(ARG_SELECTED_ID, -1);
        }

        if (groupList != null) {
            List<TagGroupEntity> filtered = new ArrayList<>();
            for (TagGroupEntity g : groupList) {
                if (g != null && g.getId() != selectedGroupId) {
                    filtered.add(g);
                }
            }
            groupList = filtered;
        }

        TagGroupAdapter adapter = new TagGroupAdapter(groupList, selectedGroupId, group -> {
            if (listener != null) {
                listener.onTagGroupSelected(group);
            }
            dismiss();
        });

        binding.recyclerGroup.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerGroup.setAdapter(adapter);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dlg -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
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

    private static class TagGroupAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<TagGroupAdapter.ViewHolder> {
        private final List<TagGroupEntity> list;
        private final long selectedId;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(TagGroupEntity group);
        }

        TagGroupAdapter(List<TagGroupEntity> list, long selectedId, OnItemClickListener listener) {
            this.list = list != null ? list : new ArrayList<>();
            this.selectedId = selectedId;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGroupBinding binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TagGroupEntity group = list.get(position);
            holder.binding.tvGroupName.setText(group.getName());
            holder.binding.ivGroupIcon.setVisibility(View.GONE);
            holder.binding.ivCheck.setVisibility(group.getId() == selectedId ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(group);
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ItemGroupBinding binding;

            ViewHolder(@NonNull ItemGroupBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
