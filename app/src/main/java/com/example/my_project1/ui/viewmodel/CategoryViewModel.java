package com.example.my_project1.ui.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.repository.CategoryRepository;
import com.example.my_project1.utils.AppExecutors;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * 分类管理 ViewModel
 * 负责桥接 UI 与 Repository，管理分类的增删改查及同步状态
 */
public class CategoryViewModel extends AndroidViewModel {

    private final CategoryRepository repository;
    private final MutableLiveData<ApiResponse<String>> _operationState = new MutableLiveData<>(ApiResponse.idle());
    public final LiveData<ApiResponse<String>> operationState = _operationState;

    public CategoryViewModel(@NonNull Application application) {
        super(application);
        repository = new CategoryRepository(application);
    }

    /**
     * 从云端同步分类数据
     */
    public void syncFromCloud() {
        repository.syncCategoriesFromCloud(success -> {
            // 同步结果暂不通过 operationState 暴露，仅用于后台更新
        });
    }

    /**
     * 获取支出分类
     */
    public LiveData<List<CategoryWithSubCategories>> getExpenseCategories(String userId) {
        return repository.getCategoriesWithSubs(userId, "expense");
    }

    public List<CategoryWithSubCategories> getExpenseSnapshot() {
        return repository.getCategoriesSnapshot("expense");
    }

    /**
     * 获取收入分类
     */
    public LiveData<List<CategoryWithSubCategories>> getIncomeCategories(String userId) {
        return repository.getCategoriesWithSubs(userId, "income");
    }

    public List<CategoryWithSubCategories> getIncomeSnapshot() {
        return repository.getCategoriesSnapshot("income");
    }

    /**
     * 获取转账分类（含兜底预设）
     */
    public LiveData<List<CategoryWithSubCategories>> getTransferCategories(String userId) {
        androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> result = new androidx.lifecycle.MediatorLiveData<>();
        result.addSource(repository.getCategoriesWithSubs(userId, "transfer"), categories -> {
            if (categories == null || categories.isEmpty()) {
                List<CategoryWithSubCategories> presets = new ArrayList<>();
                
                Category c1 = new Category();
                c1.setName("转账");
                c1.setIconUri("ic_qiehuan");
                c1.setType("transfer");
                c1.setCloudId("system_transfer_1");
                
                CategoryWithSubCategories item1 = new CategoryWithSubCategories();
                item1.category = c1;
                presets.add(item1);
                
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

    public void insert(Category category) { repository.insert(category); }

    public void update(Category category) { repository.update(category); }

    public void updateCategorySafe(long id, String newName, String newIconUri, String newIconBgColor, boolean excludeBudget) {
        AppExecutors.get().diskIO().execute(() -> {
            Category existing = repository.getCategoryById(id);
            if (existing == null) return;

            existing.setName(newName);
            existing.setIconUri(newIconUri);
            existing.setIconBackgroundColor(newIconBgColor);
            existing.setExcludeBudget(excludeBudget);
            existing.markUpdatedForSync();

            repository.update(existing);
        });
    }

    public void deleteCategoryById(long categoryId) { repository.deleteCategoryById(categoryId); }

    public void checkCategoryBills(String userId, String cloudId, Consumer<Integer> callback) {
        repository.checkBillsCount(userId, cloudId, callback);
    }

    /**
     * 更新全部分类排序索引
     */
    public void updateCategoryOrder(List<Category> categories) {
        AppExecutors.get().diskIO().execute(() -> {
            if (categories == null) return;
            for (int i = 0; i < categories.size(); i++) {
                Category cat = categories.get(i);
                cat.setSortIndex(i);
                cat.markUpdatedForSync();
            }
            repository.updateAll(categories);
        });
    }

    /**
     * 归档一级分类
     */
    public void archiveCategory(long id, boolean archiveChildren) {
        _operationState.setValue(ApiResponse.loading("正在归档..."));
        repository.archiveCategory(id, archiveChildren);
        _operationState.postValue(ApiResponse.success("已归档"));
    }

    /**
     * 归档二级分类
     */
    public void archiveSubCategory(long id) {
        _operationState.setValue(ApiResponse.loading("正在归档..."));
        repository.archiveSubCategory(id);
        _operationState.postValue(ApiResponse.success("已归档"));
    }

    /**
     * 恢复已归档分类
     */
    public void restoreCategory(long id, boolean isSub) {
        _operationState.setValue(ApiResponse.loading("正在恢复..."));
        repository.restoreCategory(id, isSub);
        _operationState.postValue(ApiResponse.success("已恢复"));
    }

    /**
     * 迁移分类下的账单数据
     */
    public void migrateCategoryData(String userId, String sourceId, Category target) {
        if (userId == null || sourceId == null || target == null) {
            _operationState.setValue(ApiResponse.error("参数缺失，无法迁移"));
            return;
        }
        _operationState.setValue(ApiResponse.loading("正在迁移账单..."));
        repository.migrateBills(userId, sourceId, target, count -> {
            if (count > 0) {
                _operationState.postValue(ApiResponse.success("成功迁移 " + count + " 条账单"));
            } else {
                _operationState.postValue(ApiResponse.error("该分类下暂无账单，无需迁移"));
            }
        });
    }

    /**
     * 调整二级分类的归属（父分类）
     */
    public void changeParentCategory(long subId, Category newParent) {
        if (newParent == null) return;
        _operationState.setValue(ApiResponse.loading("正在调整归属..."));
        repository.changeSubCategoryParent(subId, newParent);
        _operationState.postValue(ApiResponse.success("已调整"));
    }

    /**
     * 将二级分类晋升为一级分类
     */
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

    /**
     * 获取用户所有已归档的分类
     */
    public LiveData<List<CategoryWithSubCategories>> getArchivedCategories(String userId) {
        if (userId == null) return new MutableLiveData<>(new ArrayList<>());

        androidx.lifecycle.MediatorLiveData<List<CategoryWithSubCategories>> result = new androidx.lifecycle.MediatorLiveData<>();
        LiveData<List<CategoryWithSubCategories>> expenseSource = repository.getCategoriesWithSubs(userId, "expense");
        LiveData<List<CategoryWithSubCategories>> incomeSource = repository.getCategoriesWithSubs(userId, "income");

        result.addSource(expenseSource, categories -> combineAndFilterArchived(result, categories, incomeSource.getValue()));
        result.addSource(incomeSource, categories -> combineAndFilterArchived(result, expenseSource.getValue(), categories));
        
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

            if (cw.category.getArchiveStatus() != null && cw.category.getArchiveStatus() == 1) {
                archived.add(cw);
            } else if (cw.subCategories != null) {
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
