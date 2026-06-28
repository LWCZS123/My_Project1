package com.example.my_project1.utils;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * IconGridUtils
 * ─────────────────────────────────────────────────────────────
 * 图标网格布局动态计算工具。
 *
 * 核心职责：
 * ① calcSpanCount  — 根据容器宽度动态计算最优列数（3~6 列）
 * ② calcPageSize   — 根据 BottomSheet 可用高度计算每页理想图标数
 *
 * 使用场景：
 *   DetailPagerAdapter.PageViewHolder 构造时调用 calcSpanCount
 *   IconDetailFragment.onViewCreated 的 View.post() 回调中调用 calcPageSize
 */
public class IconGridUtils {

    /** 图标 item 目标最小宽度（dp）。低于此宽度时自动减少列数 */
    private static final int ITEM_MIN_WIDTH_DP = 72;

    /** 最少列数 */
    private static final int SPAN_MIN = 3;

    /** 最多列数 */
    private static final int SPAN_MAX = 6;

    /** 最少行数（防止 pageSize 计算结果为 0） */
    private static final int ROW_MIN = 2;

    private IconGridUtils() {}

    /**
     * 根据容器可用宽度（px）计算最合适的列数。
     *
     * 算法：spanCount = floor(containerWidthPx / itemMinWidthPx)
     * 结果钳制在 [SPAN_MIN, SPAN_MAX]。
     *
     * @param context          用于读取 density
     * @param containerWidthPx 容器可用宽度（像素）
     * @return 最优列数
     */
    public static int calcSpanCount(Context context, int containerWidthPx) {
        if (containerWidthPx <= 0) return SPAN_MIN;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float itemMinPx = ITEM_MIN_WIDTH_DP * dm.density;
        int span = (int) (containerWidthPx / itemMinPx);
        return Math.max(SPAN_MIN, Math.min(SPAN_MAX, span));
    }

    /**
     * 根据可用高度和 item 高度计算每页应显示的图标数（pageSize）。
     *
     * 算法：
     *   rows     = floor(availHeightPx / itemHeightPx)，最少 ROW_MIN 行
     *   pageSize = rows × spanCount
     *
     * @param context       用于读取 density
     * @param availHeightPx ViewPager2 的实际可用高度（像素），由 View.post() 获取
     * @param itemHeightDp  单个图标 item 的高度（dp），含文字 + padding。
     *                      详情页推荐值：88dp（图标64dp + 文字16dp + 上下padding8dp）
     * @param spanCount     已计算好的列数
     * @return 理想每页图标数
     */
    public static int calcPageSize(Context context, int availHeightPx,
                                   int itemHeightDp, int spanCount) {
        if (availHeightPx <= 0 || spanCount <= 0) return spanCount * ROW_MIN;
        float density = context.getResources().getDisplayMetrics().density;
        float itemHeightPx = itemHeightDp * density;
        int rows = Math.max(ROW_MIN, (int) (availHeightPx / itemHeightPx));
        return rows * spanCount;
    }
}