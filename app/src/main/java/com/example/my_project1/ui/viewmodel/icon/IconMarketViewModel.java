package com.example.my_project1.ui.viewmodel.icon;

import android.app.Application;
import android.util.Log;
import android.util.SparseArray;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SubCategoryDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.remote.BmobApiImpl;
import com.example.my_project1.data.repository.CategoryRepository;
import com.example.my_project1.data.repository.SubCategoryRepository;
import com.example.my_project1.data.repository.icon.IconRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;

public class IconMarketViewModel extends AndroidViewModel {

    private static final String TAG = "IconMarketViewModel";

    public enum PageStatus { IDLE, LOADING, LOADED, ERROR }

    public enum IconStyle {
        ALL, DEFAULT, LINEAR, COLORED
    }

    private final MutableLiveData<IconStyle> _currentStyle = new MutableLiveData<>(IconStyle.ALL);
    public  final LiveData<IconStyle>         currentStyle  = _currentStyle;

    private final MutableLiveData<List<IconCategory>> _allLoadedCategories = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<IconCategory> _hotCategory = new MutableLiveData<>();
    public  final LiveData<IconCategory> hotCategory = _hotCategory;

    private final MutableLiveData<Statistics> _statistics = new MutableLiveData<>(new Statistics(0, 0, 0));
    public  final LiveData<Statistics> statistics = _statistics;

    public static class Statistics {
        public final int totalIcons;
        public final int totalCollections;
        public final int newThisWeek;

        public Statistics(int totalIcons, int totalCollections, int newThisWeek) {
            this.totalIcons = totalIcons;
            this.totalCollections = totalCollections;
            this.newThisWeek = newThisWeek;
        }
    }

    public static class PageState {
        public final PageStatus status;
        public final List<IconItem> items;
        public final String error;

        public PageState(PageStatus status, List<IconItem> items, String error) {
            this.status = status;
            this.items = items;
            this.error = error;
        }

        public static PageState idle() { return new PageState(PageStatus.IDLE, null, null); }
        public static PageState loading() { return new PageState(PageStatus.LOADING, null, null); }
        public static PageState loaded(List<IconItem> items) { return new PageState(PageStatus.LOADED, items, null); }
        public static PageState error(String error) { return new PageState(PageStatus.ERROR, null, error); }
    }

    private final SparseArray<PageState> pageStates = new SparseArray<>();
    private final Set<Integer> inflightPages = new HashSet<>();

    private final MutableLiveData<SparseArray<PageState>> _detailPageStates = new MutableLiveData<>();
    public  final LiveData<SparseArray<PageState>> detailPageStates = _detailPageStates;

    private final MutableLiveData<Integer> _detailTotalPages = new MutableLiveData<>(0);
    public  final LiveData<Integer>         detailTotalPages  = _detailTotalPages;

    private final MutableLiveData<Boolean> _detailLoading = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         detailLoading  = _detailLoading;

    private final MutableLiveData<String>  _detailError = new MutableLiveData<>();
    public  final LiveData<String>          detailError  = _detailError;

    private final MutableLiveData<IconCategory> _selectedCategory = new MutableLiveData<>();
    public  final LiveData<IconCategory> selectedCategory = _selectedCategory;

    private final MutableLiveData<List<IconCategory>> _categories = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<IconCategory>>         categories  = _categories;

    private final MutableLiveData<List<IconCategory>> _categorySearchResults = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<IconCategory>>         categorySearchResults  = _categorySearchResults;

    private final MutableLiveData<Boolean> _categoryLoading = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         categoryLoading  = _categoryLoading;

    private final MutableLiveData<Boolean> _categoryLoadingMore = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         categoryLoadingMore  = _categoryLoadingMore;

    private final MutableLiveData<Boolean> _categoryHasMore = new MutableLiveData<>(true);
    public  final LiveData<Boolean>         categoryHasMore      = _categoryHasMore;

    private final MutableLiveData<String>  _categoryError = new MutableLiveData<>();
    public  final LiveData<String>          categoryError  = _categoryError;

    private int categoryPage = 0;
    private boolean isCategoryLoadingMore = false;

    private final MutableLiveData<List<IconItem>> _searchResults = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<IconItem>>         searchResults  = _searchResults;

    private final MutableLiveData<Boolean> _searchLoading     = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         searchLoading      = _searchLoading;

