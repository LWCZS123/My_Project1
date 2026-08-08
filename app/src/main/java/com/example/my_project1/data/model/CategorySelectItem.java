package com.example.my_project1.data.model;

public class CategorySelectItem {
    private String id;
    private String name;
    private String icon;
    private int level; // 1 = 一级分类, 2 = 二级分类
    private String iconBgColor;
    private Object originalData; // 存储原始 Category 或 SubCategory

    public CategorySelectItem(String id, String name, String icon, int level, String iconBgColor, Object originalData) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.level = level;
        this.iconBgColor = iconBgColor;
        this.originalData = originalData;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public int getLevel() { return level; }
    public String getIconBgColor() { return iconBgColor; }
    public Object getOriginalData() { return originalData; }
}
