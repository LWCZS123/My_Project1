package com.example.my_project1.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.CategoryRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.annotations.NonNull;

/**
 * CategoryViewModel
 * ---------------------------
 * 负责桥接 UI 和 Repository
 * 提供对分类数据的 LiveData 监听
 */
public class CategoryViewModel extends AndroidViewModel {

    private final CategoryRepository repository;
    private LiveData<List<CategoryWithSubCategories>> incomeCategories;
    private LiveData<List<CategoryWithSubCategories>> expenseCategories;

    private final MutableLiveData<ApiResponse<String>> _operationState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> operationState = _operationState;

    public CategoryViewModel(@NonNull Application application) {
        super(application);
        repository = new CategoryRepository(application);
    }

    public void syncFromCloud() {
        repository.syncCategoriesFromCloud(success -> {
            if (success) {
                Log.d("CategoryVM", "✅ 云端同步成功");
            } else {
                Log.e("CategoryVM", "❌ 云端同步失败");
            }
        });
    }

    /** 获取支出分类 */
    public LiveData<List<CategoryWithSubCategories>> getExpenseCategories(String userId) {
        // 允许传入不同的 userId 刷新 LiveData
        expenseCategories = repository.getCategoriesWithSubs(userId, "expense");
        return expenseCategories;
    }

    public List<CategoryWithSubCategories> getExpenseSnapshot() {
        return repository.getCategoriesSnapshot("expense");
    }

    /** 获取收入分类 */
    public LiveData<List<CategoryWithSubCategories>> getIncomeCategories(String userId) {
        incomeCategories = repository.getCategoriesWithSubs(userId, "income");
        return incomeCategories;
    }

    public List<CategoryWithSubCategories> getIncomeSnapshot() {
        return repository.getCategoriesSnapshot("income");
    }

    public LiveData<List<CategoryWithSubCategories>> getTransferCategories(String userId) {
        androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> result = new androidx.lifecycle.MediatorLiveData<>();
        result.addSource(repository.getCategoriesWithSubs(userId, "transfer"), categories -> {
            if (categories == null || categories.isEmpty()) {
                // 🔑 兜底逻辑：如果数据库没数据，返回预设的转账分类
                List<CategoryWithSubCategories> presets = new java.util.ArrayList<>();
                
                Category c1 = new Category();
                c1.setName("转账");
                c1.setIconUri("ic_qiehuan");
                c1.setType("transfer");
                c1.setCloudId("system_transfer_1");
                
                CategoryWithSubCategories item = new CategoryWithSubCategories();
                item.category = c1;
                presets.add(item);
                
                Category c2 = new Category();
                c2.setName("还款");
                c2.setIconUri("ic_card");
                c2.setType("transfer");
                c2.setCloudId("system_transfer_2");
                
                CategoryWithSubCategories item2 = new CategoryWithSubCategories();
                item2.category = c2;
                presets.add(item2);

                result.setValue(presets);
            } else {
                result.setValue(categories);
            }
        });
        return result;
    }

    /** 操作方法 */
    public void insert(Category category) { repository.insert(category); }

    public void update(Category category) { repository.update(category); }

    public void deleteCategoryById(long categoryId) { repository.deleteCategoryById(categoryId); }

    public void checkCategoryBills(String userId, String cloudId, java.util.function.Consumer<Integer> callback) {
        repository.checkBillsCount(userId, cloudId, callback);
    }

    public void updateSortIndex(long id, int index) {
        AppExecutors.get().diskIO().execute(() -> {
            Category cat = repository.getCategoryById(id);
            if (cat != null && cat.getSortIndex() != index) {
                cat.setSortIndex(index);
                cat.markUpdatedForSync();
                repository.update(cat);
            }
        });
    }

    public void updateCategoryOrder(List<Category> categories) {
        AppExecutors.get().diskIO().execute(() -> {
            for (int i = 0; i < categories.size(); i++) {
                Category cat = categories.get(i);
                cat.setSortIndex(i);
                cat.markUpdatedForSync();
            }
            repository.updateAll(categories);
        });
    }

    public void updateCategorySafe(long id, String newName, String newIconUri, String newIconBgColor, boolean excludeBudget) {
        AppExecutors.get().diskIO().execute(() -> {
            Category existing = repository.getCategoryById(id); // 或直接调用 DAO
            if (existing == null) {
                Log.w("CategoryVM", "找不到分类 id=" + id);
                return;
            }

            // 只修改需要改动的字段（保留 cloudId / ownerId / 其它字段）
            existing.setName(newName);
            existing.setIconUri(newIconUri);
            existing.setIconBackgroundColor(newIconBgColor);
            existing.setExcludeBudget(excludeBudget);
            existing.markUpdatedForSync(); // 更新 updatedAt 并 set syncState = TO_UPDATE

            // 保存到本地（会触发 LiveData -> UI 更新）
            repository.update(existing);
            // repository.update() 应该会执行 diskIO().execute(() -> dao.update(existing));
        });
    }

