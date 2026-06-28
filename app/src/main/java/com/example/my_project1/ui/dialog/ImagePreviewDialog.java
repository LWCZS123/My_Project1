package com.example.my_project1.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.my_project1.R;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 图片预览对话框 - 支持多图浏览和缩放
 */
public class ImagePreviewDialog extends Dialog {

    private List<Uri> imageUris;
    private int currentPosition;
    private ViewPager2 viewPager;
    private TextView tvIndicator;
    private ImageView btnClose;

    public ImagePreviewDialog(@NonNull Context context, List<Uri> imageUris, int position) {
        super(context, R.style.FullScreenDialog);
        this.imageUris = imageUris;
        this.currentPosition = position;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_image_preview);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
        }

        initViews();
        setupViewPager();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewpager_preview);
        tvIndicator = findViewById(R.id.tv_indicator);
        btnClose = findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> dismiss());

        updateIndicator(currentPosition);
    }

    private void setupViewPager() {
        ImagePreviewAdapter adapter = new ImagePreviewAdapter(getContext(), imageUris);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicator(position);
            }
        });
    }

    private void updateIndicator(int position) {
        tvIndicator.setText((position + 1) + "/" + imageUris.size());
    }

    // ViewPager Adapter - 使用 PhotoView 支持缩放
    private static class ImagePreviewAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImagePreviewAdapter.ViewHolder> {
        private Context context;
        private List<Uri> imageUris;

        public ImagePreviewAdapter(Context context, List<Uri> imageUris) {
            this.context = context;
            this.imageUris = imageUris;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            PhotoView photoView = new PhotoView(context);
            photoView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            return new ViewHolder(photoView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Glide.with(context)
                    .load(imageUris.get(position))
                    .fitCenter()
                    .into(holder.photoView);
        }

        @Override
        public int getItemCount() {
            return imageUris.size();
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            PhotoView photoView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = (PhotoView) itemView;
            }
        }
    }
}