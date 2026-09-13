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
                    // 同时校验名称和所属文件（pack key），确保不同风格下同名分类能正确刷新
                    return a.getCategory().equals(b.getCategory())
                            && a.getFile().equals(b.getFile());
                }

                @Override
                public boolean areContentsTheSame(@NonNull IconCategory a, @NonNull IconCategory b) {
                    return a.getCount() == b.getCount()
                            && a.getCategory().equals(b.getCategory())
                            && java.util.Objects.equals(a.getThumbUrls(), b.getThumbUrls());
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_icon_collection, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvStyleTag;
        private final TextView tvRating;
        private final TextView tvDescription;
        private final TextView tvDownloadCount;
        private final TextView tvIconCount;
        private final com.google.android.material.button.MaterialButton btnDownload;
        private final ImageView[] thumbViews = new ImageView[6];

        ViewHolder(View itemView) {
            super(itemView);
            tvName          = itemView.findViewById(R.id.tv_collection_name);
            tvStyleTag      = itemView.findViewById(R.id.tv_style_tag);
            tvRating        = itemView.findViewById(R.id.tv_rating);
            tvDescription   = itemView.findViewById(R.id.tv_description);
            tvDownloadCount = itemView.findViewById(R.id.tv_download_count);
            tvIconCount     = itemView.findViewById(R.id.tv_icon_count);
            btnDownload     = itemView.findViewById(R.id.btn_download);

            ViewGroup layoutPreviews = itemView.findViewById(R.id.layout_previews);
            for (int i = 0; i < 6; i++) {
                ViewGroup container = (ViewGroup) layoutPreviews.getChildAt(i);
                thumbViews[i] = (ImageView) container.getChildAt(0);
            }
        }

        void bind(IconCategory category) {
            tvName.setText(category.getCategory());
            
            String style = category.getStyle();
            tvStyleTag.setText(getStyleDisplayName(style));
            
            // 设置下载按钮颜色
            int colorRes = R.color.icon_style_filled;
            if ("line".equals(style)) colorRes = R.color.icon_style_line;
            else if ("lineal-color".equals(style)) colorRes = R.color.icon_style_color;
            btnDownload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    itemView.getContext().getResources().getColor(colorRes)));

            tvIconCount.setText("· " + category.getCount() + " 枚图标");
            
            // 演示用数据，实际应从 Model 获取
            tvRating.setText("4.9");
            tvDownloadCount.setText((category.getCount() * 3) + " 下载");
            tvDescription.setText("精选 " + category.getCategory() + " 风格图标集合");

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCategoryClick(category);
            });

            List<String> thumbUrls = category.getThumbUrls();
            int padding = (int) (1 * itemView.getContext().getResources().getDisplayMetrics().density + 0.5f);

            for (int i = 0; i < 6; i++) {
                ImageView iv = thumbViews[i];
                iv.setPadding(padding, padding, padding, padding);
                Glide.with(itemView.getContext()).clear(iv);

                if (thumbUrls != null && i < thumbUrls.size()) {
                    String url = thumbUrls.get(i);
                    String thumbUrl = url.contains("?") ? url : url + "?x-oss-process=image/resize,w_100";
                    GlideImageLoader.loadThumbnail(itemView.getContext(), thumbUrl, iv);
                    iv.setVisibility(View.VISIBLE);
                    ((View)iv.getParent()).setVisibility(View.VISIBLE);
                } else {
                    iv.setVisibility(View.INVISIBLE);
                    ((View)iv.getParent()).setVisibility(View.INVISIBLE);
                }
            }
        }

        private String getStyleDisplayName(String style) {
            if ("line".equals(style)) return "线性";
            if ("lineal-color".equals(style)) return "彩色";
            return "默认";
        }
    }
}