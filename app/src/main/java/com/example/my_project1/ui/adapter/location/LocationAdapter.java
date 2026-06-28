package com.example.my_project1.ui.adapter.location;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.core.PoiItem;
import com.example.my_project1.R;

import java.util.List;

import io.reactivex.annotations.NonNull;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {

    private Context context;
    private List<PoiItem> poiItems;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(PoiItem poiItem);
    }

    public LocationAdapter(Context context, List<PoiItem> poiItems) {
        this.context = context;
        this.poiItems = poiItems;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PoiItem poiItem = poiItems.get(position);

        holder.tvName.setText(poiItem.getTitle());
        holder.tvAddress.setText(poiItem.getSnippet());

        // 显示距离
        int distance = (int) poiItem.getDistance();
        String distanceText;
        if (distance < 1000) {
            distanceText = distance + "m";
        } else {
            distanceText = String.format("%.1fkm", distance / 1000.0);
        }
        holder.tvDistance.setText(distanceText);

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(poiItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return poiItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvAddress;
        TextView tvDistance;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvDistance = itemView.findViewById(R.id.tvDistance);
        }
    }
}