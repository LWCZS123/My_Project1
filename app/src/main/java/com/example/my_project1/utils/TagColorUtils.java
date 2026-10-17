package com.example.my_project1.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

public class TagColorUtils {

    public static class ColorOption {
        public String bgColor;
        public String textColor;

        public ColorOption(String bgColor, String textColor) {
            this.bgColor = bgColor;
            this.textColor = textColor;
        }
    }

    public static List<ColorOption> getPresetColors() {
        List<ColorOption> options = new ArrayList<>();
        options.add(new ColorOption("#FFF3E0", "#FF9800")); // Soft Orange
        options.add(new ColorOption("#FFFDE7", "#FBC02D")); // Soft Yellow
        options.add(new ColorOption("#E1F5FE", "#03A9F4")); // Soft Blue
        options.add(new ColorOption("#E8EAF6", "#3F51B5")); // Soft Dark Blue/Indigo
        options.add(new ColorOption("#F3E5F5", "#9C27B0")); // Soft Purple
        options.add(new ColorOption("#FFEEEF", "#FF5252")); // Soft Pink/Red
        options.add(new ColorOption("#E8F5E9", "#4CAF50")); // Soft Green
        options.add(new ColorOption("#E0F2F1", "#009688")); // Soft Teal
        options.add(new ColorOption("#ECEFF1", "#607D8B")); // Soft Grey
        return options;
    }

    public static Drawable getRainbowDrawable(Context context, int sizePx) {
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] colors = new int[]{
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        };
        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float r = sizePx / 2f;
        paint.setShader(new SweepGradient(cx, cy, colors, null));
        canvas.drawCircle(cx, cy, r, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static String calculateTextColor(String bgHexColor) {
        if (bgHexColor == null || bgHexColor.trim().isEmpty()) {
            return "#333333";
        }
        try {
            int color = Color.parseColor(bgHexColor.trim());
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            double luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue);
            if (luminance < 160) {
                return "#FFFFFF";
            } else {
                return "#333333";
            }
        } catch (Exception e) {
            return "#333333";
        }
    }
}
