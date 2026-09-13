# 图标市场页面重构实施计划

本计划旨在根据设计稿重构 Android 项目的“图标市场”页面布局，提升视觉档次并优化层级结构。

## 用户评审要求

- **颜色确认**: 采用了主色 `#5969F5` 及背景色 `#F6F7FB`，统计卡片采用了淡色系背景。
- **图标资源**: 尽量使用了项目中已有的图标（如 `ic_sun`, `ic_camera` 等），如果某些特定图标缺失，使用了占位图标。

## 拟议变更

### 资源文件 (Resources)

#### [colors.xml](file:///C:/Users/86147/Desktop/xiaoyuan/My_Project1/app/src/main/res/values/colors.xml)
- 新增 `market_primary`, `market_page_bg` 等 10 余个 UI 相关颜色。

#### [NEW] Drawables
- 创建 `bg_market_card.xml`: 20dp 圆角背景。
- 创建 `bg_pill_selected_market.xml` / `bg_pill_unselected_market.xml`: 胶囊式标签背景。
- 创建 `bg_stat_blue_card.xml`, `bg_stat_red_card.xml`, `bg_stat_green_card.xml`: 统计区域背景。
- 创建 `bg_icon_item_container.xml`: 图标预览容器背景。

---

### 布局文件 (Layouts)

#### [activity_icon_market.xml](file:///C:/Users/86147/Desktop/xiaoyuan/My_Project1/app/src/main/res/layout/activity_icon_market.xml)
- 使用 `ConstraintLayout` 重构。
- 使用 `NestedScrollView` 实现长页面的流畅滚动。
- 结构包含：Header, Search, StyleTabs, HotIcons Card, Statistics, Collection Header, RecyclerView.

#### [NEW] [item_icon_collection.xml](file:///C:/Users/86147/Desktop/xiaoyuan/My_Project1/app/src/main/res/layout/item_icon_collection.xml)
- 定义合集列表的项布局，使用 `MaterialCardView` 配合 20dp 圆角。

## 验证计划

### 自动化测试
- 无（主要是 UI 布局变更）。

### 手动验证
- **设计视图验证**: 使用 Android Studio 的 Layout Editor 预览各屏幕尺寸下的显示效果。
- **编译验证**: 确保所有 ID 和资源引用正确，无 XML 语法错误。
