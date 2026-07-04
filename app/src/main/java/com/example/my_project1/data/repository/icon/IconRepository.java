package com.example.my_project1.data.repository.icon;

import android.util.Log;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * IconRepository - 图标数据仓库
 * -------------------------------------------------------
 * 数据源：阿里云 OSS JSON 文件
 *
 * JSON 文件结构：
 *   index.json   → 所有分类元信息列表
 *   search.json  → 全量搜索数据（约 20000 条，按需分块）
 *   {file}.json  → 每个分类的图标详情
 *
 * OSS 基础路径：https://icons-classify.oss-cn-hangzhou.aliyuncs.com/
 *
 * 性能优化：
 *   - 分类详情 JSON 按需加载，不预加载全部
 *   - 内存缓存已加载的分类 JSON，避免重复网络请求
 *   - 搜索数据懒加载，首次搜索时才拉取 search.json
 *   - computation 线程池处理 JSON 解析，不阻塞 networkIO
 *   - 分页截取：Repository 层直接切片，减少 ViewModel 数据处理量
 *   - 【使用 OkHttp 加载】
 */
public class IconRepository {

    private static final String TAG = "IconRepository";

    // ==================== OSS 配置 ====================

    public static final String OSS_BASE =
            "https://icons-classify.oss-cn-hangzhou.aliyuncs.com/";

    /** index.json 的完整 URL */
    private static final String INDEX_URL = OSS_BASE + "json/index.json";

    /** search.json 的完整 URL */
    private static final String SEARCH_URL = OSS_BASE + "json/search.json";

    /** OSS 缩略图参数 */
    public static final String THUMB_SUFFIX = "?x-oss-process=image/resize,w_100";

    // ==================== 分页配置 ====================

    /** 分类市集首页每页加载分类数 */
    public static final int PAGE_SIZE_CATEGORY = 10;

    /** 图标详情页每页图标数 */
    public static final int PAGE_SIZE_DETAIL = 50;

    /** 搜索结果每页图标数 */
    public static final int PAGE_SIZE_SEARCH = 200;

    // ==================== 单例 ====================

    private static volatile IconRepository instance;

    private final AppExecutors executors;
    private final OkHttpClient okHttpClient;

    /** 分类详情缓存：file → List<IconItem>，避免重复请求 */
    private final ConcurrentHashMap<String, List<IconItem>> categoryCache = new ConcurrentHashMap<>();

    /** index.json 缓存 */
    private List<IconCategory> categoryIndexCache = null;

    /** search.json 缓存（懒加载） */
    private List<IconItem> searchCache = null;

    // ==================== 回调接口 ====================

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    // ==================== 单例 ====================

    private IconRepository() {
        this.executors = AppExecutors.get();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public static IconRepository getInstance() {
        if (instance == null) {
            synchronized (IconRepository.class) {
                if (instance == null) {
                    instance = new IconRepository();
                }
            }
        }
        return instance;
    }

    // ==================== 分类市集首页 ====================

    /**
     * 获取分类列表（分页）
     * 首次调用拉取 index.json 并缓存，后续直接从缓存分页
     *
     * 修复：fillCategoryThumbs 同步在 networkIO 线程内完成，
     * 所有缩略图填充完毕后再 postMain，保证 UI 拿到数据时 thumbUrls 已就绪。
     * 同时将缩略图加载移至 computation 线程，避免占用 networkIO 线程槽位。
     *
     * @param page     页码（从 0 开始）
     * @param callback 主线程回调
     */
    public void getCategoryPage(int page, Callback<List<IconCategory>> callback) {
        executors.networkIO().execute(() -> {
            try {
                // 1. 若缓存为空则拉取 index.json
                if (categoryIndexCache == null) {
                    String json = fetchUrl(INDEX_URL);
                    categoryIndexCache = parseIndex(json);
                    Log.d(TAG, "getCategoryPage - index.json 加载完成: "
                            + categoryIndexCache.size() + " 个分类");
                }

                // 2. 分页截取
                int start = page * PAGE_SIZE_CATEGORY;
                if (start >= categoryIndexCache.size()) {
                    postMain(callback, new ArrayList<>());
                    return;
                }
                int end = Math.min(start + PAGE_SIZE_CATEGORY, categoryIndexCache.size());
                List<IconCategory> pageData = new ArrayList<>(categoryIndexCache.subList(start, end));

                // 3. 【修复 Bug1 + Bug3】
                //    在 computation 线程同步填充所有缩略图后，再回调主线程。
                //    这样 UI 拿到 pageData 时 thumbUrls 已全部就绪，不再出现空白格子。
                //    同时不占用 networkIO 线程做 CPU 解析，后续分页请求可以顺利拿到线程槽位。
                try {
                    executors.computation().submit(() -> {
                        for (IconCategory cat : pageData) {
                            if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                                fillCategoryThumbs(cat);
                            }
                        }
                    }).get(); // 阻塞等待缩略图全部填充完毕
                } catch (Exception e) {
                    Log.w(TAG, "getCategoryPage - 预取缩略图异常（不影响主流程）: " + e.getMessage());
                }

                postMain(callback, pageData);

            } catch (Exception e) {
                Log.e(TAG, "getCategoryPage 异常: " + e.getMessage(), e);
                postMainError(callback, "加载分类列表失败：" + e.getMessage());
            }
        });
    }

