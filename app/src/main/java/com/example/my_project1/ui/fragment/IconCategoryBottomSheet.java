package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryIconGroup;
import com.example.my_project1.data.model.response.CategoryResponse;
import com.example.my_project1.ui.adapter.IconGridAdapter;
import com.example.my_project1.ui.custom.VerticalTabLayout;
import com.example.my_project1.utils.AppExecutors;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class IconCategoryBottomSheet extends BottomSheetDialogFragment {

    private VerticalTabLayout verticalTabLayout;
    private RecyclerView iconRecycler;
    private LottieAnimationView lottieLoading, lottieError;
    private IconGridAdapter iconAdapter;
    private List<CategoryIconGroup> categoryList;
    private CardView cardContainer;
    private OnIconSelectedListener listener;

    // keep a reference to the current network Call so we can cancel it on teardown
    private Call currentCall;

    private static final String ICON_DATA_URL =
            "https://xdicons.oss-cn-beijing.aliyuncs.com/icons/icons.json";

    public interface OnIconSelectedListener {
        void onIconSelected(String iconUrl);
    }

    public void setOnIconSelectedListener(OnIconSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_icon_category_bottom_sheet, container, false);

        verticalTabLayout = view.findViewById(R.id.category_tab_layout);
        iconRecycler = view.findViewById(R.id.icon_recycler);
        lottieLoading = view.findViewById(R.id.lottie_loading);
        lottieError = view.findViewById(R.id.lottie_error);
        cardContainer = view.findViewById(R.id.card_container);

        iconRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        iconAdapter = new IconGridAdapter(requireContext());
        iconRecycler.setAdapter(iconAdapter);

        // 转发 adapter 点击事件到 Fragment，然后交给外部 listener
        iconAdapter.setOnIconClickListener(iconUrl -> {
            if (listener != null) {
                listener.onIconSelected(iconUrl);
            }
            dismiss();
        });

        // 点击错误动画重试
        if (lottieError != null) {
            lottieError.setOnClickListener(v -> loadIconData());
        }

        loadIconData();
        return view;
    }

    private void loadIconData() {
        // 如果 fragment 已经 detach，直接返回
        if (!isAdded()) return;

        showLoading();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(ICON_DATA_URL).build();
        currentCall = client.newCall(request);

        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 切到主线程更新 UI
                AppExecutors.get().mainThread().execute(() -> {
                    // 如果 fragment 不在 UI 中，不做 UI 更新
                    if (!isAdded()) return;
                    showError();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 如果 fragment 已被移除，不再处理响应
                if (!isAdded()) {
                    if (response != null) response.close();
                    return;
                }
                if (response == null || !response.isSuccessful()) {
                    AppExecutors.get().mainThread().execute(() -> {
                        if (!isAdded()) return;
                        showError();
                    });
                    if (response != null) response.close();
                    return;
                }

                // 安全地读取 body 并解析
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        AppExecutors.get().mainThread().execute(() -> {
                            if (!isAdded()) return;
                            showError();
                        });
                        return;
                    }
                    String json = body.string();
                    // 解析在后台线程（当前线程为 OkHttp 的线程），若解析耗时可继续在线程池执行
                    CategoryResponse responseObj;
                    try {
                        responseObj = new Gson().fromJson(json, CategoryResponse.class);
                    } catch (Exception ex) {
                        responseObj = null;
                    }

                    if (responseObj == null || responseObj.getCategories() == null ||
                            responseObj.getCategories().isEmpty()) {
                        AppExecutors.get().mainThread().execute(() -> {
                            if (!isAdded()) return;
                            showError();
                        });
                        return;
                    }

                    // 成功解析后赋值并回到主线程更新 UI
                    categoryList = responseObj.getCategories();

                    //过滤分类（银行、平台）
                    filterHiddenCategories();
                    AppExecutors.get().mainThread().execute(() -> {
                        if (!isAdded()) return;
                        showContent();
                        setupTabs();
                    });

                } catch (Exception e) {
                    AppExecutors.get().mainThread().execute(() -> {
                        if (!isAdded()) return;
                        showError();
                    });
                }
            }


        });
    }
    private void filterHiddenCategories() {
        if (categoryList == null) return;

        List<CategoryIconGroup> filtered = new ArrayList<>();
        for (CategoryIconGroup group : categoryList) {
            String name = group.getName();
            // 不展示 银行 和 平台
            if (!"银行".equals(name) && !"平台".equals(name)) {
                filtered.add(group);
            }
        }

        categoryList = filtered;
    }

    private void setupTabs() {
        if (categoryList == null || categoryList.isEmpty()) return;
        if (verticalTabLayout == null || iconAdapter == null) return;

        List<String> names = new ArrayList<>();
        for (CategoryIconGroup group : categoryList) {
            names.add(group.getName());
        }

        // 将 tab 名称与回调设置给自定义 VerticalTabLayout
        verticalTabLayout.setTabs(names, (position, text) -> {
            if (categoryList == null || position < 0 || position >= categoryList.size()) return;
            List<String> icons = categoryList.get(position).getIcons();
            if (icons == null) icons = new ArrayList<>();
            iconAdapter.setData(icons);
        });

        // 默认显示第一个分类（若存在）
        List<String> firstIcons = categoryList.get(0).getIcons();
        if (firstIcons == null) firstIcons = new ArrayList<>();
        iconAdapter.setData(firstIcons);
    }

    private void showLoading() {
        // 使用字段引用并检查 null
        if (cardContainer != null) cardContainer.setVisibility(View.GONE);
        if (verticalTabLayout != null) verticalTabLayout.setVisibility(View.GONE);
        if (iconRecycler != null) iconRecycler.setVisibility(View.GONE);

        if (lottieLoading != null) {
            lottieLoading.setVisibility(View.VISIBLE);
            lottieLoading.playAnimation();
        }
        if (lottieError != null) {
            lottieError.setVisibility(View.GONE);
            lottieError.pauseAnimation();
        }
    }

    private void showError() {
        if (!isAdded()) return;

        if (cardContainer != null) cardContainer.setVisibility(View.GONE);
        if (verticalTabLayout != null) verticalTabLayout.setVisibility(View.GONE);
        if (iconRecycler != null) iconRecycler.setVisibility(View.GONE);

        if (lottieLoading != null) {
            lottieLoading.setVisibility(View.GONE);
            lottieLoading.pauseAnimation();
        }
        if (lottieError != null) {
            lottieError.setVisibility(View.VISIBLE);
            lottieError.playAnimation();
        }
    }

    private void showContent() {
        if (!isAdded()) return;

        if (cardContainer != null) cardContainer.setVisibility(View.VISIBLE);
        if (verticalTabLayout != null) verticalTabLayout.setVisibility(View.VISIBLE);
        if (iconRecycler != null) iconRecycler.setVisibility(View.VISIBLE);

        if (lottieLoading != null) {
            lottieLoading.setVisibility(View.GONE);
            lottieLoading.pauseAnimation();
        }
        if (lottieError != null) {
            lottieError.setVisibility(View.GONE);
            lottieError.pauseAnimation();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null && isAdded()) {
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            if (bottomSheetDialog.getWindow() != null) {
                bottomSheetDialog.getWindow().setWindowAnimations(R.style.BottomSheetDialogAnimation);
            }
        });
        return bottomSheetDialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 取消未完成的网络请求，避免回调到已销毁的 fragment
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
            currentCall = null;
        }

        // 清理 adapter 的回调，减少泄漏风险
        if (iconAdapter != null) {
            iconAdapter.setOnIconClickListener(null);
        }

        // 停止并释放 lottie 动画
        if (lottieLoading != null) {
            lottieLoading.cancelAnimation();
        }
        if (lottieError != null) {
            lottieError.cancelAnimation();
        }

       //释放view
        verticalTabLayout = null;
        iconRecycler = null;
        lottieLoading = null;
        lottieError = null;
        cardContainer = null;
        iconAdapter = null;
        categoryList = null;
        listener = null;
    }
}
