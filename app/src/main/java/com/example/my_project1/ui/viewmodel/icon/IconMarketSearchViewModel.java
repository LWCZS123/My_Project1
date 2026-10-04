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
import java.util.concurrent.atomic.AtomicInteger;

public class IconMarketSearchViewModel extends AndroidViewModel {

    public enum ResultType { COLLECTION, ICON }

    private final IconRepository repository = IconRepository.getInstance();
    private final AtomicInteger requestVersion = new AtomicInteger();

    private final MutableLiveData<ResultType> _resultType =
            new MutableLiveData<>(ResultType.ICON);
    public final LiveData<ResultType> resultType = _resultType;

    private final MutableLiveData<IconMarketViewModel.IconStyle> _style =
            new MutableLiveData<>(IconMarketViewModel.IconStyle.ALL);
    public final LiveData<IconMarketViewModel.IconStyle> style = _style;

    private final MutableLiveData<List<IconItem>> _icons =
            new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconItem>> icons = _icons;

    private final MutableLiveData<List<IconCategory>> _collections =
            new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<IconCategory>> collections = _collections;

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public final LiveData<Boolean> loading = _loading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public final LiveData<String> error = _error;

    private String keyword = "";

    public IconMarketSearchViewModel(@NonNull Application application) {
        super(application);
    }

    public void setResultType(ResultType resultType) {
        if (resultType == _resultType.getValue()) return;
        _resultType.setValue(resultType);
        search(keyword);
    }

    public void setStyle(IconMarketViewModel.IconStyle style) {
        if (style == _style.getValue()) return;
        _style.setValue(style);
        search(keyword);
    }

    public void search(String query) {
        keyword = query == null ? "" : query.trim();
        int version = requestVersion.incrementAndGet();
        if (keyword.isEmpty()) {
            _loading.setValue(false);
            _icons.setValue(new ArrayList<>());
            _collections.setValue(new ArrayList<>());
            return;
        }

        _loading.setValue(true);
        ResultType type = _resultType.getValue() == null ? ResultType.ICON : _resultType.getValue();
        String styleFilter = toRepositoryStyle(_style.getValue());
        if (type == ResultType.ICON) {
            _icons.setValue(new ArrayList<>());
            repository.searchAllIcons(getApplication().getAssets(), keyword, styleFilter,
                    new IconRepository.Callback<List<IconItem>>() {
                        @Override
                        public void onSuccess(List<IconItem> data) {
                            if (version != requestVersion.get()) return;
                            _loading.setValue(false);
                            _icons.setValue(data == null ? new ArrayList<>() : data);
                        }

                        @Override
                        public void onError(String message) {
                            if (version != requestVersion.get()) return;
                            _loading.setValue(false);
                            _error.setValue(message);
                        }
                    });
        } else {
            _collections.setValue(new ArrayList<>());
            repository.searchAllCategories(getApplication().getAssets(), keyword, styleFilter,
                    new IconRepository.Callback<List<IconCategory>>() {
                        @Override
                        public void onSuccess(List<IconCategory> data) {
                            if (version != requestVersion.get()) return;
                            _loading.setValue(false);
                            _collections.setValue(data == null ? new ArrayList<>() : data);
                        }

                        @Override
                        public void onError(String message) {
                            if (version != requestVersion.get()) return;
                            _loading.setValue(false);
                            _error.setValue(message);
                        }
                    });
        }
    }

    public String getKeyword() {
        return keyword;
    }

    public void clearError() {
        _error.setValue(null);
    }

    private String toRepositoryStyle(IconMarketViewModel.IconStyle style) {
        if (style == IconMarketViewModel.IconStyle.LINEAR) return "line";
        if (style == IconMarketViewModel.IconStyle.COLORED) return "lineal-color";
        if (style == IconMarketViewModel.IconStyle.DEFAULT) return "filled";
        return null;
    }
}
