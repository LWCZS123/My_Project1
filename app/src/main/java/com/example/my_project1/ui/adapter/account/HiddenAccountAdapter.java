package com.example.my_project1.ui.adapter.account;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.ItemSubAccountBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiddenAccountAdapter extends RecyclerView.Adapter<HiddenAccountAdapter.ViewHolder> {

    private final List<Account> accounts = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount, boolean isMultiSelectMode);
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public void setAccounts(List<Account> newAccounts) {
        accounts.clear();
        if (newAccounts != null) accounts.addAll(newAccounts);
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void setMultiSelectMode(boolean enabled) {
        this.isMultiSelectMode = enabled;
        if (!enabled) selectedPositions.clear();
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size(), isMultiSelectMode);
        }
    }

    public void selectAll(boolean select) {
        selectedPositions.clear();
        if (select) {
            for (int i = 0; i < accounts.size(); i++) selectedPositions.add(i);
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size(), isMultiSelectMode);
        }
    }

    public List<Account> getSelectedAccounts() {
        List<Account> selected = new ArrayList<>();
        for (int pos : selectedPositions) {
            selected.add(accounts.get(pos));
        }
        return selected;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSubAccountBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(accounts.get(position), position);
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubAccountBinding binding;

        ViewHolder(ItemSubAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account account, int position) {
            binding.tvName.setText(account.getName());
            binding.tvSubtitle.setText(account.getRemark());
            binding.tvAmount.setText(String.format("¥%.2f", account.getBalance()));
            
            if (account.getIconUrl() != null) {
                ImageLoaderUtils.load(binding.getRoot().getContext(), account.getIconUrl(), binding.ivIcon);
            }

            // Selection UI
            boolean isSelected = selectedPositions.contains(position);
            binding.contentView.setBackgroundResource(isSelected ? 
                    R.color.background_color : android.R.color.white);

            binding.contentView.setOnClickListener(v -> {
                if (isMultiSelectMode) {
                    if (selectedPositions.contains(position)) {
                        selectedPositions.remove(position);
                    } else {
                        selectedPositions.add(position);
                    }
                    notifyItemChanged(position);
                    if (selectionChangeListener != null) {
                        selectionChangeListener.onSelectionChanged(selectedPositions.size(), isMultiSelectMode);
                    }
                }
            });

            binding.contentView.setOnLongClickListener(v -> {
                if (!isMultiSelectMode) {
                    setMultiSelectMode(true);
                    selectedPositions.add(position);
                    notifyItemChanged(position);
                    return true;
                }
                return false;
            });
            
            // Disable swipe in hidden account list to simplify multi-select
            binding.swipeLayout.setSwipeEnable(false);
        }
    }
}
