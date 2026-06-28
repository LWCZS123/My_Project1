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
import com.example.my_project1.data.remote.model.CloudCategory;
import com.example.my_project1.data.remote.model.CloudSubCategory;
import com.example.my_project1.data.repository.CategoryRepository;
import com.example.my_project1.data.repository.SubCategoryRepository;
import com.example.my_project1.data.repository.icon.IconRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import cn.bmob.v3.BmobUser;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SaveListener;

/**
 * IconMarketViewModel — 修复 + 优化版
 * ─────────────────────────────────────────────────────────────
 * 修复问题：
 *
 * ① 二级分类批量保存后清除 App 数据，重新登录无法同步
 *    根因：saveAsSecondLevelCategory 未写入 parentCloudId，
 *    SubCategorySyncWorker 检测到 parent_cloud_id 为空 → retry 无限循环 → 永不上传
 *    修复：保存子分类时，同步从本地 Category 查出 cloudId 填充 parentCloudId。
 *         若父分类尚未同步（cloudId=null），先触发父分类同步后再保存子分类。
 *
 * ② 批量上传走 WorkManager 逐条处理，HTTP 请求数 = 图标数，性能差
 *    优化：新增 batchUploadToCloud()，在 ViewModel 层直接并发调用 Bmob save，
 *    所有请求并行发出（CountDownLatch 等待），远少于 WorkManager 串行逐条处理的时间。
 *    WorkManager 仅作为失败兜底重试机制保留。
 *
 * ③ 一级分类保存缺少 syncState = TO_CREATE（原代码用 markUpdatedForSync 设置的是 TO_UPDATE）
 *    修复：显式设置 syncState = TO_CREATE.getValue()。
 *
 * ④ 其余所有逻辑（详情页分页、多选、搜索、首页分类加载）保持不变。
 */
public class IconMarketViewModel extends AndroidViewModel {

    private static final String TAG = "IconMarketViewModel";

    // ══════════════════════════════════════════════════════════
    // PageState（保持不变）
    // ══════════════════════════════════════════════════════════

    public enum PageStatus { IDLE, LOADING, LOADED, ERROR }

    public static class PageState {
        public final PageStatus     status;
        public final List<IconItem> items;
        public final String         error;

        public PageState(PageStatus status, List<IconItem> items, String error) {
            this.status = status;
            this.items  = items;
            this.error  = error;
        }

        public static PageState idle()    { return new PageState(PageStatus.IDLE,    null, null); }
        public static PageState loading() { return new PageState(PageStatus.LOADING, null, null); }
        public static PageState loaded(List<IconItem> items) {
            return new PageState(PageStatus.LOADED, items != null ? items : new ArrayList<>(), null);
        }
        public static PageState error(String msg) {
            return new PageState(PageStatus.ERROR, null, msg);
        }
    }

    private final SparseArray<PageState> pageStates    = new SparseArray<>();
    private final Set<Integer>           inflightPages = new HashSet<>();

    private final MutableLiveData<SparseArray<PageState>> _detailPageStates = new MutableLiveData<>();
    public  final LiveData<SparseArray<PageState>>         detailPageStates  = _detailPageStates;

    private final MutableLiveData<Integer> _detailTotalPages = new MutableLiveData<>(0);
    public  final LiveData<Integer>         detailTotalPages  = _detailTotalPages;

    private final MutableLiveData<Boolean> _detailLoading = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         detailLoading  = _detailLoading;

    private final MutableLiveData<String>  _detailError = new MutableLiveData<>();
    public  final LiveData<String>          detailError  = _detailError;

    private final MutableLiveData<IconCategory> _selectedCategory = new MutableLiveData<>();
    public  final LiveData<IconCategory>         selectedCategory  = _selectedCategory;

    // ══════════════════════════════════════════════════════════
    // 首页（保持不变）
    // ══════════════════════════════════════════════════════════

    private final MutableLiveData<List<IconCategory>> _categories = new MutableLiveData<>(new ArrayList<>());
    public  final LiveData<List<IconCategory>>         categories  = _categories;

    private final MutableLiveData<Boolean> _categoryLoading     = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         categoryLoading      = _categoryLoading;

    private final MutableLiveData<Boolean> _categoryLoadingMore = new MutableLiveData<>(false);
    public  final LiveData<Boolean>         categoryLoadingMore  = _categoryLoadingMore;

