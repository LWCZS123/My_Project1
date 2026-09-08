package com.example.my_project1.ui.viewmodel.icon;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.my_project1.data.model.icon.IconCategory;
import com.example.my_project1.data.model.icon.IconItem;
import com.example.my_project1.data.repository.icon.IconRepository;
import com.example.my_project1.utils.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class IconSelectionViewModel extends AndroidViewModel {

    public static final String STYLE_LINEAR = "线性图标";
    public static final String STYLE_EMOJI = "Emoji";
    public static final String STYLE_PRO = "彩色图标(PRO)";

    private final IconRepository repository;
    private final Pattern chinesePattern = Pattern.compile("^[\\u4e00-\\u9fa5]+$");

    private final MutableLiveData<String> _currentStyle = new MutableLiveData<>(STYLE_LINEAR);
    public final LiveData<String> currentStyle = _currentStyle;

    private JSONObject linearPacksJson;
    private JSONObject emojiPacksJson;

    private final MutableLiveData<List<IconCategory>> _categories = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconCategory>> categories = _categories;

    private final MutableLiveData<IconCategory> _selectedCategory = new MutableLiveData<>();
    public final LiveData<IconCategory> selectedCategory = _selectedCategory;

    private final MutableLiveData<List<IconItem>> _iconItems = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconItem>> iconItems = _iconItems;

    private final MutableLiveData<IconItem> _selectedIcon = new MutableLiveData<>();
    public final LiveData<IconItem> selectedIcon = _selectedIcon;

    private final MutableLiveData<String> _selectedColor = new MutableLiveData<>(null);
    public final LiveData<String> selectedColor = _selectedColor;

    private final MutableLiveData<Boolean> _includeInBudget = new MutableLiveData<>(true);
    public final LiveData<Boolean> includeInBudget = _includeInBudget;

    private final MutableLiveData<String> _mode = new MutableLiveData<>("add"); // "add" or "modify"
    public final LiveData<String> mode = _mode;

    private final MutableLiveData<String> _targetType = new MutableLiveData<>("category"); // "category" or "subcategory"
    public final LiveData<String> targetType = _targetType;

    private final MutableLiveData<Long> _targetId = new MutableLiveData<>(-1L);
    public final LiveData<Long> targetId = _targetId;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public final LiveData<Boolean> loading = _loading;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    public IconSelectionViewModel(@NonNull Application application) {
        super(application);
        this.repository = IconRepository.getInstance();
    }

    /**
     * 加载初始数据
     */
    public void loadInitialData() {
        String style = _currentStyle.getValue();
        if (STYLE_LINEAR.equals(style)) {
            loadLinearIcons();
        } else if (STYLE_EMOJI.equals(style)) {
            loadEmojiIcons();
        } else {
            loadRemoteIcons();
        }
    }

    private void loadRemoteIcons() {
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
     * 加载 assets 中的线性图标
     */
    private void loadLinearIcons() {
        if (linearPacksJson != null) {
            displayLinearCategories();
            return;
        }

        _loading.setValue(true);
        AppExecutors.get().diskIO().execute(() -> {
            try (InputStream is = getApplication().getAssets().open("线性色.json")) {
                int size = is.available();
                byte[] buffer = new byte[size];
                int read = is.read(buffer);
                if (read <= 0) return;
                String json = new String(buffer, 0, read, StandardCharsets.UTF_8);

                linearPacksJson = new JSONObject(json).getJSONObject("packs");
                displayLinearCategories();
            } catch (Exception e) {
                _errorMessage.postValue("加载线性图标失败");
                _loading.postValue(false);
            }
        });
    }

    /**
     * 加载 assets 中的 Emoji 图标 (数据源：freeicon_line.json)
     * 限制展示 200 个集合
     */
    private void loadEmojiIcons() {
        if (emojiPacksJson != null) {
            displayEmojiCategories();
            return;
        }

        _loading.setValue(true);
        AppExecutors.get().diskIO().execute(() -> {
            try (InputStream is = getApplication().getAssets().open("freeicon_line.json")) {
                int size = is.available();
                byte[] buffer = new byte[size];
                int read = is.read(buffer);
                if (read <= 0) return;
                String json = new String(buffer, 0, read, StandardCharsets.UTF_8);

                emojiPacksJson = new JSONObject(json).getJSONObject("packs");
                displayEmojiCategories();
            } catch (Exception e) {
                _errorMessage.postValue("加载 Emoji 图标失败");
                _loading.postValue(false);
            }
        });
    }

    private void displayEmojiCategories() {
        try {
            List<IconCategory> categoryList = new ArrayList<>();
            Iterator<String> keys = emojiPacksJson.keys();
            int count = 0;
            while (keys.hasNext() && count < 200) { // 限制 200 个集合
                String key = keys.next();
                JSONObject packObj = emojiPacksJson.getJSONObject(key);
                IconCategory cat = new IconCategory();
                cat.setCategory(packObj.optString("title"));
                cat.setFile(key);

                JSONArray iconsArr = packObj.optJSONArray("icons");
                if (iconsArr != null) {
                    cat.setCount(iconsArr.length());
                    List<String> thumbs = new ArrayList<>();
                    int thumbCount = Math.min(9, iconsArr.length());
                    for (int j = 0; j < thumbCount; j++) {
                        thumbs.add(iconsArr.getJSONObject(j).optString("cdn_url"));
                    }
                    cat.setThumbUrls(thumbs);
                }
                categoryList.add(cat);
                count++;
            }
            _categories.postValue(categoryList);
            if (!categoryList.isEmpty()) {
                selectCategory(categoryList.get(0));
            }
            _loading.postValue(false);
        } catch (Exception e) {
            _loading.postValue(false);
        }
    }

    private void displayLinearCategories() {
        try {
            List<IconCategory> categoryList = new ArrayList<>();
            Iterator<String> keys = linearPacksJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject packObj = linearPacksJson.getJSONObject(key);
                IconCategory cat = new IconCategory();
                cat.setCategory(packObj.optString("title"));
                cat.setFile(key); // 借用 file 字段存 pack key

                JSONArray iconsArr = packObj.optJSONArray("icons");
                if (iconsArr != null) {
                    cat.setCount(iconsArr.length());
                    List<String> thumbs = new ArrayList<>();
                    int thumbCount = Math.min(9, iconsArr.length());
                    for (int j = 0; j < thumbCount; j++) {
                        thumbs.add(iconsArr.getJSONObject(j).optString("cdn_url"));
                    }
                    cat.setThumbUrls(thumbs);
                }
                categoryList.add(cat);
            }
            _categories.postValue(categoryList);
            if (!categoryList.isEmpty()) {
                selectCategory(categoryList.get(0));
            }
            _loading.postValue(false);
        } catch (Exception e) {
            _loading.postValue(false);
        }
    }

    public void switchStyle(String style) {
        if (style.equals(_currentStyle.getValue())) return;
        _currentStyle.setValue(style);
        _selectedCategory.setValue(null);
        _iconItems.setValue(new ArrayList<>());
        loadInitialData();
    }

    /**
     * 选中分类，加载该分类前50个图标
     */
    public void selectCategory(IconCategory category) {
        if (_selectedCategory.getValue() == category) return;
        _selectedCategory.postValue(category);

        String style = _currentStyle.getValue();
        if (STYLE_LINEAR.equals(style)) {
            loadLocalCategoryDetail(category, linearPacksJson, 100);
            return;
        } else if (STYLE_EMOJI.equals(style)) {
            loadLocalCategoryDetail(category, emojiPacksJson, 50); // 每个集合显示 50 个图标
            return;
        }

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

    private void loadLocalCategoryDetail(IconCategory category, JSONObject packsJson, int limit) {
        if (packsJson == null) return;
        try {
            JSONObject packObj = packsJson.getJSONObject(category.getFile());
            JSONArray iconsArr = packObj.getJSONArray("icons");
            List<IconItem> items = new ArrayList<>();
            int count = Math.min(limit, iconsArr.length());
            for (int i = 0; i < count; i++) {
                JSONObject obj = iconsArr.getJSONObject(i);
                IconItem item = new IconItem();
                item.setName(obj.optString("name"));
                item.setUrl(obj.optString("cdn_url"));
                item.setThumb(obj.optString("cdn_url"));
                items.add(item);
            }
            _iconItems.postValue(items);
            if (!items.isEmpty()) {
                _selectedIcon.postValue(items.get(0));
            }
        } catch (Exception e) {
            _errorMessage.postValue("加载图标详情失败");
        }
    }

    public void selectIcon(IconItem icon) {
        _selectedIcon.setValue(icon);
    }

    public void selectColor(String color) {
        _selectedColor.setValue(color);
    }

    public void setIncludeInBudget(boolean include) {
        _includeInBudget.setValue(include);
    }

    public void setMode(String mode, String targetType, long targetId) {
        _mode.setValue(mode);
        _targetType.setValue(targetType);
        _targetId.setValue(targetId);
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

        if (STYLE_LINEAR.equals(_currentStyle.getValue())) {
            searchLocal(keyword, linearPacksJson);
            return;
        } else if (STYLE_EMOJI.equals(_currentStyle.getValue())) {
            searchLocal(keyword, emojiPacksJson);
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

    private void searchLocal(String keyword, JSONObject packsJson) {
        if (packsJson == null) return;
        AppExecutors.get().computation().execute(() -> {
            try {
                List<IconItem> results = new ArrayList<>();
                Iterator<String> keys = packsJson.keys();
                while (keys.hasNext()) {
                    JSONObject pack = packsJson.getJSONObject(keys.next());
                    JSONArray iconsArr = pack.getJSONArray("icons");
                    for (int i = 0; i < iconsArr.length(); i++) {
                        JSONObject obj = iconsArr.getJSONObject(i);
                        String name = obj.optString("name");
                        if (name.contains(keyword)) {
                            IconItem item = new IconItem();
                            item.setName(name);
                            item.setUrl(obj.optString("cdn_url"));
                            item.setThumb(obj.optString("cdn_url"));
                            results.add(item);
                        }
                    }
                }
                if (results.size() > 100) results = results.subList(0, 100);
                _iconItems.postValue(results);
                if (!results.isEmpty()) {
                    _selectedIcon.postValue(results.get(0));
                }
            } catch (Exception e) {
                // ignore
            }
        });
    }
}
