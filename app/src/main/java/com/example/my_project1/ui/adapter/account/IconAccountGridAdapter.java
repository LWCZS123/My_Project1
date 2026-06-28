package com.example.my_project1.ui.adapter.account;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.account.IconItem;
import com.example.my_project1.databinding.ItemIconAccountBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class IconAccountGridAdapter extends RecyclerView.Adapter<IconAccountGridAdapter.IconViewHolder> {

    private final Context context;
    private List<IconItem> icons = new ArrayList<>();
    private OnIconClickListener listener;

    public interface OnIconClickListener {
        void onIconClick(IconItem item);
    }

    public void setOnIconClickListener(OnIconClickListener listener) {
        this.listener = listener;
    }

    public IconAccountGridAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<IconItem> data) {
        this.icons = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIconAccountBinding binding = ItemIconAccountBinding.inflate(
                LayoutInflater.from(context), parent, false
        );
        return new IconViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
        IconItem item = icons.get(position);
        ImageLoaderUtils.load(holder.binding.getRoot().getContext(), item.getUrl(), holder.binding.iconImage);
        holder.binding.iconName.setText(item.getName());

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) listener.onIconClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return icons.size();
    }

    static class IconViewHolder extends RecyclerView.ViewHolder {
        ItemIconAccountBinding binding;

        IconViewHolder(@NonNull ItemIconAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
