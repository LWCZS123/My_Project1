package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryIconGroup;
import com.example.my_project1.data.model.account.IconItem;
import com.example.my_project1.data.model.response.CategoryResponse;
import com.example.my_project1.databinding.FragmentIconAccountBottomSheetBinding;
import com.example.my_project1.ui.adapter.account.IconAccountGridAdapter;
import com.example.my_project1.utils.AppExecutors;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class IconAccountBottomSheet extends BottomSheetDialogFragment {

    private FragmentIconAccountBottomSheetBinding binding;
    private IconAccountGridAdapter platformAdapter, bankAdapter;
    private List<CategoryIconGroup> fullList;
    private Call currentCall;

    private static final String ICON_DATA_URL =
            "https://xdicons.oss-cn-beijing.aliyuncs.com/icons/icons.json";

    public interface OnIconSelectedListener {
        void onIconSelected(IconItem item);
    }

    private OnIconSelectedListener listener;

    public void setOnIconSelectedListener(OnIconSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIconAccountBottomSheetBinding.inflate(inflater, container, false);

        // RecyclerView
        binding.platformRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 6));
        binding.platformRecyclerView.setNestedScrollingEnabled(false);

        binding.bankRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 6));

        binding.bankRecyclerView.setNestedScrollingEnabled(false);
        platformAdapter = new IconAccountGridAdapter(requireContext());
        bankAdapter = new IconAccountGridAdapter(requireContext());

        binding.platformRecyclerView.setAdapter(platformAdapter);
        binding.bankRecyclerView.setAdapter(bankAdapter);

        // 点击回调
        platformAdapter.setOnIconClickListener(item -> {
            if (listener != null) listener.onIconSelected(item);
            dismiss();
        });
        bankAdapter.setOnIconClickListener(item -> {
            if (listener != null) listener.onIconSelected(item);
            dismiss();
        });

        binding.lottieError.setOnClickListener(v -> loadData());

        loadData();
        return binding.getRoot();
    }

    private void loadData() {
        if (!isAdded()) return;

        showLoading();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(ICON_DATA_URL).build();
        currentCall = client.newCall(request);

        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AppExecutors.get().mainThread().execute(() -> showError());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!isAdded() || response == null) return;

                if (!response.isSuccessful()) {
                    AppExecutors.get().mainThread().execute(() -> showError());
                    response.close();
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        AppExecutors.get().mainThread().execute(() -> showError());
                        return;
                    }

                    String json = body.string();
                    CategoryResponse res;
                    try {
                        res = new Gson().fromJson(json, CategoryResponse.class);
                    } catch (Exception e) {
                        res = null;
                    }

                    if (res == null || res.getCategories() == null) {
                        AppExecutors.get().mainThread().execute(() -> showError());
                        return;
                    }

                    fullList = res.getCategories();

                    AppExecutors.get().mainThread().execute(() -> {
                        showContent();
                        updateUI();
                    });
                }
            }
        });
    }

    private void updateUI() {
        if (fullList == null) return;

        List<IconItem> platformIcons = new ArrayList<>();
        List<IconItem> bankIcons = new ArrayList<>();

        for (CategoryIconGroup group : fullList) {
            if ("平台".equals(group.getName())) {
                for (String url : group.getIcons()) {
                    String fileName = url.substring(url.lastIndexOf("/") + 1);
                    // 去掉后缀
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        fileName = fileName.substring(0, dotIndex);
                    }
                    platformIcons.add(new IconItem(url, fileName));                }
            }
            if ("银行".equals(group.getName())) {
                for (String url : group.getIcons()) {
                    String fileName = url.substring(url.lastIndexOf("/") + 1);
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        fileName = fileName.substring(0, dotIndex);
                    }
                    bankIcons.add(new IconItem(url, fileName));                }
            }
        }

        platformAdapter.setData(platformIcons);
        bankAdapter.setData(bankIcons);
    }

    private void showLoading() {
        binding.contentContainer.setVisibility(View.GONE);
        binding.lottieLoading.setVisibility(View.VISIBLE);
        binding.lottieLoading.playAnimation();
        binding.lottieError.setVisibility(View.GONE);
        binding.lottieError.pauseAnimation();
        binding.lottieError.bringToFront();

    }

    private void showContent() {
        binding.contentContainer.setVisibility(View.VISIBLE);
        binding.lottieLoading.setVisibility(View.GONE);
        binding.lottieLoading.pauseAnimation();
        binding.lottieError.setVisibility(View.GONE);
        binding.lottieError.pauseAnimation();

    }

    private void showError() {
        binding.contentContainer.setVisibility(View.GONE);
        binding.lottieLoading.setVisibility(View.GONE);
        binding.lottieLoading.pauseAnimation();
        binding.lottieError.setVisibility(View.VISIBLE);
        binding.lottieError.playAnimation();
        binding.lottieError.bringToFront();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );

            if (sheet != null) {
                sheet.setBackground(
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet)
                );

                // 设置高度 = 屏幕高度的 75%
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.75);

                sheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }

            if (dialog.getWindow() != null) {
                dialog.getWindow().setWindowAnimations(R.style.BottomSheetDialogAnimation);
            }
        });

        return dialog;
    }

    private int getScreenHeight() {
        return requireContext().getResources().getDisplayMetrics().heightPixels;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (currentCall != null && !currentCall.isCanceled()) currentCall.cancel();
        if (platformAdapter != null) platformAdapter.setOnIconClickListener(null);
        if (bankAdapter != null) bankAdapter.setOnIconClickListener(null);
        if (binding.lottieLoading != null) binding.lottieLoading.cancelAnimation();
        if (binding.lottieError != null) binding.lottieError.cancelAnimation();

        platformAdapter = null;
        bankAdapter = null;
        listener = null;
        binding = null;
    }
}
