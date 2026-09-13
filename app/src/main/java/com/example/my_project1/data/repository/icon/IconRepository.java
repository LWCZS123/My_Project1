package com.example.my_project1.data.repository.icon;

import android.content.res.AssetManager;
import android.util.Log;

import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * IconRepository - 高性能图标数据仓库
 * -------------------------------------------------------
 * 职责：聚合管理 阿里云 OSS、本地 Asset、Flaticon 三种数据源。
 * 优化点：
 *   1. 统一使用 ApiResponse<T> 状态流转。
 *   2. 缩略图异步延后加载，首屏数据秒开返回。
 *   3. 消除 Future.get() 线程阻塞，使用安全的非阻塞并发。
 *   4. 双重检查锁（DCL）确保高并发下缓存不被重复重复加载。
 *   5. 代码高度提炼，抽取通用分页与异常安全边界。
 */
public class IconRepository {

    private static final String TAG = "IconRepository";

    // ==================== 常量配置 ====================
    public static final String OSS_BASE = "https://icons-classify.oss-cn-hangzhou.aliyuncs.com/";
    private static final String INDEX_URL = OSS_BASE + "json/index.json";
    private static final String SEARCH_URL = OSS_BASE + "json/search.json";
    private static final String FLATICON_URL = OSS_BASE + "json/flaticon_lineal-color-icons.json";
    public static final String THUMB_SUFFIX = "?x-oss-process=image/resize,w_100";

    public static final int PAGE_SIZE_CATEGORY = 10;
    public static final int PAGE_SIZE_DETAIL = 50;
    public static final int PAGE_SIZE_SEARCH = 200;

    // ==================== 单例与并发锁 ====================
    private static volatile IconRepository instance;
    private final Object indexLock = new Object();
    private final Object searchLock = new Object();
    private final Object flaticonLock = new Object();

    private final AppExecutors executors;
    private final OkHttpClient okHttpClient;

    // ==================== 缓存容器 ====================
    private final ConcurrentHashMap<String, List<IconItem>> categoryCache = new ConcurrentHashMap<>();
    private volatile List<IconCategory> categoryIndexCache = null;
    private volatile List<IconItem> searchCache = null;

    private final ConcurrentHashMap<String, JSONObject> assetJsonCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<IconCategory>> assetCategoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<IconItem>> assetSearchCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<IconItem>>> assetCategoryDetailCache = new ConcurrentHashMap<>();

    private int totalPacks = 0;
    private int totalIcons = 0;

    private volatile JSONObject flaticonJsonCache = null;
    private volatile List<IconCategory> flaticonCategoryCache = null;
    private final ConcurrentHashMap<String, List<IconItem>> flaticonCategoryDetailCache = new ConcurrentHashMap<>();
    private volatile List<IconItem> flaticonSearchCache = null;

    // ==================== 回调接口 ====================
    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

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

    public void clearCache() {
        synchronized (indexLock) {
            categoryIndexCache = null;
        }
        synchronized (searchLock) {
            searchCache = null;
        }
        synchronized (flaticonLock) {
            flaticonJsonCache = null;
            flaticonCategoryCache = null;
            flaticonSearchCache = null;
        }
        totalPacks = 0;
        totalIcons = 0;
        categoryCache.clear();
        assetJsonCache.clear();
        assetCategoryCache.clear();
        assetSearchCache.clear();
        assetCategoryDetailCache.clear();
        flaticonCategoryDetailCache.clear();
        Log.d(TAG, "clearCache: 内存缓存已全部清空");
    }

    public int getTotalPacks() { return totalPacks; }
    public int getTotalIcons() { return totalIcons; }

    // ==================== 核心业务：分类市集首页 ====================

    public void getCategoryPage(int page, Callback<List<IconCategory>> callback) {
        // Re-entry fast path: the index is process-cached, so do not enqueue a
        // network worker or rebuild the merged source list.
        List<IconCategory> cachedIndex = categoryIndexCache;
        if (cachedIndex != null) {
            List<IconCategory> pageData = paginate(cachedIndex, page, PAGE_SIZE_CATEGORY);
            dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));
            
