package com.example.my_project1.ui.adapter.calendar;

import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.data.model.calendar.CalendarDay;
import com.example.my_project1.databinding.ItemCalendarMonthBinding;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 月份 ViewPager2 适配器 - 性能优化版
 *
 * 核心改动：
 *
 * 1. 【移除强制 Measure】
 *    高度完全由 applyHeight() 的固定公式 ROW_HEIGHT_DP × rowCount 决定，
 *    Fragment 侧通过 dataEngine 回调调用 CalendarFragment.applyCalendarHeight()，
 *    不再需要 itemView.measure()。
 *
 * 2. 【骨架屏占位符】
 *    showPlaceholder() 在数据就绪前展示灰色骨架行（SkeletonView），
 *    用户能立即看到日历轮廓，消除白屏感。
 *    数据到达后 applyData() 直接替换为真实内容，无闪烁。
 *
 * 3. 【ViewHolder 复用安全】
 *    onViewRecycled() 正确清理 activeHolders，防止脏数据投递。
 *
 * 4. 【ROW_HEIGHT_DP public】
 *    CalendarFragment.applyCalendarHeight() 必须引用此常量，
 *    保证两侧数值完全一致。
 */
public class MonthPagerAdapter extends RecyclerView.Adapter<MonthPagerAdapter.MonthViewHolder> {

    private static final String PERF_TAG = "CalendarPagerPerf";

    /** 每行高度 60dp，供外部 CalendarFragment.applyCalendarHeight() 引用。 */
    public static final int ROW_HEIGHT_DP = 60;

    public interface OnDayClickListener extends CalendarAdapter1.OnDayClickListener {}

    private int                                   totalCount = 0;
    private CalendarDataEngine                    dataEngine;
    private CalendarAdapter1.OnDayClickListener   dayClickListener;
    private int                                   displayType = 0;
    private float                                 density     = 0f;

    private final android.util.SparseArray<MonthViewHolder> activeHolders =
            new android.util.SparseArray<>();

    // ─────────────────────────────────────────────
    // 外部配置接口
    // ─────────────────────────────────────────────

    public void init(int totalCount, CalendarDataEngine engine) {
        this.totalCount = totalCount;
        this.dataEngine  = engine;
        notifyDataSetChanged();
    }

    public void setOnDayClickListener(CalendarAdapter1.OnDayClickListener listener) {
        this.dayClickListener = listener;
    }

    public void setDisplayType(int type) {
        if (this.displayType == type) return;
        this.displayType = type;
        notifyDataSetChanged();
    }

    /**
     * 由 CalendarDataEngine 回调，将异步计算好的月份数据投递给对应 ViewHolder。
     * 必须在主线程调用（CalendarFragment 已保证通过 mainHandler 切换）。
     */
    public void deliverData(int pageIndex,
                            List<CalendarDay> days,
                            CalendarDataEngine.MonthMeta meta) {
        MonthViewHolder holder = activeHolders.get(pageIndex);
        if (holder != null) {
            holder.applyData(days, meta);
        }
    }

    // ─────────────────────────────────────────────
    // RecyclerView.Adapter 实现
    // ─────────────────────────────────────────────

