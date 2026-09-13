package com.example.my_project1.ui.adapter.icon;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

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
    private volatile boolean metadataLoading;

    public CategoryAdapter(OnCategoryClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<IconCategory> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<IconCategory>() {
                @Override
                public boolean areItemsTheSame(@NonNull IconCategory a, @NonNull IconCategory b) {
                    // 同时校验名称和所属文件（pack key），确保不同风格下同名分类能正确刷新
                    return java.util.Objects.equals(a.getCategory(), b.getCategory())
                            && java.util.Objects.equals(a.getFile(), b.getFile());
                }

                @Override
                public boolean areContentsTheSame(@NonNull IconCategory a, @NonNull IconCategory b) {
                    return a.getCount() == b.getCount()
                            && java.util.Objects.equals(a.getCategory(), b.getCategory())
                            && java.util.Objects.equals(a.getStyle(), b.getStyle())
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            // "thumbnail" 或 "metadata" 负载刷新
            holder.bind(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    /** Thumbnail URLs are enriched on the same model instance after DiffUtil ran. */
    public void refreshThumbnailContent() {
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), "thumbnail");
    }

    public void setMetadataLoading(boolean loading) {
        if (metadataLoading == loading) return;
        metadataLoading = loading;
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount(), "metadata");
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvStyleTag;
        private final TextView tvRating;
        private final TextView tvDescription;
        private final TextView tvDownloadCount;
        private final TextView tvIconCount;
        private final ImageView metadataLoadingView;
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
            metadataLoadingView = itemView.findViewById(R.id.iv_collection_loading_bar);
            btnDownload     = itemView.findViewById(R.id.btn_download);

            ViewGroup layoutPreviews = itemView.findViewById(R.id.layout_previews);
            for (int i = 0; i < 6; i++) {
                ViewGroup container = (ViewGroup) layoutPreviews.getChildAt(i);
                thumbViews[i] = (ImageView) container.getChildAt(0);
            }
        }

        void bind(IconCategory category) {
            if (category == null) return;
            List<String> thumbUrls = category.getThumbUrls();
            android.util.Log.d("CategoryAdapter", "Binding category: " + category.getCategory() 
                + ", Style: " + category.getStyle() 
                + ", File: " + category.getFile()
                + ", ThumbsCount: " + (thumbUrls != null ? thumbUrls.size() : "null"));

            boolean missingMetadata = category.getCategory() == null
                    || category.getCategory().trim().isEmpty();
            boolean showMetadataLoader = metadataLoading || missingMetadata;
            if (showMetadataLoader) {
                tvName.setVisibility(View.INVISIBLE);
                if (metadataLoadingView != null) {
                    metadataLoadingView.setVisibility(View.VISIBLE);
                    GlideImageLoader.showMetadataLoading(itemView.getContext(), metadataLoadingView);
                }
            } else {
                tvName.setVisibility(View.VISIBLE);
                if (metadataLoadingView != null) {
                    com.bumptech.glide.Glide.with(metadataLoadingView).clear(metadataLoadingView);
                    metadataLoadingView.setVisibility(View.GONE);
                }
                tvName.setText(category.getCategory());
            }
            if (category == null) return;
            
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

            thumbUrls = category.getThumbUrls();
            int padding = (int) (1 * itemView.getContext().getResources().getDisplayMetrics().density + 0.5f);

            for (int index = 0; index < 6; index++) {
                ImageView iv = thumbViews[index];
                iv.setPadding(padding, padding, padding, padding);
                if (thumbUrls != null && index < thumbUrls.size()) {
                    String url = thumbUrls.get(index);
                    // 仅对阿里云 OSS 资源添加缩略图参数，避免损坏其他源（如 freeicon）的 URL
                    String thumbUrl = url;
                    if (!url.contains("?") && url.contains("aliyuncs.com")) {
                        thumbUrl += "?x-oss-process=image/resize,w_100";
                    }
                    android.util.Log.d("CategoryAdapter", "Position: " + getBindingAdapterPosition() + ", ViewIndex: " + index + ", URL: " + thumbUrl);
                    Object previousUrl = iv.getTag(R.id.tag_icon_thumb_url);
                    if (!thumbUrl.equals(previousUrl)) {
                        iv.setTag(R.id.tag_icon_thumb_url, thumbUrl);
                        com.bumptech.glide.Glide.with(iv).clear(iv);
                        GlideImageLoader.loadThumbnail(itemView.getContext(), thumbUrl, iv);
                    }
                    iv.setAlpha(1f);
                    iv.setVisibility(View.VISIBLE);
                    ((View)iv.getParent()).setVisibility(View.VISIBLE);
                } else {
                    // Keep a stable preview slot while remote thumbnails are fetched.
                    // The name/count/style fields are already usable from the JSON payload.
                    iv.setTag(R.id.tag_icon_thumb_url, null);
                    GlideImageLoader.showThumbnailLoading(itemView.getContext(), iv);
                    iv.setAlpha(1f);
                    iv.setVisibility(View.VISIBLE);
                    ((View)iv.getParent()).setVisibility(View.VISIBLE);
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