            // 即使命中缓存，也要确保这页的缩略图是加载过的
            executors.computation().execute(() -> {
                boolean updated = false;
                for (IconCategory cat : pageData) {
                    if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                        if (cat.getFile() != null && cat.getFile().startsWith("flaticon:")) {
                            fillFlaticonCategoryThumbs(cat);
                        } else {
                            fillCategoryThumbs(cat);
                        }
                        updated = true;
                    }
                }
                if (updated) {
                    executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                }
            });
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                if (categoryIndexCache == null) {
                    synchronized (indexLock) {
                        if (categoryIndexCache == null) {
                            List<IconCategory> merged = new ArrayList<>();
                            try {
                                String json = fetchUrl(INDEX_URL);
                                merged.addAll(parseIndex(json));
                            } catch (Exception e) {
                                Log.e(TAG, "加载 INDEX_URL 失败", e);
                            }
                            try {
                                merged.addAll(loadFlaticonCategoriesInternal());
                            } catch (Exception e) {
                                Log.e(TAG, "加载 Flaticon 失败", e);
                            }
                            categoryIndexCache = merged;
                        }
                    }
                }

                List<IconCategory> pageData = paginate(categoryIndexCache, page, PAGE_SIZE_CATEGORY);
                
                // 【性能飞跃点】首屏纯列表数据不带图片秒开返回，绝不阻塞 UI
                dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));

                // 异步延后去抓取前9张缩略图，完成后触发增量差分刷新
                executors.computation().execute(() -> {
                    boolean updated = false;
                    for (IconCategory cat : pageData) {
                        if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                            if (cat.getFile() != null && cat.getFile().startsWith("flaticon:")) {
                                fillFlaticonCategoryThumbs(cat);
                            } else {
                                fillCategoryThumbs(cat);
                            }
                            updated = true;
                        }
                    }
                    if (updated) {
                        executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                    }
                });

            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载分类列表失败"));
            }
        });
    }

    public void getCategoryDetail(IconCategory category, int page, Callback<List<IconItem>> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadCategoryItems(category);
                dispatchResult(callback, ApiResponse.success(paginate(all, page, PAGE_SIZE_DETAIL)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载图标详情失败"));
            }
        });
    }

    public void getCategoryPageCount(IconCategory category, Callback<Integer> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadCategoryItems(category);
                int total = (int) Math.ceil((double) all.size() / PAGE_SIZE_DETAIL);
                dispatchResult(callback, ApiResponse.success(total));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error(e.getMessage()));
            }
        });
    }

    // ==================== 核心业务：Asset 数据源支持 ====================

    public void getAssetCategoryPage(AssetManager assets, String fileName, int page, Callback<List<IconCategory>> callback) {
        Log.d(TAG, "getAssetCategoryPage: " + fileName + ", Page: " + page);
        List<IconCategory> cached = assetCategoryCache.get(fileName);
        if (cached != null) {
            List<IconCategory> pageData = paginate(cached, page, PAGE_SIZE_CATEGORY);
            Log.d(TAG, "getAssetCategoryPage Cache Hit: " + fileName + ", Items: " + pageData.size());
            dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));
            
            // 即使命中缓存，也要确保这页的缩略图是加载过的
            executors.computation().execute(() -> {
                boolean updated = false;
                for (IconCategory cat : pageData) {
                    if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                        fillAssetCategoryThumbs(assets, fileName, cat);
                        updated = true;
                    }
                }
                if (updated) {
                    Log.d(TAG, "getAssetCategoryPage Thumbs Updated for: " + fileName + ", Page: " + page);
                    executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                }
            });
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                List<IconCategory> all = loadAssetCategories(assets, fileName);
                List<IconCategory> pageData = paginate(all, page, PAGE_SIZE_CATEGORY);
                
                dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));

                executors.computation().execute(() -> {
                    boolean updated = false;
                    for (IconCategory cat : pageData) {
                        if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                            fillAssetCategoryThumbs(assets, fileName, cat);
                            updated = true;
                        }
                    }
                    if (updated) {
                        executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                    }
                });
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载本地分类失败"));
            }
        });
    }

    public void getAssetCategoryDetail(AssetManager assets, String fileName, IconCategory category, int page, Callback<List<IconItem>> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadAssetCategoryItems(assets, fileName, category.getFile());
                dispatchResult(callback, ApiResponse.success(paginate(all, page, PAGE_SIZE_DETAIL)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载本地图标详情失败"));
            }
        });
    }

    public void getAssetCategoryPageCount(AssetManager assets, String fileName, IconCategory category, Callback<Integer> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadAssetCategoryItems(assets, fileName, category.getFile());
                int total = (int) Math.ceil((double) all.size() / PAGE_SIZE_DETAIL);
                dispatchResult(callback, ApiResponse.success(total));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error(e.getMessage()));
            }
        });
    }

    public void searchAsset(AssetManager assets, String fileName, String keyword, int page, Callback<List<IconItem>> callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            dispatchResult(callback, ApiResponse.success(new ArrayList<>()));
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> allItems = assetSearchCache.get(fileName);
                if (allItems == null) {
                    allItems = new ArrayList<>();
                    List<IconCategory> categories = loadAssetCategories(assets, fileName);
                    for (IconCategory cat : categories) {
                        allItems.addAll(loadAssetCategoryItems(assets, fileName, cat.getFile()));
                    }
                    assetSearchCache.put(fileName, allItems);
                }

                String lowerKeyword = keyword.trim().toLowerCase(Locale.CHINA);
                List<IconItem> results = new ArrayList<>();
                for (IconItem item : allItems) {
                    if (matchKeyword(item, lowerKeyword)) {
                        results.add(item);
                    }
                }
                dispatchResult(callback, ApiResponse.success(paginate(results, page, PAGE_SIZE_SEARCH)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("搜索本地图标失败"));
            }
        });
    }

    // ==================== 核心业务：Flaticon 独立源支持 ====================

    public void getFlaticonCategoryPage(int page, Callback<List<IconCategory>> callback) {
        if (flaticonCategoryCache != null) {
            List<IconCategory> pageData = paginate(flaticonCategoryCache, page, PAGE_SIZE_CATEGORY);
            dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));
            
            executors.computation().execute(() -> {
                boolean updated = false;
                for (IconCategory cat : pageData) {
                    if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                        fillFlaticonCategoryThumbs(cat);
                        updated = true;
                    }
                }
                if (updated) {
                    executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                }
            });
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                List<IconCategory> all = loadFlaticonCategoriesInternal();
                List<IconCategory> pageData = paginate(all, page, PAGE_SIZE_CATEGORY);
                dispatchResult(callback, ApiResponse.success(new ArrayList<>(pageData)));

                executors.computation().execute(() -> {
                    boolean updated = false;
                    for (IconCategory cat : pageData) {
                        if (cat.getThumbUrls() == null || cat.getThumbUrls().isEmpty()) {
                            fillFlaticonCategoryThumbs(cat);
                            updated = true;
                        }
                    }
                    if (updated) {
                        executors.mainThread().execute(() -> callback.onSuccess(new ArrayList<>(pageData)));
                    }
                });
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载 Flaticon 分类失败"));
            }
        });
    }

    public void getFlaticonCategoryDetail(IconCategory category, int page, Callback<List<IconItem>> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadFlaticonCategoryItemsInternal(category.getFile());
                dispatchResult(callback, ApiResponse.success(paginate(all, page, PAGE_SIZE_DETAIL)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("加载 Flaticon 图标详情失败"));
            }
        });
    }

    public void getFlaticonCategoryPageCount(IconCategory category, Callback<Integer> callback) {
        executors.networkIO().execute(() -> {
            try {
                List<IconItem> all = loadFlaticonCategoryItemsInternal(category.getFile());
                int total = (int) Math.ceil((double) all.size() / PAGE_SIZE_DETAIL);
                dispatchResult(callback, ApiResponse.success(total));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error(e.getMessage()));
            }
        });
    }

    public void searchFlaticon(String keyword, int page, Callback<List<IconItem>> callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            dispatchResult(callback, ApiResponse.success(new ArrayList<>()));
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                ensureFlaticonSearchCacheBuilt();
                String lowerKeyword = keyword.trim().toLowerCase(Locale.CHINA);
                List<IconItem> results = new ArrayList<>();
                for (IconItem item : flaticonSearchCache) {
                    if (matchKeyword(item, lowerKeyword)) {
                        results.add(item);
                    }
                }
                dispatchResult(callback, ApiResponse.success(paginate(results, page, PAGE_SIZE_SEARCH)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("搜索 Flaticon 图标失败"));
            }
        });
    }

    // ==================== 全量业务：全局聚合检索 ====================

    public void search(String keyword, int page, Callback<List<IconItem>> callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            dispatchResult(callback, ApiResponse.success(new ArrayList<>()));
            return;
        }
        executors.networkIO().execute(() -> {
            try {
                if (searchCache == null) {
                    synchronized (searchLock) {
                        if (searchCache == null) {
                            List<IconItem> merged = new ArrayList<>();
                            try {
                                String json = fetchUrl(SEARCH_URL);
                                merged.addAll(parseIconItems(json));
                            } catch (Exception e) {
                                Log.e(TAG, "加载全球搜索索引失败", e);
                            }
                            try {
                                ensureFlaticonSearchCacheBuilt();
                                merged.addAll(flaticonSearchCache);
                            } catch (Exception e) {
                                Log.e(TAG, "构建 Flaticon 搜索高速缓存失败", e);
                            }
                            searchCache = merged;
                        }
                    }
                }

                String lowerKeyword = keyword.trim().toLowerCase(Locale.CHINA);
                List<IconItem> allResults = new ArrayList<>();
                for (IconItem item : searchCache) {
                    if (matchKeyword(item, lowerKeyword)) {
                        allResults.add(item);
                    }
                }
                dispatchResult(callback, ApiResponse.success(paginate(allResults, page, PAGE_SIZE_SEARCH)));
            } catch (Exception e) {
                dispatchResult(callback, ApiResponse.error("全局搜索失败"));
            }
        });
    }

    // ==================== 内部实现：高内聚底层子源加载器 ====================

    private List<IconCategory> loadFlaticonCategoriesInternal() throws Exception {
        if (flaticonCategoryCache != null) return flaticonCategoryCache;
        synchronized (flaticonLock) {
            if (flaticonCategoryCache != null) return flaticonCategoryCache;
            JSONObject root = getFlaticonJsonObject();
            
            totalPacks += root.optInt("total_packs", 0);
            totalIcons += root.optInt("total_icons", 0);

            JSONObject packs = root.getJSONObject("packs");
            List<IconCategory> list = new ArrayList<>();
            Iterator<String> keys = packs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject pack = packs.getJSONObject(key);
                IconCategory cat = new IconCategory();
                cat.setCategory(pack.optString("title_zh", pack.optString("title")));
                cat.setCount(pack.optInt("icon_count"));
                cat.setFile("flaticon:" + key);
                cat.setStyle("filled");
                list.add(cat);
            }
            flaticonCategoryCache = list;
            return list;
        }
    }

    private List<IconItem> loadFlaticonCategoryItemsInternal(String packKey) throws Exception {
        String realKey = (packKey != null && packKey.startsWith("flaticon:")) ? packKey.replace("flaticon:", "") : packKey;
        List<IconItem> cached = flaticonCategoryDetailCache.get(realKey);
        if (cached != null) return cached;

        JSONObject root = getFlaticonJsonObject();
        JSONObject pack = root.getJSONObject("packs").getJSONObject(realKey);
        JSONArray icons = pack.getJSONArray("icons");
        List<IconItem> list = new ArrayList<>();
        for (int i = 0; i < icons.length(); i++) {
            JSONObject obj = icons.getJSONObject(i);
            IconItem item = new IconItem();
            item.setId("flaticon_" + realKey + "_" + obj.optString("id", String.valueOf(i)));
            item.setName(obj.optString("name_zh", obj.optString("name")));
            item.setCategory(pack.optString("title_zh", pack.optString("title")));
            String cdnUrl = obj.optString("cdn_url");
            item.setUrl(cdnUrl);
            item.setThumb(cdnUrl); // 缩略图由 Glide 在客户端动态缩放，不在此处强加 OSS 参数
            list.add(item);
        }
        flaticonCategoryDetailCache.put(realKey, list);
        return list;
    }

    private void ensureFlaticonSearchCacheBuilt() throws Exception {
        if (flaticonSearchCache != null) return;
        synchronized (flaticonLock) {
            if (flaticonSearchCache != null) return;
            List<IconItem> allItems = new ArrayList<>();
            List<IconCategory> categories = loadFlaticonCategoriesInternal();
            for (IconCategory cat : categories) {
                allItems.addAll(loadFlaticonCategoryItemsInternal(cat.getFile()));
            }
            flaticonSearchCache = allItems;
        }
    }

    private List<IconItem> loadCategoryItems(IconCategory category) throws Exception {
        String key = category.getFile();
        if (key == null || key.isEmpty()) {
            throw new Exception("分类数据文件路径为空");
        }
        if (key.startsWith("flaticon:")) {
            return loadFlaticonCategoryItemsInternal(key);
        }
        List<IconItem> cached = categoryCache.get(key);
        if (cached != null) return cached;

        String url = OSS_BASE + "json/" + key;
        try {
            String json = fetchUrl(url);
            List<IconItem> items = parseIconItems(json);
            categoryCache.put(key, items);
            return items;
        } catch (Exception e) {
            Log.e(TAG, "loadCategoryItems 失败: " + url, e);
            throw e;
        }
    }

    private List<IconCategory> loadAssetCategories(AssetManager assets, String fileName) throws Exception {
        List<IconCategory> cached = assetCategoryCache.get(fileName);
        if (cached != null) return cached;

        JSONObject root = getAssetJsonObject(assets, fileName);
        
        totalPacks += root.optInt("total_packs", 0);
        totalIcons += root.optInt("total_icons", 0);

        String rootStyle = root.optString("style", "filled");
        JSONObject packs = root.getJSONObject("packs");
        List<IconCategory> list = new ArrayList<>();
        Iterator<String> keys = packs.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject pack = packs.getJSONObject(key);
            IconCategory cat = new IconCategory();
            cat.setCategory(pack.optString("title"));
            cat.setCount(pack.optInt("icon_count"));
            cat.setFile(key);
            cat.setStyle(pack.optString("style", rootStyle));
            list.add(cat);
        }
        assetCategoryCache.put(fileName, list);
        return list;
    }

    private List<IconItem> loadAssetCategoryItems(AssetManager assets, String fileName, String packKey) throws Exception {
        if (packKey == null || packKey.isEmpty()) {
            throw new Exception("Asset PackKey 为空");
        }
        Log.d(TAG, "loadAssetCategoryItems: File=" + fileName + ", PackKey=" + packKey);
        ConcurrentHashMap<String, List<IconItem>> packCache = assetCategoryDetailCache.get(fileName);
        if (packCache == null) {
            packCache = new ConcurrentHashMap<>();
            assetCategoryDetailCache.putIfAbsent(fileName, packCache);
            packCache = assetCategoryDetailCache.get(fileName);
        }
        List<IconItem> cached = packCache.get(packKey);
        if (cached != null) {
            Log.d(TAG, "loadAssetCategoryItems Cache Hit: " + packKey + ", Icons: " + cached.size());
            return cached;
        }

        JSONObject root = getAssetJsonObject(assets, fileName);
        JSONObject packs = root.getJSONObject("packs");
        if (!packs.has(packKey)) {
            Log.e(TAG, "Asset 中找不到 Pack: " + packKey + " in file: " + fileName);
            // 打印出前几个 key 帮助调试
            Iterator<String> keys = packs.keys();
            StringBuilder sb = new StringBuilder("Available keys: ");
            for(int i=0; i<5 && keys.hasNext(); i++) sb.append(keys.next()).append(", ");
            Log.e(TAG, sb.toString());
            throw new Exception("Asset 中找不到 Pack: " + packKey);
        }
        JSONObject pack = packs.getJSONObject(packKey);
        JSONArray icons = pack.getJSONArray("icons");
        List<IconItem> list = new ArrayList<>();
        for (int i = 0; i < icons.length(); i++) {
            JSONObject obj = icons.getJSONObject(i);
            IconItem item = new IconItem();
            item.setId(packKey + "_" + i);
            item.setName(obj.optString("name"));
            item.setCategory(pack.optString("title"));
            item.setUrl(obj.optString("cdn_url"));
            item.setThumb(obj.optString("cdn_url"));
            list.add(item);
        }
        packCache.put(packKey, list);
        Log.d(TAG, "loadAssetCategoryItems Loaded: " + packKey + ", Icons: " + list.size());
        return list;
    }

    private JSONObject getAssetJsonObject(AssetManager assets, String fileName) throws Exception {
        JSONObject cached = assetJsonCache.get(fileName);
        if (cached != null) return cached;
        String json = readAssetString(assets, fileName);
        JSONObject root = new JSONObject(json);
        assetJsonCache.put(fileName, root);
        return root;
    }

    private JSONObject getFlaticonJsonObject() throws Exception {
        if (flaticonJsonCache != null) return flaticonJsonCache;
        String json = fetchUrl(FLATICON_URL);
        flaticonJsonCache = new JSONObject(json);
        return flaticonJsonCache;
    }

    // ==================== 缩略图前置渲染提取器 ====================

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
            Log.e(TAG, "fillCategoryThumbs 异常", e);
        }
    }

    private void fillFlaticonCategoryThumbs(IconCategory category) {
        try {
            List<IconItem> items = loadFlaticonCategoryItemsInternal(category.getFile());
            List<String> thumbs = new ArrayList<>();
            int count = Math.min(9, items.size());
            for (int i = 0; i < count; i++) {
                thumbs.add(items.get(i).getThumbUrl());
            }
            category.setThumbUrls(thumbs);
        } catch (Exception e) {
            Log.e(TAG, "fillFlaticonCategoryThumbs 异常", e);
        }
    }

    private void fillAssetCategoryThumbs(AssetManager assets, String fileName, IconCategory category) {
        try {
            Log.d(TAG, "Filling thumbs for asset category: " + category.getCategory() + " from file: " + fileName);
            List<IconItem> items = loadAssetCategoryItems(assets, fileName, category.getFile());
            List<String> thumbs = new ArrayList<>();
            int count = Math.min(9, items.size());
            for (int i = 0; i < count; i++) {
                thumbs.add(items.get(i).getThumbUrl());
            }
            category.setThumbUrls(thumbs);
            Log.d(TAG, "Filled " + thumbs.size() + " thumbs for " + category.getCategory() + ". First URL: " + (thumbs.isEmpty() ? "none" : thumbs.get(0)));
        } catch (Exception e) {
            Log.e(TAG, "fillAssetCategoryThumbs 异常", e);
        }
    }

    // ==================== 工具类内部私有方法 ====================

    private <T> List<T> paginate(List<T> all, int page, int pageSize) {
        int start = page * pageSize;
        if (all == null || start >= all.size()) return new ArrayList<>();
        int end = Math.min(start + pageSize, all.size());
        return new ArrayList<>(all.subList(start, end));
    }

    private boolean matchKeyword(IconItem item, String lowerKeyword) {
        return (item.getName() != null && item.getName().toLowerCase(Locale.CHINA).contains(lowerKeyword))
                || (item.getPinyin() != null && item.getPinyin().toLowerCase(Locale.CHINA).contains(lowerKeyword))
                || (item.getInitial() != null && item.getInitial().toLowerCase(Locale.CHINA).startsWith(lowerKeyword))
                || (item.getCategory() != null && item.getCategory().toLowerCase(Locale.CHINA).contains(lowerKeyword));
    }

    private List<IconCategory> parseIndex(String json) throws Exception {
        List<IconCategory> list = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        
        // OSS index.json 暂时没有 root 统计字段，按实际列表估算
        totalPacks += arr.length();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String name = obj.optString("category");
            if (name == null || "json".equalsIgnoreCase(name.trim())) continue;

            IconCategory cat = new IconCategory();
            cat.setCategory(name);
            int count = obj.optInt("count");
            cat.setCount(count);
            totalIcons += count;
            cat.setFile(obj.optString("file"));
            cat.setStyle("filled"); // OSS index 默认为 filled
            list.add(cat);
        }
        return list;
    }

    private List<IconItem> parseIconItems(String json) throws Exception {
        List<IconItem> list = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            IconItem item = new IconItem();
            item.setId(obj.optString("id"));
            item.setName(obj.optString("name"));
            item.setCategory(obj.optString("category"));

            // Category payloads are not fully uniform: CDN-backed packs use
            // cdn_url while the search index uses url/thumb.
            String url = obj.optString("url", obj.optString("cdn_url"));
            if (!url.isEmpty() && !url.startsWith("http")) url = OSS_BASE + url;
            item.setUrl(url);

            String thumb = obj.optString("thumb", obj.optString("cdn_url"));
            if (!thumb.isEmpty() && !thumb.startsWith("http")) thumb = OSS_BASE + thumb;
            item.setThumb(thumb);

            item.setPinyin(obj.optString("pinyin"));
            item.setInitial(obj.optString("initial"));
            list.add(item);
        }
        return list;
    }

    private String fetchUrl(String urlStr) throws Exception {
        Request request = new Request.Builder().url(urlStr).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " - " + urlStr);
            }
            return response.body().string();
        }
    }

    private String readAssetString(AssetManager assets, String fileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(assets.open(fileName)))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private <T> void dispatchResult(Callback<T> callback, ApiResponse<T> response) {
        if (callback == null) return;
        executors.mainThread().execute(() -> {
            if (response.isSuccess()) {
                callback.onSuccess(response.getData());
            } else {
                callback.onError(response.getMessage());
            }
        });
    }
}