    private final MutableLiveData<Boolean> _searchLoadingMore = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         searchLoadingMore  = _searchLoadingMore;

    private final MutableLiveData<Boolean> _searchHasMore     = new MutableLiveData<>(true);
    public  final LiveData<Boolean>         searchHasMore      = _searchHasMore;

    private final MutableLiveData<String>  _currentKeyword = new MutableLiveData<>("");
    public  final LiveData<String>          currentKeyword  = _currentKeyword;

    private final MutableLiveData<String>  _searchError = new MutableLiveData<>();
    public  final LiveData<String>          searchError  = _searchError;

    private final MutableLiveData<Integer> _searchScope = new MutableLiveData<>(1);
    public  final LiveData<Integer>         searchScope  = _searchScope;

    private int     searchPage          = 0;
    private boolean isSearchLoadingMore = false;
    private String  lastKeyword         = "";

    public final SelectionManager selectionManager = new SelectionManager();

    public static class SaveResult {
        public final boolean success;
        public final int     savedCount;
        public final int     skipCount;
        public final String  message;

        public SaveResult(boolean success, int savedCount, int skipCount, String message) {
            this.success    = success;
            this.savedCount = savedCount;
            this.skipCount  = skipCount;
            this.message    = message;
        }
    }

    private final MutableLiveData<SaveResult> _saveResult = new MutableLiveData<>();
    public  final LiveData<SaveResult>         saveResult  = _saveResult;

    private final MutableLiveData<Boolean> _saving = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         saving  = _saving;

    private final IconRepository        repository;
    private final CategoryRepository    categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    private final CategoryDao    categoryDao;
    private final SubCategoryDao subCategoryDao;
    private final BmobApiImpl    bmobApi;

    private String currentUserId = "";

    public IconMarketViewModel(Application application) {
        super(application);
        repository            = IconRepository.getInstance();
        categoryRepository    = new CategoryRepository(application);
        subCategoryRepository = new SubCategoryRepository(application);

        AppDatabase db = AppDatabase.getInstance(application);
        categoryDao    = db.categoryDao();
        subCategoryDao = db.subCategoryDao();
        bmobApi        = new BmobApiImpl(application);

        initCurrentUserId();
    }

