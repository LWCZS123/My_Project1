package com.example.my_project1.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class IconGridAdapter extends ListAdapter<String, IconGridAdapter.IconViewHolder> {

    private final Context context;
    private OnIconClickListener listener;

    public interface OnIconClickListener {
        void onIconClick(String iconUrl);
    }

    public void setOnIconClickListener(OnIconClickListener listener) {
        this.listener = listener;
    }

    public IconGridAdapter(Context context) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.context = context;
    }

    public void setData(List<String> data) {
        submitList(data);
    }

    @NonNull
    @Override
    public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_icon, parent, false);
        return new IconViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
        String iconUrl = getItem(position);

        ImageLoaderUtils.load(holder.itemView.getContext(),
                iconUrl, holder.iconImage);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onIconClick(iconUrl);
        });
    }

    static class IconViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImage;

        IconViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImage = itemView.findViewById(R.id.icon_image);
        }
    }
}
