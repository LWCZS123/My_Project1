package com.example.my_project1.data.model.icon;

/**
 * IconItem - 单个图标数据模型
 *
 * 对应两种 JSON 结构：
 *   search.json  → { id, name, category, thumb, pinyin, initial }
 *   category.json → { id, name, category, url, thumb, pinyin, initial }
 *
 * OSS 缩略图规则：thumb = url + "?x-oss-process=image/resize,w_100"
 */
public class IconItem {

    // ==================== JSON 字段 ====================

    /** 图标唯一 ID */
    private String id;

    /** 图标名称，如 "云存储" */
    private String name;

    /** 所属分类名称，如 "NAS存储" */
    private String category;

    /** 原图 URL，用于详情页展示 */
    private String url;

    /**
     * 缩略图 URL
     * search.json 中直接提供
     * category.json 中可由 url + OSS 参数构建
     */
    private String thumb;

    /** 拼音全拼，如 "yuancunchu" */
    private String pinyin;

    /** 首字母，如 "y"，用于首字母搜索 */
    private String initial;

    // ==================== 构造函数 ====================

    public IconItem() {}

    // ==================== Getter & Setter ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getThumb() { return thumb; }
    public void setThumb(String thumb) { this.thumb = thumb; }

    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }

    public String getInitial() { return initial; }
    public void setInitial(String initial) { this.initial = initial; }

    // ==================== 工具方法 ====================

    /**
     * 获取缩略图 URL
     * 优先使用 thumb 字段，若为空则从 url 构建 OSS 缩略图参数
     */
    public String getThumbUrl() {
        if (thumb != null && !thumb.isEmpty()) return thumb;
        if (url != null && !url.isEmpty()) return url + "?x-oss-process=image/resize,w_100";
        return "";
    }
}