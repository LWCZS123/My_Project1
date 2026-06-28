package com.example.my_project1.ui.viewmodel.billvm;

/**
 * BillUiModel - 账单UI展示模型
 * -------------------------------------------------------
 * 📌 设计原则:
 *   所有格式化字符串在 ViewModel 中预计算完成
 *   onBindViewHolder 只做纯赋值，零计算，消除滑动抖动
 *
 * 📌 时间轴状态字段:
 *   isFirstOfDay  → 该条账单是当天第一笔，隐藏上连线
 *   isLastOfDay   → 该条账单是当天最后一笔，隐藏下连线
 */
public class BillUiModel {

    // ── 原始 ID（用于跳转详情）──────────────────────────
    public final long   localId;       // Room 主键
    public final String objectId;      // Bmob 云端 ID（可为 null）

    // ── 预格式化字段（直接 setText）────────────────────
    public final String timeText;          // "14:30"
    public final String categoryName;      // "餐饮"
    public final String categoryIconUrl;   // Glide 加载 URL
    public final String amountText;        // "-¥21.00" / "+¥500.00"
    public final int    amountColor;       // 红/绿颜色 int（已 resolve）
    public final String remarkText;        // 备注，空串则隐藏
    public final String locationText;      // 位置，空串则隐藏
    public final java.util.List<String> imageUrls; // 图片 URL 列表

    // ── 时间轴连线状态（Adapter 按位置判定后写入）────────
    /** true → 该 item 是当天第一笔，上方没有连线（隐藏上半段竖线） */
    public boolean isFirstOfDay;
    /** true → 该 item 是当天最后一笔，下方没有连线（隐藏下半段竖线） */
    public boolean isLastOfDay;

    /** DiffUtil 去重用 key，优先 objectId，否则用 localId */
    public final String diffKey;

    // ── 构造器（Builder 模式）──────────────────────────
    private BillUiModel(Builder b) {
        this.localId        = b.localId;
        this.objectId       = b.objectId;
        this.timeText       = b.timeText;
        this.categoryName   = b.categoryName;
        this.categoryIconUrl= b.categoryIconUrl;
        this.amountText     = b.amountText;
        this.amountColor    = b.amountColor;
        this.remarkText     = b.remarkText;
        this.locationText   = b.locationText;
        this.imageUrls      = b.imageUrls;
        this.isFirstOfDay   = b.isFirstOfDay;
        this.isLastOfDay    = b.isLastOfDay;
        this.diffKey        = (objectId != null && !objectId.isEmpty())
                ? objectId : String.valueOf(localId);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        long   localId;
        String objectId;
        String timeText       = "";
        String categoryName   = "";
        String categoryIconUrl= "";
        String amountText     = "";
        int    amountColor;
        String remarkText     = "";
        String locationText   = "";
        java.util.List<String> imageUrls = new java.util.ArrayList<>();
        boolean isFirstOfDay  = false;
        boolean isLastOfDay   = false;

        public Builder localId(long v)        { localId = v; return this; }
        public Builder objectId(String v)     { objectId = v; return this; }
        public Builder timeText(String v)     { timeText = v; return this; }
        public Builder categoryName(String v) { categoryName = v; return this; }
        public Builder categoryIconUrl(String v){ categoryIconUrl = v; return this; }
        public Builder amountText(String v)   { amountText = v; return this; }
        public Builder amountColor(int v)     { amountColor = v; return this; }
        public Builder remarkText(String v)   { remarkText = v != null ? v : ""; return this; }
        public Builder locationText(String v) { locationText = v != null ? v : ""; return this; }
        public Builder imageUrls(java.util.List<String> v){ imageUrls = v != null ? v : new java.util.ArrayList<>(); return this; }
        public Builder isFirstOfDay(boolean v){ isFirstOfDay = v; return this; }
        public Builder isLastOfDay(boolean v) { isLastOfDay = v; return this; }

        public BillUiModel build() { return new BillUiModel(this); }
    }
}