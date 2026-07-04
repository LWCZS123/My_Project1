package com.example.my_project1.ui.adapter.icon;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;

import java.util.ArrayList;
import java.util.List;

public class IconSelectionColorAdapter extends RecyclerView.Adapter<IconSelectionColorAdapter.VH> {

    private List<String> colors = new ArrayList<>();
    private String selectedColor;
    private final OnColorClickListener listener;

    public interface OnColorClickListener {
        void onColorClick(String color);
    }

    public IconSelectionColorAdapter(OnColorClickListener listener) {
        this.listener = listener;
        initDefaultColors();
    }

    private void initDefaultColors() {
        colors.add("#EF5350"); // Red
        colors.add("#FF7043"); // Orange
        colors.add("#FFCA28"); // Yellow
        colors.add("#9CCC65"); // Green
        colors.add("#42A5F5"); // Blue
        colors.add("#5C6BC0"); // Indigo
        colors.add("#7E57C2"); // Purple
        colors.add("#AB47BC"); // Pink
        colors.add("#26A69A"); // Teal
        colors.add("#78909C"); // Blue Grey
        colors.add("#8D6E63"); // Brown
    }

    public void setSelectedColor(String color) {
        this.selectedColor = color;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selection_color, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String colorStr = colors.get(position);
        try {
            int color = Color.parseColor(colorStr);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            holder.vColor.setBackground(gd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        holder.ivSelected.setVisibility(colorStr.equalsIgnoreCase(selectedColor) ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onColorClick(colorStr);
        });
    }

    @Override
    public int getItemCount() {
        return colors.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        View vColor;
        ImageView ivSelected;

        public VH(@NonNull View itemView) {
            super(itemView);
            vColor = itemView.findViewById(R.id.vColor);
            ivSelected = itemView.findViewById(R.id.ivSelected);
        }
    }
}