    @NonNull
    @Override
    public MonthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (density == 0f) {
            density = parent.getContext().getResources().getDisplayMetrics().density;
        }
        ItemCalendarMonthBinding binding = ItemCalendarMonthBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MonthViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MonthViewHolder holder, int position) {
        activeHolders.put(position, holder);

        // 先展示骨架屏（立即可见，零延迟）
        CalendarDataEngine.MonthMeta meta = dataEngine != null
                ? dataEngine.getMetaSync(position) : null;
        holder.showPlaceholder(meta);

        // 再异步请求真实数据
        if (dataEngine != null) {
            dataEngine.requestMonth(position);
        }
    }

    @Override
    public void onViewRecycled(@NonNull MonthViewHolder holder) {
        super.onViewRecycled(holder);
        int pos = holder.getCurrentPosition();
        if (pos >= 0 && activeHolders.get(pos) == holder) {
            activeHolders.remove(pos);
        }
        holder.clearPosition();
    }

    @Override
    public int getItemCount() { return totalCount; }

    // ─────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────

    class MonthViewHolder extends RecyclerView.ViewHolder {

        private final ItemCalendarMonthBinding binding;
        private final CalendarAdapter1         innerAdapter;
        private int currentPosition = (int) RecyclerView.NO_ID;

        MonthViewHolder(@NonNull ItemCalendarMonthBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            GridLayoutManager layoutManager =
                    new GridLayoutManager(binding.getRoot().getContext(), 7) {
                        @Override public boolean canScrollVertically() { return false; }
                    };
            layoutManager.setInitialPrefetchItemCount(7);
            binding.rvMonth.setLayoutManager(layoutManager);
            binding.rvMonth.setItemAnimator(null);
            // RecyclerView 本身高度固定由 applyHeight 设置，可设 hasFixedSize 提速
            binding.rvMonth.setHasFixedSize(false); // rowCount 可能是 5 或 6，不能 fixed

            innerAdapter = new CalendarAdapter1();
            binding.rvMonth.setAdapter(innerAdapter);

            innerAdapter.setOnDayClickListener(day -> {
                if (dayClickListener != null) dayClickListener.onDayClick(day);
            });
        }

        /**
         * 骨架屏占位：数据未到达时，根据已知的行数提前设置高度并显示半透明骨架。
         * 若 meta 为 null（首次加载，行数未知），默认按 5 行展示。
         */
        void showPlaceholder(CalendarDataEngine.MonthMeta meta) {
            currentPosition = getAdapterPosition();
            int rowCount = (meta != null) ? meta.rowCount : 5;
            applyHeight(rowCount, "placeholder");

            // 显示骨架视图，隐藏真实内容

            binding.rvMonth.setVisibility(View.INVISIBLE);
        }

        /**
         * 数据就绪：隐藏骨架，填充真实内容。
         * 高度已在 showPlaceholder 阶段设置好，此处仅做修正（rowCount 可能更新）。
         */
        void applyData(List<CalendarDay> days, CalendarDataEngine.MonthMeta meta) {
            long t0 = SystemClock.elapsedRealtime();
            if (days == null || days.isEmpty()) {
                binding.rvMonth.setVisibility(View.INVISIBLE);
                return;
            }
            applyHeight(meta.rowCount, "applyData");
            innerAdapter.setDisplayType(displayType);
            innerAdapter.setDays(days);

            // 骨架屏淡出，内容淡入（可选动画，16ms 即 1 帧，几乎无感）

            binding.rvMonth.setVisibility(View.VISIBLE);

            Log.d(PERF_TAG, "applyData: page=" + currentPosition
                    + " rows=" + meta.rowCount
                    + " took=" + (SystemClock.elapsedRealtime() - t0) + "ms");
        }

        /**
         * 设置 rvMonth 精确高度。
         *
         * 公式：ROW_HEIGHT_DP × rowCount × density
         * 与 CalendarFragment.applyCalendarHeight() 使用相同常量，
         * 保证 ViewPager2 容器高度 == 内容高度，不截断也不留白。
         *
         * ⚠️ 不调用任何 measure/layout！纯粹修改 LayoutParams。
         */
        private void applyHeight(int rowCount, String caller) {
            int heightPx = (int) (ROW_HEIGHT_DP * rowCount * density);
            ViewGroup.LayoutParams params = binding.rvMonth.getLayoutParams();
            if (params.height != heightPx) {
                params.height = heightPx;
                binding.rvMonth.setLayoutParams(params);
                Log.d(PERF_TAG, "applyHeight[" + caller + "]: page=" + currentPosition
                        + " rows=" + rowCount + " px=" + heightPx);
            }
        }

        int getCurrentPosition() { return currentPosition; }
        void clearPosition()      { currentPosition = (int) RecyclerView.NO_ID; }
    }
}