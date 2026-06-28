package com.example.my_project1.data.model;

import java.util.List;

public class CategoryIconGroup {
    private String name; // ← 对应 JSON 的 "name"
    private List<String> icons;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIcons() {
        return icons;
    }

    public void setIcons(List<String> icons) {
        this.icons = icons;
    }
}
