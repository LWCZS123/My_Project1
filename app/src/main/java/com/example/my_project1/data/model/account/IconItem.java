package com.example.my_project1.data.model.account;

public class IconItem {
    private String url;   // 图标地址
    private String name;  // 图标名字

    public IconItem(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public String getUrl() { return url; }
    public String getName() { return name; }
}