    /**
     * 获取分类详情（分页）
     * 按需加载对应分类 JSON，并缓存到内存
     *
     * @param category 分类对象（含 file 字段）
     * @param page     页码（从 0 开始）
     * @param callback 主线程回调
     */
    public void getCategoryDetail(IconCategory category, int page,
                                  Callback<List<IconItem>> callback) {
        executors.networkIO().execute(() -> {
            try {
                Log.d(TAG, "=== getCategoryDetail 执行 ===");
                Log.d(TAG, "分类: " + category.getCategory());
                Log.d(TAG, "请求页码: " + page);

                List<IconItem> all = loadCategoryItems(category);
                Log.d(TAG, "该分类总图标数: " + all.size());

                int start = page * PAGE_SIZE_DETAIL;
                int end = Math.min(start + PAGE_SIZE_DETAIL, all.size());
                Log.d(TAG, "分页区间 start=" + start + " end=" + end);


                if (start >= all.size()) {
                    Log.w(TAG, "getCategoryDetail: 页码超出，返回空列表");
                    postMain(callback, new ArrayList<>());
                    return;
                }

                List<IconItem> pageList = new ArrayList<>(all.subList(start, end));
                Log.d(TAG, "分页返回数量: " + pageList.size());
                postMain(callback, pageList);

            } catch (Exception e) {
                Log.e(TAG, "getCategoryDetail 异常: " + e.getMessage(), e);
                postMainError(callback, "加载图标详情失败：" + e.getMessage());
            }
        });
    }

