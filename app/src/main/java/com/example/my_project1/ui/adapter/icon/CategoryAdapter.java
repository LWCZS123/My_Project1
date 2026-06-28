package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.utils.GlideImageLoader;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * CategoryAdapter - 分类市集首页列表 Adapter
 * -------------------------------------------------------
 * 使用 ListAdapter + DiffUtil，数据变化时高效局部刷新
 * 每个 item：
 *   - 分类名称
 *   - 图标总数
 *   - 3×3 九宫格缩略图预览
 */
public class CategoryAdapter extends ListAdapter<IconCategory, CategoryAdapter.ViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(IconCategory category);
    }

    private final OnCategoryClickListener listener;

    public CategoryAdapter(OnCategoryClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<IconCategory> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<IconCategory>() {
                @Override
                public boolean areItemsTheSame(@NonNull IconCategory a, @NonNull IconCategory b) {
                    return a.getCategory().equals(b.getCategory());
                }

                @Override
                public boolean areContentsTheSame(@NonNull IconCategory a, @NonNull IconCategory b) {
                    return a.getCount() == b.getCount()
                            && a.getCategory().equals(b.getCategory());
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_icon_category, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvCount;
        private final ImageView[] thumbViews = new ImageView[9];

        ViewHolder(View itemView) {
            super(itemView);
            tvName  = itemView.findViewById(R.id.tv_category_name);
            tvCount = itemView.findViewById(R.id.tv_category_count);

            // 9 个缩略图 ImageView（xml 中 id 为 iv_thumb_0 ~ iv_thumb_8）
            thumbViews[0] = itemView.findViewById(R.id.iv_thumb_0);
            thumbViews[1] = itemView.findViewById(R.id.iv_thumb_1);
            thumbViews[2] = itemView.findViewById(R.id.iv_thumb_2);
            thumbViews[3] = itemView.findViewById(R.id.iv_thumb_3);
            thumbViews[4] = itemView.findViewById(R.id.iv_thumb_4);
            thumbViews[5] = itemView.findViewById(R.id.iv_thumb_5);
            thumbViews[6] = itemView.findViewById(R.id.iv_thumb_6);
            thumbViews[7] = itemView.findViewById(R.id.iv_thumb_7);
            thumbViews[8] = itemView.findViewById(R.id.iv_thumb_8);
        }

        void bind(IconCategory category) {
            tvName.setText(category.getCategory());
            tvCount.setText(category.getCount() + " 个");

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCategoryClick(category);
            });

            List<String> thumbUrls = category.getThumbUrls();

            for (int i = 0; i < 9; i++) {
                ImageView iv = thumbViews[i];
                if (iv == null) continue;

                Glide.with(itemView.getContext()).clear(iv);

                if (thumbUrls != null && i < thumbUrls.size()) {
                    String url = thumbUrls.get(i);
                    String thumbUrl = url.contains("?") ? url : url + "?x-oss-process=image/resize,w_100";

                    GlideImageLoader.loadThumbnail(
                            itemView.getContext(),
                            thumbUrl,
                            iv
                    );
                } else {
                    iv.setImageResource(android.R.color.darker_gray);
                }
            }
        }
    }
}