    public void archiveCategory(long id, boolean archiveChildren) {
        _operationState.setValue(ApiResponse.loading("正在归档..."));
        repository.archiveCategory(id, archiveChildren);
        _operationState.postValue(ApiResponse.success("已归档"));
    }

    public void archiveSubCategory(long id) {
        _operationState.setValue(ApiResponse.loading("正在归档..."));
        repository.archiveSubCategory(id);
        _operationState.postValue(ApiResponse.success("已归档"));
    }

    public void restoreCategory(long id, boolean isSub) {
        _operationState.setValue(ApiResponse.loading("正在恢复..."));
        repository.restoreCategory(id, isSub);
        _operationState.postValue(ApiResponse.success("已恢复"));
    }

    public void migrateCategoryData(String userId, String sourceId, Category target) {
        _operationState.setValue(ApiResponse.loading("正在迁移账单..."));
        repository.migrateBills(userId, sourceId, target, count -> {
            if (count > 0) {
                _operationState.postValue(ApiResponse.success("成功迁移 " + count + " 条账单"));
            } else {
                _operationState.postValue(ApiResponse.error("该分类下暂无账单，无需迁移"));
            }
        });
    }

    public void changeParentCategory(long subId, Category newParent) {
        _operationState.setValue(ApiResponse.loading("正在调整归属..."));
        repository.changeSubCategoryParent(subId, newParent);
        _operationState.postValue(ApiResponse.success("已调整"));
    }

    public void promoteToMainCategory(long subId, String categoryType) {
        _operationState.setValue(ApiResponse.loading("正在晋升为一级分类..."));
        repository.promoteSubCategory(subId, categoryType, success -> {
            if (success) {
                _operationState.postValue(ApiResponse.success("已晋升为一级分类"));
            } else {
                _operationState.postValue(ApiResponse.error("晋升失败，请检查网络"));
            }
        });
    }

    public void resetOperationState() {
        _operationState.setValue(ApiResponse.idle());
    }

    /** 获取归档分类 */
    public LiveData<List<CategoryWithSubCategories>> getArchivedCategories(String userId) {
        if (userId == null) return new MutableLiveData<>(new ArrayList<>());

        androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> result = new androidx.lifecycle.MediatorLiveData<>();
        LiveData<List<CategoryWithSubCategories>> expenseSource = repository.getCategoriesWithSubs(userId, "expense");
        LiveData<List<CategoryWithSubCategories>> incomeSource = repository.getCategoriesWithSubs(userId, "income");

        result.addSource(expenseSource, categories -> {
            combineAndFilterArchived(result, categories, incomeSource.getValue());
        });
        result.addSource(incomeSource, categories -> {
            combineAndFilterArchived(result, expenseSource.getValue(), categories);
        });
        return result;
    }

    private void combineAndFilterArchived(androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> result,
                                         List<CategoryWithSubCategories> exp, List<CategoryWithSubCategories> inc) {
        List<CategoryWithSubCategories> archived = new ArrayList<>();

        processArchivedList(exp, archived);
        processArchivedList(inc, archived);

        result.setValue(archived);
    }

    private void processArchivedList(List<CategoryWithSubCategories> list, List<CategoryWithSubCategories> archived) {
        if (list == null) return;
        for (CategoryWithSubCategories cw : list) {
            if (cw == null || cw.category == null) continue;

            // 1. 如果一级分类已归档
            if (cw.category.getArchiveStatus() != null && cw.category.getArchiveStatus() == 1) {
                archived.add(cw);
            } else {
                // 2. 如果一级分类未归档，但包含已归档的二级分类
                if (cw.subCategories != null) {
                    List<SubCategory> archivedSubs = new ArrayList<>();
                    for (SubCategory sub : cw.subCategories) {
                        if (sub.getArchiveStatus() != null && sub.getArchiveStatus() == 1) {
                            archivedSubs.add(sub);
                        }
                    }
                    if (!archivedSubs.isEmpty()) {
                        CategoryWithSubCategories partial = new CategoryWithSubCategories();
                        partial.category = cw.category;
                        partial.subCategories = archivedSubs;
                        archived.add(partial);
                    }
                }
            }
        }
    }
}
