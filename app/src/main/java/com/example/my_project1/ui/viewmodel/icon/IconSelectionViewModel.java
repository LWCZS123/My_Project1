package com.example.my_project1.ui.viewmodel.icon;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.repository.icon.IconRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class IconSelectionViewModel extends AndroidViewModel {

    private final IconRepository repository;
    private final Pattern chinesePattern = Pattern.compile("^[\\u4e00-\\u9fa5]+$");

    private final MutableLiveData<List<IconCategory>> _categories = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconCategory>> categories = _categories;

    private final MutableLiveData<IconCategory> _selectedCategory = new MutableLiveData<>();
    public final LiveData<IconCategory> selectedCategory = _selectedCategory;

    private final MutableLiveData<List<IconItem>> _iconItems = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconItem>> iconItems = _iconItems;

    private final MutableLiveData<IconItem> _selectedIcon = new MutableLiveData<>();
    public final LiveData<IconItem> selectedIcon = _selectedIcon;

    private final MutableLiveData<String> _selectedColor = new MutableLiveData<>("#EF5350");
    public final LiveData<String> selectedColor = _selectedColor;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public final LiveData<Boolean> loading = _loading;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    public IconSelectionViewModel(@NonNull Application application) {
        super(application);
        this.repository = IconRepository.getInstance();
    }

    /**
     * 加载初始数据：前20个分类
     */
    public void loadInitialData() {
        _loading.setValue(true);
        // 加载第一页分类 (10个)
        repository.getCategoryPage(0, new IconRepository.Callback<List<IconCategory>>() {
            @Override
            public void onSuccess(List<IconCategory> page1) {
                // 加载第二页分类 (10个)
                repository.getCategoryPage(1, new IconRepository.Callback<List<IconCategory>>() {
                    @Override
                    public void onSuccess(List<IconCategory> page2) {
                        List<IconCategory> all = new ArrayList<>(page1);
                        all.addAll(page2);
                        _categories.postValue(all);
                        if (!all.isEmpty()) {
                            selectCategory(all.get(0));
                        }
                        _loading.postValue(false);
                    }

                    @Override
                    public void onError(String message) {
                        _categories.postValue(page1);
                        if (!page1.isEmpty()) selectCategory(page1.get(0));
                        _loading.postValue(false);
                    }
                });
            }

            @Override
            public void onError(String message) {
                _errorMessage.postValue(message);
                _loading.postValue(false);
            }
        });
    }

    /**
     * 选中分类，加载该分类前50个图标
     */
    public void selectCategory(IconCategory category) {
        if (_selectedCategory.getValue() == category) return;
        _selectedCategory.setValue(category);
        _loading.setValue(true);

        repository.getCategoryDetail(category, 0, new IconRepository.Callback<List<IconItem>>() {
            @Override
            public void onSuccess(List<IconItem> page1) {
                repository.getCategoryDetail(category, 1, new IconRepository.Callback<List<IconItem>>() {
                    @Override
                    public void onSuccess(List<IconItem> page2) {
                        List<IconItem> all = new ArrayList<>(page1);
                        all.addAll(page2);

                        // 过滤：仅显示纯中文名称的图标
                        List<IconItem> filtered = new ArrayList<>();
                        for (IconItem item : all) {
                            if (item.getName() != null && chinesePattern.matcher(item.getName()).matches()) {
                                filtered.add(item);
                            }
                        }

                        // 只取前 50 个
                        if (filtered.size() > 50) {
                            filtered = filtered.subList(0, 50);
                        }
                        _iconItems.postValue(filtered);
                        _loading.postValue(false);
                    }

                    @Override
                    public void onError(String message) {
                        List<IconItem> filtered = new ArrayList<>();
                        for (IconItem item : page1) {
                            if (item.getName() != null && chinesePattern.matcher(item.getName()).matches()) {
                                filtered.add(item);
                            }
                        }
                        _iconItems.postValue(filtered);
                        _loading.postValue(false);
                    }
                });
            }

            @Override
            public void onError(String message) {
                _errorMessage.postValue(message);
                _loading.postValue(false);
            }
        });
    }

    public void selectIcon(IconItem icon) {
        _selectedIcon.setValue(icon);
    }

    public void selectColor(String color) {
        _selectedColor.setValue(color);
    }

    public void search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            if (_selectedCategory.getValue() != null) {
                IconCategory current = _selectedCategory.getValue();
                _selectedCategory.setValue(null);
                selectCategory(current);
            }
            return;
        }

        _loading.setValue(true);
        repository.search(keyword, 0, new IconRepository.Callback<List<IconItem>>() {
            @Override
            public void onSuccess(List<IconItem> data) {
                List<IconItem> filtered = new ArrayList<>();
                for (IconItem item : data) {
                    if (item.getName() != null && chinesePattern.matcher(item.getName()).matches()) {
                        filtered.add(item);
                    }
                }

                if (filtered.size() > 50) filtered = filtered.subList(0, 50);
                _iconItems.postValue(filtered);
                _loading.postValue(false);
            }

            @Override
            public void onError(String message) {
                _errorMessage.postValue(message);
                _loading.postValue(false);
            }
        });
    }
}
