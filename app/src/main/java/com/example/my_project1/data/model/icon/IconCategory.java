package com.example.my_project1.data.model.icon;

import java.util.List;

/**
 * IconCategory - 图标分类模型
 * 对应 index.json 中每条分类元数据
 * 以及每个分类 JSON 文件解析后的完整数据
 */
public class IconCategory {

    // ==================== index.json 字段 ====================

    /** 分类名称，如 "NAS存储" */
    private String category;

    /** 该分类图标总数 */
    private int count;

    /** 分类详情 JSON 文件名，如 "nas.json" */
    private String file;

    // ==================== UI 辅助字段 ====================

    /**
     * 9 张缩略图 URL（3×3 宫格预览用）
     * 从分类详情 JSON 中取前 9 条
     */
    private List<String> thumbUrls;

    // ==================== 构造函数 ====================

    public IconCategory() {}

    public IconCategory(String category, int count, String file) {
        this.category = category;
        this.count = count;
        this.file = file;
    }

    // ==================== Getter & Setter ====================

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public List<String> getThumbUrls() { return thumbUrls; }
    public void setThumbUrls(List<String> thumbUrls) { this.thumbUrls = thumbUrls; }
}