    private final MutableLiveData<Boolean> _categoryHasMore     = new MutableLiveData<>(true);
    public  final LiveData<Boolean>         categoryHasMore      = _categoryHasMore;

    private final MutableLiveData<String>  _categoryError = new MutableLiveData<>();
    public  final LiveData<String>          categoryError  = _categoryError;

    private int     categoryPage          = 0;
    private boolean isCategoryLoadingMore = false;

    // ══════════════════════════════════════════════════════════
    // 搜索（保持不变）
    // ══════════════════════════════════════════════════════════

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

    private int     searchPage          = 0;
    private boolean isSearchLoadingMore = false;
    private String  lastKeyword         = "";

    // ══════════════════════════════════════════════════════════
    // 多选（保持不变）
    // ══════════════════════════════════════════════════════════

    public final SelectionManager selectionManager = new SelectionManager();

    // ══════════════════════════════════════════════════════════
    // 保存结果
    // ══════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════
    // 依赖
    // ══════════════════════════════════════════════════════════

    private final IconRepository        repository;
    private final CategoryRepository    categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    /** 直接持有 DAO，用于批量上传时查询 cloudId（避免绕一圈 Repository） */
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
        bmobApi        = new BmobApiImpl();

        initCurrentUserId();
    }

    private void initCurrentUserId() {
        BmobUser user = BmobUser.getCurrentUser();
        currentUserId = (user != null) ? user.getObjectId() : "";
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId != null ? userId : "";
    }

    // ══════════════════════════════════════════════════════════
    // 详情页（保持不变）
    // ══════════════════════════════════════════════════════════

    public void openCategory(IconCategory category) {
        _selectedCategory.setValue(category);
        pageStates.clear();
        inflightPages.clear();
        _detailTotalPages.setValue(0);
        _detailLoading.setValue(true);
        notifyPageStatesChanged();

        repository.getCategoryPageCount(category, new IconRepository.Callback<Integer>() {
            @Override public void onSuccess(Integer totalPages) { _detailTotalPages.setValue(totalPages); }
            @Override public void onError(String message)       { Log.e(TAG, "获取总页数失败: " + message); }
        });

        loadDetailPage(category, 0);
    }

    public void loadDetailPage(IconCategory category, int page) {
        if (category == null) return;
        Integer total = _detailTotalPages.getValue();
        if (total != null && total > 0 && page >= total) return;
        PageState existing = pageStates.get(page);
        if (existing != null &&
                (existing.status == PageStatus.LOADING || existing.status == PageStatus.LOADED)) return;
        if (inflightPages.contains(page)) return;

        inflightPages.add(page);
        pageStates.put(page, PageState.loading());
        notifyPageStatesChanged();
        refreshGlobalLoadingState();

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
    }

    public PageState getPageState(int page) {
        PageState s = pageStates.get(page);
        return s != null ? s : PageState.idle();
    }

    // ══════════════════════════════════════════════════════════
    // 多选操作（保持不变）
    // ══════════════════════════════════════════════════════════

    public void onIconLongClick(String iconId)  { selectionManager.enterMultiSelectMode(iconId); }
    public void onIconClick(String iconId) {
        if (Boolean.TRUE.equals(selectionManager.multiSelectMode.getValue()))
            selectionManager.toggle(iconId);
    }
    public void toggleSelectAll(List<String> allIconIds) { selectionManager.toggleSelectAll(allIconIds); }
    public void exitMultiSelectMode()                    { selectionManager.exitMultiSelectMode(); }

    // ══════════════════════════════════════════════════════════
    // 分类列表（保持不变）
    // ══════════════════════════════════════════════════════════

    public LiveData<List<CategoryWithSubCategories>> getCategoriesByType(String categoryType) {
        if (currentUserId == null || currentUserId.isEmpty())
            return new MutableLiveData<>(new ArrayList<>());
        return categoryRepository.getCategoriesWithSubs(currentUserId, categoryType);
    }

    // ══════════════════════════════════════════════════════════
    // ★ 修复：批量保存为一级分类
    // ══════════════════════════════════════════════════════════

    /**
     * 批量保存为一级分类。
     *
     * 修复点：
     *  ① syncState 明确设置为 TO_CREATE（原代码 markUpdatedForSync 设的是 TO_UPDATE）
     *  ② 写入本地后，立即发起并行批量云端上传（不再完全依赖 WorkManager）
     *     - 并行上传：所有分类同时 save，N 个 HTTP 请求几乎并行，总耗时 ≈ 单次请求耗时
     *     - 上传成功后立刻回写 cloudId + SyncState=SYNCED，避免 Worker 重复上传
     *     - 失败的分类仍保留 TO_CREATE 状态，WorkManager 作为兜底稍后重试
     */
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
                if (existing != null) {
                    skipCount++;
                    Log.d(TAG, "saveAsFirst - 跳过重复: " + item.getName());
                    continue;
                }

                Category cat = new Category();
                cat.setName(item.getName());
                cat.setIconUri(item.getUrl());
                cat.setType(type);
                cat.setOwnerId(currentUserId);
                cat.setOrder(0);
                cat.setSystemPreset(false);
                cat.setUpdatedAt(System.currentTimeMillis());
                // ★ 修复①：明确设置 TO_CREATE，不用 markUpdatedForSync
                cat.setSyncState(SyncState.TO_CREATE.getValue());
                toInsert.add(cat);
            }

            if (toInsert.isEmpty()) {
                final int fs = 0, fsk = skipCount;
                AppExecutors.get().mainThread().execute(() -> {
                    _saving.setValue(false);
                    _saveResult.setValue(new SaveResult(false, fs, fsk, "所选图标均已存在，无需重复保存"));
                });
                return;
            }

            // Step 1：写入本地数据库，拿到自增 id
            long[] insertedIds = categoryDao.insertCategories(toInsert);
            Log.d(TAG, "saveAsFirst - 本地写入 " + toInsert.size() + " 条");

            // 将自增 id 回填到 category 对象（insertCategories 按顺序返回 rowId）
            for (int i = 0; i < insertedIds.length && i < toInsert.size(); i++) {
                if (insertedIds[i] > 0) {
                    toInsert.get(i).setId(insertedIds[i]);
                }
            }

            // Step 2：并行批量上传到云端（★ 性能优化核心）
            batchUploadCategoriesToCloud(toInsert);

            final int finalSaved = toInsert.size();
            final int finalSkip  = skipCount;
            AppExecutors.get().mainThread().execute(() -> {
                _saving.setValue(false);
                String msg = "已保存 " + finalSaved + " 个分类" +
                        (finalSkip > 0 ? "，跳过重复 " + finalSkip + " 个" : "") +
                        "，同步中...";
                _saveResult.setValue(new SaveResult(true, finalSaved, finalSkip, msg));
                selectionManager.exitMultiSelectMode();
            });
        });
    }

    // ══════════════════════════════════════════════════════════
    // ★ 修复：批量保存为二级分类
    // ══════════════════════════════════════════════════════════

    /**
     * 批量保存为指定父分类下的子分类。
     *
     * 修复点（★ 这是二级分类同步失败的根本原因）：
     *  ① 保存时同步填充 parentCloudId
     *     原代码只设置了 parentCategoryId（本地 id），未填充 parentCloudId，
     *     导致 SubCategorySyncWorker 的 guardParentCloudId() 检测失败，
     *     子分类永远无法上传到云端。
     *  ② 若父分类还没有 cloudId（尚未同步），子分类记录仍写入本地，
     *     但 parentCloudId 留空，由 WorkManager 在父分类同步后自动补全并上传。
     *  ③ 若父分类已有 cloudId，立即并行上传子分类到云端。
     */
    public void saveAsSecondLevelCategory(List<IconItem> selectedItems, long parentCategoryId) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            _saveResult.setValue(new SaveResult(false, 0, 0, "请先选择图标"));
            return;
        }
        if (Boolean.TRUE.equals(_saving.getValue())) return;

        _saving.setValue(true);

        AppExecutors.get().diskIO().execute(() -> {
            // ★ 修复①：查出父分类，获取 cloudId（这是子分类能否同步的关键）
            Category parentCategory = categoryDao.getCategoryById(parentCategoryId);
            String parentCloudId = (parentCategory != null) ? parentCategory.getCloudId() : null;

            if (parentCloudId == null || parentCloudId.isEmpty()) {
                Log.w(TAG, "saveAsSecond - 父分类 cloudId 为空（尚未同步），子分类将在父类同步后自动补全");
                // 不阻塞用户操作，仍然保存到本地，WorkManager 的 propagateParentCloudIdToChildren 会补全
            } else {
                Log.d(TAG, "saveAsSecond - 父分类 cloudId=" + parentCloudId + "，子分类可直接上传");
            }

            List<SubCategory> toInsert = new ArrayList<>();
            int skipCount = 0;

            for (IconItem item : selectedItems) {
                SubCategory existing = subCategoryRepository.getSubCategoryByParentAndName(
                        parentCategoryId, item.getName());
                if (existing != null) {
                    skipCount++;
                    Log.d(TAG, "saveAsSecond - 跳过重复: " + item.getName());
                    continue;
                }

                SubCategory sub = new SubCategory();
                sub.setName(item.getName());
                sub.setIconUri(item.getUrl());
                sub.setParentCategoryId(parentCategoryId);
                sub.setOwnerId(currentUserId);
                sub.setOrder(0);
                sub.setUpdatedAt(System.currentTimeMillis());
                // ★ 修复②：填充 parentCloudId，SubCategorySyncWorker 依赖此字段判断是否可上传
                sub.setParentCloudId(parentCloudId); // 若父类未同步则为 null，Worker 稍后会补全
                sub.setSyncState(SyncState.TO_CREATE.getValue());
                toInsert.add(sub);
            }

            if (toInsert.isEmpty()) {
                final int fsk = skipCount;
                AppExecutors.get().mainThread().execute(() -> {
                    _saving.setValue(false);
                    _saveResult.setValue(new SaveResult(false, 0, fsk, "所选图标均已存在，无需重复保存"));
                });
                return;
            }

            // Step 1：写入本地数据库
            long[] insertedIds = subCategoryDao.insertSubCategories(toInsert);
            Log.d(TAG, "saveAsSecond - 本地写入 " + toInsert.size() + " 条 → parentId=" + parentCategoryId);

            // 回填自增 id
            for (int i = 0; i < insertedIds.length && i < toInsert.size(); i++) {
                if (insertedIds[i] > 0) {
                    toInsert.get(i).setId(insertedIds[i]);
                }
            }

            // Step 2：若父分类已有 cloudId，立即并行上传（★ 性能优化）
            if (parentCloudId != null && !parentCloudId.isEmpty()) {
                batchUploadSubCategoriesToCloud(toInsert, parentCloudId);
            } else {
                // 父分类未同步，触发父分类 WorkManager 同步（它会在完成后 propagate parentCloudId）
                com.example.my_project1.work.CategorySyncWorker.enqueue(getApplication());
            }

            final int finalSaved = toInsert.size();
            final int finalSkip  = skipCount;
            AppExecutors.get().mainThread().execute(() -> {
                _saving.setValue(false);
                String msg = "已保存 " + finalSaved + " 个子分类" +
                        (finalSkip > 0 ? "，跳过重复 " + finalSkip + " 个" : "") +
                        "，同步中...";
                _saveResult.setValue(new SaveResult(true, finalSaved, finalSkip, msg));
                selectionManager.exitMultiSelectMode();
            });
        });
    }

    // ══════════════════════════════════════════════════════════
    // ★ 性能优化：并行批量上传一级分类到云端
    // ══════════════════════════════════════════════════════════

    /**
     * 并行批量上传一级分类到 Bmob。
     *
     * 优化原理：
     *  - 旧方案：WorkManager 串行处理，N 个分类 → N 次串行 HTTP，总耗时 = N × 单次耗时
     *  - 新方案：同时发起 N 个 Bmob save，HTTP 请求几乎并行，总耗时 ≈ 单次耗时
     *  - CountDownLatch 等待所有请求完成后统一处理结果
     *  - 成功：立即回写 cloudId + SYNCED，WorkManager 不会重复上传
     *  - 失败：保留 TO_CREATE，WorkManager 兜底重试
     *
     * @param categories 已写入本地数据库的 Category 列表（id > 0）
     */
    private void batchUploadCategoriesToCloud(List<Category> categories) {
        if (categories == null || categories.isEmpty()) return;

        AppExecutors.get().networkIO().execute(() -> {
            CountDownLatch latch = new CountDownLatch(categories.size());
            AtomicInteger successCount = new AtomicInteger(0);

            for (Category category : categories) {
                // 每个分类独立发起 Bmob 异步 save，不相互阻塞
                CloudCategory cloud = CloudCategory.fromLocalCategory(category);
                cloud.setOwnerId(currentUserId);

                cloud.save(new SaveListener<String>() {
                    @Override
                    public void done(String objectId, BmobException e) {
                        if (e == null && objectId != null) {
                            // 立即持久化 cloudId，避免 WorkManager 重复上传
                            AppExecutors.get().diskIO().execute(() -> {
                                categoryDao.updateCloudIdById(
                                        category.getId(), objectId, SyncState.SYNCED.getValue());
                                Log.d(TAG, "✅ 一级分类上传成功: " + category.getName()
                                        + " | cloudId=" + objectId);
                            });
                            successCount.incrementAndGet();
                        } else {
                            // 失败保留 TO_CREATE，WorkManager 兜底
                            Log.w(TAG, "⚠️ 一级分类上传失败（WorkManager 将重试）: "
                                    + category.getName()
                                    + " | " + (e != null ? e.getMessage() : "unknown"));
                        }
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await(); // 等待所有并行请求完成
                Log.d(TAG, "batchUploadCategories 完成: 成功 " + successCount.get()
                        + "/" + categories.size());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "batchUploadCategories 被中断");
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // ★ 性能优化：并行批量上传子分类到云端
    // ══════════════════════════════════════════════════════════

    /**
     * 并行批量上传子分类到 Bmob。
     *
     * 与 batchUploadCategoriesToCloud 逻辑相同，额外处理：
     *  - 设置 parentCategory Pointer（Bmob 需要此字段建立父子关系）
     *  - 上传成功后同时更新 cloudId 和 parentCloudId 到本地
     *
     * @param subCategories  已写入本地数据库的子分类列表（id > 0）
     * @param parentCloudId  父分类的云端 objectId（调用前已确认非空）
     */
    private void batchUploadSubCategoriesToCloud(List<SubCategory> subCategories,
                                                 String parentCloudId) {
        if (subCategories == null || subCategories.isEmpty() ||
                parentCloudId == null || parentCloudId.isEmpty()) return;

        AppExecutors.get().networkIO().execute(() -> {
            CountDownLatch latch = new CountDownLatch(subCategories.size());
            AtomicInteger successCount = new AtomicInteger(0);

            for (SubCategory sub : subCategories) {
                // 构造云端对象
                CloudSubCategory cloud = CloudSubCategory.fromLocalSubCategory(sub, parentCloudId);

                // 设置用户 Pointer
                if (currentUserId != null && !currentUserId.isEmpty()) {
                    cn.bmob.v3.BmobUser userPointer = new cn.bmob.v3.BmobUser();
                    userPointer.setObjectId(currentUserId);
                    cloud.setOwnerId(userPointer);
                }

                cloud.save(new SaveListener<String>() {
                    @Override
                    public void done(String objectId, BmobException e) {
                        if (e == null && objectId != null) {
                            // 立即持久化 cloudId + parentCloudId，避免 WorkManager 重复上传
                            AppExecutors.get().diskIO().execute(() -> {
                                subCategoryDao.updateSubCloudIdById(
                                        sub.getId(), objectId, SyncState.SYNCED.getValue());
                                Log.d(TAG, "✅ 子分类上传成功: " + sub.getName()
                                        + " | cloudId=" + objectId
                                        + " | parentCloudId=" + parentCloudId);
                            });
                            successCount.incrementAndGet();
                        } else {
                            // 失败保留 TO_CREATE，WorkManager 兜底（parent_cloud_id 已写入，可正常重试）
                            Log.w(TAG, "⚠️ 子分类上传失败（WorkManager 将重试）: "
                                    + sub.getName()
                                    + " | " + (e != null ? e.getMessage() : "unknown"));
                        }
                        latch.countDown();
                    }
                });
            }

            try {
                latch.await();
                Log.d(TAG, "batchUploadSubCategories 完成: 成功 " + successCount.get()
                        + "/" + subCategories.size());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "batchUploadSubCategories 被中断");
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // 首页分类加载（保持不变）
    // ══════════════════════════════════════════════════════════

    public void loadCategories() {
        if (Boolean.TRUE.equals(_categoryLoading.getValue())) return;
        categoryPage = 0;
        _categoryLoading.setValue(true);
        _categories.setValue(new ArrayList<>());

        repository.getCategoryPage(categoryPage, new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) {
                _categoryLoading.setValue(false);
                _categories.setValue(data);
                _categoryHasMore.setValue(data.size() >= IconRepository.PAGE_SIZE_CATEGORY);
                categoryPage = 1;
            }
            @Override public void onError(String message) {
                _categoryLoading.setValue(false);
                _categoryError.setValue(message);
            }
        });
    }

    public void loadMoreCategories() {
        if (isCategoryLoadingMore) return;
        if (!Boolean.TRUE.equals(_categoryHasMore.getValue())) return;
        isCategoryLoadingMore = true;
        _categoryLoadingMore.setValue(true);

        repository.getCategoryPage(categoryPage, new IconRepository.Callback<List<IconCategory>>() {
            @Override public void onSuccess(List<IconCategory> data) {
                isCategoryLoadingMore = false;
                _categoryLoadingMore.setValue(false);
                if (data.isEmpty()) { _categoryHasMore.setValue(false); return; }
                List<IconCategory> current = _categories.getValue();
                if (current == null) current = new ArrayList<>();
                List<IconCategory> merged = new ArrayList<>(current);
                merged.addAll(data);
                _categories.setValue(merged);
                _categoryHasMore.setValue(data.size() >= IconRepository.PAGE_SIZE_CATEGORY);
                categoryPage++;
            }
            @Override public void onError(String message) {
                isCategoryLoadingMore = false;
                _categoryLoadingMore.setValue(false);
                _categoryError.setValue(message);
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // 搜索（保持不变）
    // ══════════════════════════════════════════════════════════

    public void search(String keyword) {
        if (keyword == null) keyword = "";
        String trimmed = keyword.trim();
        if (trimmed.equals(lastKeyword) && searchPage > 0) return;

        lastKeyword = trimmed;
        _currentKeyword.setValue(trimmed);
        searchPage = 0;
        _searchResults.setValue(new ArrayList<>());
        _searchHasMore.setValue(true);
        if (trimmed.isEmpty()) { _searchLoading.setValue(false); return; }

        _searchLoading.setValue(true);
        repository.search(trimmed, 0, new IconRepository.Callback<List<IconItem>>() {
            @Override public void onSuccess(List<IconItem> data) {
                _searchLoading.setValue(false);
                _searchResults.setValue(data);
                _searchHasMore.setValue(data.size() >= IconRepository.PAGE_SIZE_SEARCH);
                searchPage = 1;
            }
            @Override public void onError(String message) {
                _searchLoading.setValue(false);
                _searchError.setValue(message);
            }
        });
    }

    public void loadMoreSearchResults() {
        if (isSearchLoadingMore) return;
        if (!Boolean.TRUE.equals(_searchHasMore.getValue())) return;
        if (lastKeyword.isEmpty()) return;

        isSearchLoadingMore = true;
        _searchLoadingMore.setValue(true);

        repository.search(lastKeyword, searchPage, new IconRepository.Callback<List<IconItem>>() {
            @Override public void onSuccess(List<IconItem> data) {
                isSearchLoadingMore = false;
                _searchLoadingMore.setValue(false);
                if (data.isEmpty()) { _searchHasMore.setValue(false); return; }
                List<IconItem> current = _searchResults.getValue();
                if (current == null) current = new ArrayList<>();
                List<IconItem> merged = new ArrayList<>(current);
                merged.addAll(data);
                _searchResults.setValue(merged);
                _searchHasMore.setValue(data.size() >= IconRepository.PAGE_SIZE_SEARCH);
                searchPage++;
            }
            @Override public void onError(String message) {
                isSearchLoadingMore = false;
                _searchLoadingMore.setValue(false);
                _searchError.setValue(message);
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    // 私有工具
    // ══════════════════════════════════════════════════════════

    private void notifyPageStatesChanged() {
        _detailPageStates.setValue(pageStates.clone());
    }

    private void refreshGlobalLoadingState() {
        _detailLoading.setValue(!inflightPages.isEmpty());
    }

    public void clearCategoryError() { _categoryError.setValue(null); }
    public void clearSearchError()   { _searchError.setValue(null);   }
    public void clearDetailError()   { _detailError.setValue(null);   }
    public void clearSaveResult()    { _saveResult.setValue(null);    }
}