    private void initCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser();
        currentUserId = (user != null) ? user.getObjectId() : "";
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId != null ? userId : "";
    }

    public void switchStyle(IconStyle style) {
        if (_currentStyle.getValue() == style) return;
        _currentStyle.setValue(style);
        applyFilters();
    }

    private void applyFilters() {
        List<IconCategory> all = _allLoadedCategories.getValue();
        if (all == null) {
            _categories.setValue(new ArrayList<>());
            return;
        }

        String styleFilter = getStyleFilterString();
        String keyword = _currentKeyword.getValue();
        Integer scope = _searchScope.getValue();
        if (scope == null) scope = 0;

        List<IconCategory> filtered = new ArrayList<>();

        for (IconCategory cat : all) {
            boolean styleMatch = (styleFilter == null || styleFilter.equals(cat.getStyle()));
            if (!styleMatch) continue;

            if (scope == 1 && keyword != null && !keyword.isEmpty()) {
                if (cat.getCategory() != null && cat.getCategory().toLowerCase().contains(keyword.toLowerCase())) {
                    filtered.add(cat);
                }
            } else {
                filtered.add(cat);
            }
        }

        _categories.setValue(filtered);
        _categorySearchResults.setValue(filtered);
        _statistics.setValue(new Statistics(repository.getTotalIcons(), repository.getTotalPacks(), 38)); 
        
        if (!filtered.isEmpty() && _hotCategory.getValue() == null) {
            int randomIndex = (int) (Math.random() * filtered.size());
            _hotCategory.setValue(filtered.get(randomIndex));
        }
    }

    private String getStyleFilterString() {
        IconStyle style = _currentStyle.getValue();
        if (style == IconStyle.LINEAR) return "line";
        if (style == IconStyle.COLORED) return "lineal-color";
        if (style == IconStyle.DEFAULT) return "filled";
        return null;
    }

    private String getStyleFileName() {
        IconStyle style = _currentStyle.getValue();
        if (style == IconStyle.LINEAR) return "freeicon_line.json";
        if (style == IconStyle.COLORED) return "线性色.json";
        return null;
    }

    private String getCategoryFileName(IconCategory category) {
        if (category == null) return null;
        String style = category.getStyle();
        if ("line".equals(style)) return "freeicon_line.json";
        if ("lineal-color".equals(style)) return "线性色.json";
        return null;
    }

    public void openCategory(IconCategory category) {
        _selectedCategory.setValue(category);
        pageStates.clear();
        inflightPages.clear();
        _detailTotalPages.setValue(0);
        _detailLoading.setValue(true);
        notifyPageStatesChanged();

        String fileName = getCategoryFileName(category);
        if (fileName == null) {
            repository.getCategoryPageCount(category, new IconRepository.Callback<Integer>() {
                @Override public void onSuccess(Integer totalPages) { _detailTotalPages.setValue(totalPages); }
                @Override public void onError(String message)       { Log.e(TAG, "Fail: " + message); }
            });
        } else {
            repository.getAssetCategoryPageCount(getApplication().getAssets(), fileName, category, new IconRepository.Callback<Integer>() {
                @Override public void onSuccess(Integer totalPages) { _detailTotalPages.setValue(totalPages); }
                @Override public void onError(String message)       { Log.e(TAG, "Fail: " + message); }
            });
        }
        loadDetailPage(category, 0);
    }

    public void loadDetailPage(IconCategory category, int page) {
        if (category == null) return;
        Integer total = _detailTotalPages.getValue();
        if (total != null && total > 0 && page >= total) return;
        PageState existing = pageStates.get(page);
        if (existing != null && (existing.status == PageStatus.LOADING || existing.status == PageStatus.LOADED)) return;
        if (inflightPages.contains(page)) return;

        inflightPages.add(page);
        pageStates.put(page, PageState.loading());
        notifyPageStatesChanged();
        refreshGlobalLoadingState();

        String fileName = getCategoryFileName(category);
        if (fileName == null) {
            repository.getCategoryDetail(category, page, new IconRepository.Callback<List<IconItem>>() {
                @Override public void onSuccess(List<IconItem> data) {
                    inflightPages.remove(page);
                    pageStates.put(page, PageState.loaded(data));
                    notifyPageStatesChanged();
                    refreshGlobalLoadingState();
                }
                @Override public void onError(String message) {
                    inflightPages.remove(page);
                    pageStates.put(page, PageState.error(message));
                    notifyPageStatesChanged();
                    refreshGlobalLoadingState();
                    _detailError.setValue(message);
                }
            });
        } else {
            repository.getAssetCategoryDetail(getApplication().getAssets(), fileName, category, page, new IconRepository.Callback<List<IconItem>>() {
                @Override public void onSuccess(List<IconItem> data) {
                    inflightPages.remove(page);
                    pageStates.put(page, PageState.loaded(data));
                    notifyPageStatesChanged();
                    refreshGlobalLoadingState();
                }
                @Override public void onError(String message) {
                    inflightPages.remove(page);
                    pageStates.put(page, PageState.error(message));
                    notifyPageStatesChanged();
                    refreshGlobalLoadingState();
                    _detailError.setValue(message);
                }
            });
        }
    }

    public void onIconLongClick(String iconId)  { selectionManager.enterMultiSelectMode(iconId); }
    public void onIconClick(String iconId) {
        if (Boolean.TRUE.equals(selectionManager.multiSelectMode.getValue()))
            selectionManager.toggle(iconId);
    }
    public void toggleSelectAll(List<String> allIconIds) { selectionManager.toggleSelectAll(allIconIds); }
    public void exitMultiSelectMode()                    { selectionManager.exitMultiSelectMode(); }

    public LiveData<List<CategoryWithSubCategories>> getCategoriesByType(String categoryType) {
        if (currentUserId == null || currentUserId.isEmpty()) return new MutableLiveData<>(new ArrayList<>());
        return categoryRepository.getCategoriesWithSubs(currentUserId, categoryType);
    }

    public void saveAsFirstLevelCategory(List<IconItem> selectedItems, String categoryType) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            _saveResult.setValue(new SaveResult(false, 0, 0, "请先选择图标"));
            return;
        }
        if (Boolean.TRUE.equals(_saving.getValue())) return;
        _saving.setValue(true);
        final String type = (categoryType != null && !categoryType.isEmpty()) ? categoryType : "expense";

        AppExecutors.get().diskIO().execute(() -> {
            List<Category> toInsert = new ArrayList<>();
            int skipCount = 0;
            for (IconItem item : selectedItems) {
                Category existing = categoryRepository.getCategoryByNameAndUser(item.getName(), currentUserId);
                if (existing != null) { skipCount++; continue; }
                Category cat = new Category();
                cat.setName(item.getName());
                cat.setIconUri(item.getUrl());
                cat.setType(type);
                cat.setOwnerId(currentUserId);
                cat.setSyncState(SyncState.TO_CREATE.getValue());
                toInsert.add(cat);
            }
            if (toInsert.isEmpty()) {
                _saving.postValue(false);
                _saveResult.postValue(new SaveResult(true, 0, skipCount, "全部分类已存在，已跳过"));
                return;
            }
            long[] insertedIds = categoryDao.insertCategories(toInsert);
            for (int i = 0; i < insertedIds.length && i < toInsert.size(); i++) {
                toInsert.get(i).setId(insertedIds[i]);
            }
            batchUploadCategoriesToCloud(toInsert);
            _saving.postValue(false);
            _saveResult.postValue(new SaveResult(true, toInsert.size(), skipCount, "成功保存 " + toInsert.size() + " 个分类"));
        });
    }

    public void saveAsSecondLevelCategory(List<IconItem> selectedItems, long parentCategoryId) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            _saveResult.setValue(new SaveResult(false, 0, 0, "请先选择图标"));
            return;
        }
        if (Boolean.TRUE.equals(_saving.getValue())) return;
        _saving.setValue(true);

        AppExecutors.get().diskIO().execute(() -> {
            Category parent = categoryDao.getCategoryById(parentCategoryId);
            if (parent == null) {
                _saving.postValue(false);
                _saveResult.postValue(new SaveResult(false, 0, 0, "父分类不存在"));
                return;
            }
            List<SubCategory> toInsert = new ArrayList<>();
            int skipCount = 0;
            for (IconItem item : selectedItems) {
                SubCategory existing = subCategoryRepository.getSubCategoryByParentAndName(parentCategoryId, item.getName());
                if (existing != null) { skipCount++; continue; }
                SubCategory sub = new SubCategory();
                sub.setName(item.getName());
                sub.setIconUri(item.getUrl());
                sub.setParentCategoryId(parentCategoryId);
                sub.setParentCloudId(parent.getCloudId());
                sub.setOwnerId(currentUserId);
                sub.setSyncState(SyncState.TO_CREATE.getValue());
                toInsert.add(sub);
            }
            if (toInsert.isEmpty()) {
                _saving.postValue(false);
                _saveResult.postValue(new SaveResult(true, 0, skipCount, "全部子分类已存在，已跳过"));
                return;
            }
            subCategoryDao.insertAll(toInsert);
            batchUploadSubCategoriesToCloud(toInsert, parent.getCloudId());
            _saving.postValue(false);
            _saveResult.postValue(new SaveResult(true, toInsert.size(), skipCount, "成功保存 " + toInsert.size() + " 个子分类"));
        });
    }

    private void batchUploadCategoriesToCloud(List<Category> categories) {
        final CountDownLatch latch = new CountDownLatch(categories.size());
        final AtomicInteger successCount = new AtomicInteger(0);
        for (Category cat : categories) {
            bmobApi.uploadCategory(cat, new SaveListener<String>() {
                @Override
                public void done(String cloudId, BmobException e) {
                    if (e == null) {
                        cat.setCloudId(cloudId);
                        cat.setSyncState(SyncState.SYNCED.getValue());
                        categoryDao.update(cat);
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                }
            });
        }
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void batchUploadSubCategoriesToCloud(List<SubCategory> subCategories, String parentCloudId) {
        final CountDownLatch latch = new CountDownLatch(subCategories.size());
        for (SubCategory sub : subCategories) {
            bmobApi.uploadSubCategory(sub, new SaveListener<String>() {
                @Override
                public void done(String cloudId, BmobException e) {
                    if (e == null) {
                        sub.setCloudId(cloudId);
                        sub.setSyncState(SyncState.SYNCED.getValue());
                        subCategoryDao.update(sub);
                    }
                    latch.countDown();
                }
            });
        }
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void loadCategories() {
        if (Boolean.TRUE.equals(_categoryLoading.getValue())) return;
        _categoryLoading.setValue(true);
        if (_allLoadedCategories.getValue() == null) _allLoadedCategories.setValue(new ArrayList<>());
        // 每个数据源都会回调两次（一次列表数据、一次补缩略图），
        // 因此不能按回调次数计数，改为按「源」计数，避免 loading 提前结束
        final boolean[] done = new boolean[3];
        final AtomicInteger remaining = new AtomicInteger(3);
        final Runnable onAllDone = () ->
                AppExecutors.get().mainThread().execute(() -> _categoryLoading.setValue(false));
        repository.getCategoryPage(0, new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) { mergeAndNotify(data); markSourceDone(done, remaining, 0, onAllDone); }
            @Override public void onError(String message) { markSourceDone(done, remaining, 0, onAllDone); }
        });
        repository.getAssetCategoryPage(getApplication().getAssets(), "freeicon_line.json", 0, new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) { mergeAndNotify(data); markSourceDone(done, remaining, 1, onAllDone); }
            @Override public void onError(String message) { markSourceDone(done, remaining, 1, onAllDone); }
        });
        repository.getAssetCategoryPage(getApplication().getAssets(), "线性色.json", 0, new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) { mergeAndNotify(data); markSourceDone(done, remaining, 2, onAllDone); }
            @Override public void onError(String message) { markSourceDone(done, remaining, 2, onAllDone); }
        });
    }

    private synchronized void mergeAndNotify(List<IconCategory> newData) {
        if (newData == null || newData.isEmpty()) return;
        AppExecutors.get().mainThread().execute(() -> {
            List<IconCategory> all = _allLoadedCategories.getValue();
            if (all == null) all = new ArrayList<>();
            List<IconCategory> updated = new ArrayList<>(all);
            for (IconCategory cat : newData) {
                boolean found = false;
                for (int i = 0; i < updated.size(); i++) {
                    if (cat.getFile().equals(updated.get(i).getFile())) {
                        updated.set(i, cat.copy());
                        found = true;
                        break;
                    }
                }
                if (!found) updated.add(cat.copy());
            }
            List<IconCategory> coloredCategories = new ArrayList<>();
            List<IconCategory> otherCategories = new ArrayList<>();
            for (IconCategory c : updated) {
                if ("lineal-color".equals(c.getStyle())) coloredCategories.add(c);
                else otherCategories.add(c);
            }
            updated.clear();
            updated.addAll(coloredCategories);
            updated.addAll(otherCategories);
            _allLoadedCategories.setValue(updated);
            _statistics.setValue(new Statistics(repository.getTotalIcons(), repository.getTotalPacks(), 38));
            applyFilters();
        });
    }

    public void loadMoreCategories() {
        if (isCategoryLoadingMore) return;
        if (!Boolean.TRUE.equals(_categoryHasMore.getValue())) return;
        IconStyle style = _currentStyle.getValue();
        final IconStyle currentStyle = (style != null) ? style : IconStyle.ALL;
        isCategoryLoadingMore = true;
        _categoryLoadingMore.setValue(true);
        final int currentPage = categoryPage + 1;
        AppExecutors.get().networkIO().execute(() -> {
            // 各风格翻页对应自己的数据源；ALL 需同时翻 3 个源
            final int sourceCount = (currentStyle == IconStyle.ALL) ? 3 : 1;
            final boolean[] done = new boolean[sourceCount];
            final AtomicInteger remaining = new AtomicInteger(sourceCount);
            final Runnable onAllDone = () -> AppExecutors.get().mainThread().execute(() -> {
                isCategoryLoadingMore = false;
                _categoryLoadingMore.setValue(false);
                categoryPage = currentPage;
                // 注意：由于 IconRepository 分批回调，缩略图会在回调后通过 mergeAndNotify 异步更新
            });
            switch (currentStyle) {
                case DEFAULT:
                    repository.getCategoryPage(currentPage, newLoadMoreCategoryCallback(done, remaining, 0, onAllDone));
                    break;
                case LINEAR:
                    repository.getAssetCategoryPage(getApplication().getAssets(), "freeicon_line.json", currentPage, newLoadMoreCategoryCallback(done, remaining, 0, onAllDone));
                    break;
                case COLORED:
                    repository.getAssetCategoryPage(getApplication().getAssets(), "线性色.json", currentPage, newLoadMoreCategoryCallback(done, remaining, 0, onAllDone));
                    break;
                case ALL:
                default:
                    repository.getCategoryPage(currentPage, newLoadMoreCategoryCallback(done, remaining, 0, onAllDone));
                    repository.getAssetCategoryPage(getApplication().getAssets(), "freeicon_line.json", currentPage, newLoadMoreCategoryCallback(done, remaining, 1, onAllDone));
                    repository.getAssetCategoryPage(getApplication().getAssets(), "线性色.json", currentPage, newLoadMoreCategoryCallback(done, remaining, 2, onAllDone));
                    break;
            }
        });
    }

    // 每个数据源的回调（列表数据 + 缩略图）可能触发多次，但只应计数一次；
    // 这里用 done[index] 保证「每个源完成一次即计数」，避免 loading 提前结束。
    private static void markSourceDone(boolean[] done, AtomicInteger remaining, int index, Runnable onAllDone) {
        boolean first;
        synchronized (done) {
            first = !done[index];
            if (first) done[index] = true;
        }
        if (first && remaining.decrementAndGet() == 0) {
            onAllDone.run();
        }
    }

    private IconRepository.Callback<List<IconCategory>> newLoadMoreCategoryCallback(
            boolean[] done, AtomicInteger remaining, int index, Runnable onAllDone) {
        return new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) {
                mergeAndNotify(data);
                markSourceDone(done, remaining, index, onAllDone);
            }
            @Override public void onError(String message) {
                markSourceDone(done, remaining, index, onAllDone);
            }
        };
    }

    public void search(String keyword) {
        if (keyword == null) keyword = "";
        String trimmed = keyword.trim().toLowerCase();
        Integer scope = _searchScope.getValue();
        if (scope == null) scope = 0;
        lastKeyword = trimmed;
        _currentKeyword.setValue(trimmed);
        if (trimmed.isEmpty()) {
            _searchResults.setValue(new ArrayList<>());
            applyFilters();
            return;
        }
        if (scope == 0) {
            searchIcons(trimmed);
            applyFilters(); 
        } else {
            _searchResults.setValue(new ArrayList<>());
            applyFilters(); 
        }
    }

    private void searchIcons(String trimmed) {
        _searchLoading.setValue(true);
        AppExecutors.get().networkIO().execute(() -> {
            repository.search(trimmed, 0, new IconRepository.Callback<List<IconItem>>() {
                @Override public void onSuccess(List<IconItem> data) {
                    _searchLoading.postValue(false);
                    _searchResults.postValue(data);
                }
                @Override public void onError(String message) {
                    _searchLoading.postValue(false);
                    _searchError.postValue(message);
                }
            });
        });
    }

    public void setSearchScope(int scope) {
        _searchScope.setValue(scope);
        String keyword = _currentKeyword.getValue();
        if (keyword != null && !keyword.isEmpty()) search(keyword);
        else applyFilters();
    }

    public void loadMoreSearchResults() {
        if (isSearchLoadingMore) return;
        if (!Boolean.TRUE.equals(_searchHasMore.getValue())) return;
        String keyword = _currentKeyword.getValue();
        if (keyword == null || keyword.isEmpty()) return;
        isSearchLoadingMore = true;
        _searchLoadingMore.setValue(true);
        final int nextPage = searchPage + 1;
        AppExecutors.get().networkIO().execute(() -> {
            repository.search(keyword, nextPage, new IconRepository.Callback<List<IconItem>>() {
                @Override public void onSuccess(List<IconItem> data) {
                    isSearchLoadingMore = false;
                    _searchLoadingMore.postValue(false);
                    if (data == null || data.isEmpty()) { _searchHasMore.postValue(false); return; }
                    List<IconItem> current = _searchResults.getValue();
                    List<IconItem> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
                    updated.addAll(data);
                    _searchResults.postValue(updated);
                    searchPage = nextPage;
                }
                @Override public void onError(String message) {
                    isSearchLoadingMore = false;
                    _searchLoadingMore.postValue(false);
                    _searchError.postValue(message);
                }
            });
        });
    }

    public void clearDetailError() { _detailError.setValue(null); }
    private void notifyPageStatesChanged() { _detailPageStates.setValue(pageStates); }
    private void refreshGlobalLoadingState() { _detailLoading.setValue(!inflightPages.isEmpty()); }
    public void clearCategoryError() { _categoryError.setValue(null); }
    public void clearSearchError() { _searchError.setValue(null); }
    public void clearSaveResult() { _saveResult.setValue(null); }
}