    /**
     * 获取某分类的总页数
     */
    public void getCategoryPageCount(IconCategory category, Callback<Integer> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadCategoryItems(category);
                int total = (int) Math.ceil((double) all.size() / PAGE_SIZE_DETAIL);
                postMain(callback, total);
            } catch (Exception e) {
                Log.e(TAG, "getCategoryPageCount 异常: " + e.getMessage(), e);
                postMainError(callback, e.getMessage());
            }
        });
    }

    // ==================== 搜索 ====================

    /**
     * 搜索图标（支持名称、拼音、首字母）
     * 首次搜索懒加载 search.json，后续走内存缓存
     *
     * @param keyword  搜索关键词
     * @param page     页码（从 0 开始）
     * @param callback 主线程回调
     */
    public void search(String keyword, int page, Callback<List<IconItem>> callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            postMain(callback, new ArrayList<>());
            return;
        }

        executors.networkIO().execute(() -> {
            try {
                // 1. 懒加载 search.json
                if (searchCache == null) {
                    Log.d(TAG, "search - 首次搜索，开始加载 search.json");
                    String json = fetchUrl(SEARCH_URL);
                    searchCache = parseIconItems(json);
                    Log.d(TAG, "search - search.json 加载完成: " + searchCache.size() + " 条");
                }

                // 2. computation 线程过滤（避免在 networkIO 做 CPU 密集操作）
                final String lowerKeyword = keyword.trim().toLowerCase(Locale.CHINA);
                List<IconItem> allResults = new ArrayList<>();

                executors.computation().submit(() -> {
                    for (IconItem item : searchCache) {
                        if (matchKeyword(item, lowerKeyword)) {
                            allResults.add(item);
                        }
                    }
                }).get(); // 等待计算完成

                // 3. 分页
                int start = page * PAGE_SIZE_SEARCH;
                if (start >= allResults.size()) {
                    postMain(callback, new ArrayList<>());
                    return;
                }
                int end = Math.min(start + PAGE_SIZE_SEARCH, allResults.size());
                postMain(callback, new ArrayList<>(allResults.subList(start, end)));

                Log.d(TAG, "search - 关键词「" + keyword + "」命中 " + allResults.size() + " 条");

            } catch (Exception e) {
                Log.e(TAG, "search 异常: " + e.getMessage(), e);
                postMainError(callback, "搜索失败：" + e.getMessage());
            }
        });
    }

    // ==================== 内部：数据加载 ====================

    /**
     * 加载分类图标列表，命中缓存直接返回
     */
    private List<IconItem> loadCategoryItems(IconCategory category) throws Exception {
        String key = category.getFile();
        List<IconItem> cached = categoryCache.get(key);
        if (cached != null) {
            Log.d(TAG, "loadCategoryItems - 命中缓存: " + key);
            return cached;
        }

        String fileUrl = OSS_BASE + "json/" + category.getFile();
        Log.d(TAG, "loadCategoryItems - 网络加载: " + fileUrl);
        String json = fetchUrl(fileUrl);
        List<IconItem> items = parseIconItems(json);
        categoryCache.put(key, items);
        return items;
    }

    /**
     * 为分类填充前 9 张缩略图 URL
     * 注意：此方法需在 networkIO 或 computation 线程调用
     */
    private void fillCategoryThumbs(IconCategory category) {
        try {
            List<IconItem> items = loadCategoryItems(category);
            List<String> thumbs = new ArrayList<>();
            int count = Math.min(9, items.size());
            for (int i = 0; i < count; i++) {
                thumbs.add(items.get(i).getThumbUrl());
            }
            category.setThumbUrls(thumbs);
        } catch (Exception e) {
            Log.e(TAG, "fillCategoryThumbs 异常: " + e.getMessage());
        }
    }

    // ==================== 内部：关键词匹配 ====================

    private boolean matchKeyword(IconItem item, String lowerKeyword) {
        if (item.getName() != null
                && item.getName().toLowerCase(Locale.CHINA).contains(lowerKeyword)) return true;
        if (item.getPinyin() != null
                && item.getPinyin().toLowerCase(Locale.CHINA).contains(lowerKeyword)) return true;
        if (item.getInitial() != null
                && item.getInitial().toLowerCase(Locale.CHINA).startsWith(lowerKeyword)) return true;
        if (item.getCategory() != null
                && item.getCategory().toLowerCase(Locale.CHINA).contains(lowerKeyword)) return true;
        return false;
    }

    // ==================== 内部：JSON 解析 ====================

    /**
     * 解析 index.json → List<IconCategory>
     */
    private List<IconCategory> parseIndex(String json) throws Exception {
        List<IconCategory> list = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String categoryName = obj.optString("category");
            if ("json".equalsIgnoreCase(categoryName.trim())) {
                continue;
            }
            IconCategory cat = new IconCategory();
            cat.setCategory(obj.optString("category"));

            cat.setCount(obj.optInt("count"));
            cat.setFile(obj.optString("file"));
            list.add(cat);
        }
        return list;
    }

    /**
     * 解析图标 JSON（search.json / category.json）→ List<IconItem>
     */
    private List<IconItem> parseIconItems(String json) throws Exception {
        List<IconItem> list = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            IconItem item = new IconItem();
            item.setId(obj.optString("id"));
            item.setName(obj.optString("name"));
            item.setCategory(obj.optString("category"));

            // 修复：补全 URL 路径
            String url = obj.optString("url");
            if (!url.isEmpty() && !url.startsWith("http")) {
                url = OSS_BASE + url;
            }
            item.setUrl(url);

            String thumb = obj.optString("thumb");
            if (!thumb.isEmpty() && !thumb.startsWith("http")) {
                thumb = OSS_BASE + thumb;
            }
            item.setThumb(thumb);

            item.setPinyin(obj.optString("pinyin"));
            item.setInitial(obj.optString("initial"));
            list.add(item);
        }
        return list;
    }

    // ==================== 内部：网络请求 ====================

    /**
     * 同步拉取 URL 文本内容
     * 在 networkIO 线程调用，不阻塞主线程
     * 【已重构为使用 OkHttp】
     */
    private String fetchUrl(String urlStr) throws Exception {
        Request request = new Request.Builder()
                .url(urlStr)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " - " + urlStr);
            }
            if (response.body() == null) {
                throw new IOException("Empty body - " + urlStr);
            }
            return response.body().string();
        }
    }

    // ==================== 内部：线程切换工具 ====================

    private <T> void postMain(Callback<T> callback, T data) {
        if (callback == null) return;
        executors.mainThread().execute(() -> callback.onSuccess(data));
    }

    private <T> void postMainError(Callback<T> callback, String message) {
        if (callback == null) return;
        executors.mainThread().execute(() -> callback.onError(message));
    }
}