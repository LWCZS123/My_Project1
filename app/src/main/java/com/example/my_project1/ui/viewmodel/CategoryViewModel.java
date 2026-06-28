package com.example.my_project1.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.CategoryWithSubCategories;
import com.example.my_project1.data.repository.CategoryRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.List;

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
        if (expenseCategories == null){
            expenseCategories = repository.getCategoriesWithSubs(userId,"expense");
        }
        return expenseCategories;
    }


    /** 获取收入分类 */
    public LiveData<List<CategoryWithSubCategories>> getIncomeCategories(String userId) {
        if (incomeCategories == null) {
            incomeCategories = repository.getCategoriesWithSubs(userId,"income");
        }
        return incomeCategories;
    }

    /** 操作方法 */
    public void insert(Category category) { repository.insert(category); }

    public void update(Category category) { repository.update(category); }

    public void deleteCategoryById(long categoryId) { repository.deleteCategoryById(categoryId); }

    public void updateCategorySafe(long id, String newName, String newIconUri, boolean excludeBudget) {
        AppExecutors.get().diskIO().execute(() -> {
            Category existing = repository.getCategoryById(id); // 或直接调用 DAO
            if (existing == null) {
                Log.w("CategoryVM", "找不到分类 id=" + id);
                return;
            }

            // 只修改需要改动的字段（保留 cloudId / ownerId / 其它字段）
            existing.setName(newName);
            existing.setIconUri(newIconUri);
            existing.setExcludeBudget(excludeBudget);
            existing.markUpdatedForSync(); // 更新 updatedAt 并 set syncState = TO_UPDATE

            // 保存到本地（会触发 LiveData -> UI 更新）
            repository.update(existing);
            // repository.update() 应该会执行 diskIO().execute(() -> dao.update(existing));
        });
    }
}
