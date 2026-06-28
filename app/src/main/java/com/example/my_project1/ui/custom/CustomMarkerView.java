package com.example.my_project1.ui.custom;

import android.content.Context;
import android.widget.TextView;

import com.example.my_project1.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;
import java.util.Locale;

/**
 * 自定义 MarkerView - 点击折线图显示详细信息
 */
public class CustomMarkerView extends MarkerView {

    private TextView tvMarkerTime;
    private TextView tvMarkerValue;
    private List<String> timeLabels; // 时间标签列表

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);

        tvMarkerTime = findViewById(R.id.tvMarkerTime);
        tvMarkerValue = findViewById(R.id.tvMarkerValue);
    }

    /**
     * 设置时间标签
     */
    public void setTimeLabels(List<String> timeLabels) {
        this.timeLabels = timeLabels;
    }

    /**
     * 每次选中数据点时调用，更新显示内容
     */
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e == null) return;

        // 获取时间标签
        int index = (int) e.getX();
        String timeLabel = "";

        if (timeLabels != null && index >= 0 && index < timeLabels.size()) {
            timeLabel = timeLabels.get(index);
        }

        // 获取净资产值
        float value = e.getY();

        // 更新显示
        tvMarkerTime.setText(timeLabel);
        tvMarkerValue.setText(String.format(Locale.getDefault(),
                "净资产: %.2f", value));

        super.refreshContent(e, highlight);
    }

    /**
     * 设置 Marker 的偏移位置（显示在数据点上方）
     */
    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10);
    }
}