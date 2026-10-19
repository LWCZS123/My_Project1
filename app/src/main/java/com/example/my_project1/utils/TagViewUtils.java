package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.my_project1.data.model.tag.TagEntity;

import java.util.List;

public class TagViewUtils {

    public static void renderTags(ViewGroup container, List<TagEntity> tags) {
        renderTags(container, tags, Integer.MAX_VALUE);
    }

    public static void renderTags(ViewGroup container, List<TagEntity> tags, int maxCount) {
        if (container == null) return;
        container.removeAllViews();

        if (tags == null || tags.isEmpty() || maxCount <= 0) {
            container.setVisibility(View.GONE);
            return;
        }

        Context context = container.getContext();
        int displayCount = Math.min(tags.size(), maxCount);
        int added = 0;

        for (int i = 0; i < displayCount; i++) {
            TagEntity tag = tags.get(i);
            if (tag == null || tag.getName() == null || tag.getName().trim().isEmpty()) {
                continue;
            }

            TextView tvTag = new TextView(context);
            String tagName = tag.getName().trim();
            if (!tagName.startsWith("#")) {
                tagName = "#" + tagName;
            }
            tvTag.setText(tagName);
            tvTag.setTextSize(11f);

            int paddingH = dp2px(context, 7);
            int paddingV = dp2px(context, 3);
            tvTag.setPadding(paddingH, paddingV, paddingH, paddingV);

            String bgColor = tag.getBgColor();
            String textColor = tag.getTextColor();

            int parsedBgColor;
            int parsedTextColor;

            if (bgColor != null && !bgColor.trim().isEmpty()) {
                try {
                    parsedBgColor = Color.parseColor(bgColor.trim());
                } catch (Exception e) {
                    parsedBgColor = Color.parseColor("#F1F5F9");
                }
            } else {
                parsedBgColor = Color.parseColor("#F1F5F9");
            }

            if (textColor != null && !textColor.trim().isEmpty()) {
                try {
                    parsedTextColor = Color.parseColor(textColor.trim());
                } catch (Exception e) {
                    parsedTextColor = Color.parseColor("#2563EB");
                }
            } else {
                parsedTextColor = Color.parseColor("#2563EB");
            }

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(dp2px(context, 6));
            gd.setColor(parsedBgColor);

            tvTag.setBackground(gd);
            tvTag.setTextColor(parsedTextColor);

            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 2, dp2px(context, 6), dp2px(context, 4));
            tvTag.setLayoutParams(lp);

            container.addView(tvTag);
            added++;
        }

        if (added > 0) {
            container.setVisibility(View.VISIBLE);
        } else {
            container.setVisibility(View.GONE);
        }
    }

    private static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
