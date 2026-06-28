package com.example.my_project1.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.repository.SubCategoryRepository;
import com.example.my_project1.utils.AppExecutors;

import java.util.List;

import io.reactivex.annotations.NonNull;

/**
 * SubCategoryViewModel
 * ---------------------------
 * 管理子分类相关数据
 */
public class SubCategoryViewModel extends AndroidViewModel {

    private final SubCategoryRepository repository;

    public SubCategoryViewModel(@NonNull Application application) {
        super(application);
        repository = new SubCategoryRepository(application);
    }

    /** 根据父分类 id 查询子分类 */
    public LiveData<List<SubCategory>> getSubCategoriesByParent(long parentId) {
        return repository.getSubCategoriesByParent(parentId);
    }
    public void syncFromCloud() {
        repository.syncSubCategoriesFromCloud(success -> {
            if (success) {
                Log.d("SubCategoryVM", "✅ 云端同步成功");
            } else {
                Log.e("SubCategoryVM", "❌ 云端同步失败");
            }
        });
    }

    /** 基础操作 */
    public void insert(SubCategory subCategory) { repository.insert(subCategory); }

    public void update(SubCategory subCategory) { repository.update(subCategory); }

    public void deleteSubCategoryById(long subCategoryId) { repository.deleteSubCategoryById(subCategoryId); }
    public void updateSubCategorySafe(long id, String newName, String newIconUri, boolean excludeBudget) {
        AppExecutors.get().diskIO().execute(() -> {
            SubCategory existing = repository.getSubCategoryById(id); // 或直接调用 DAO
            if (existing == null) {
                Log.w("SubCategoryVM", "找不到分类 id=" + id);
                return;
            }

            // 只修改需要改动的字段（保留 cloudId / ownerId / 其它字段）
            existing.setName(newName);
            existing.setIconUri(newIconUri);
            //existing.setExcludeBudget(excludeBudget);
            existing.markUpdatedForSync(); // 更新 updatedAt 并 set syncState = TO_UPDATE

            // 保存到本地（会触发 LiveData -> UI 更新）
            repository.update(existing);
            // repository.update() 应该会执行 diskIO().execute(() -> dao.update(existing));
        });
    }
}
