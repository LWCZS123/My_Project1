package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.CategoryIconGroup;
import com.example.my_project1.data.model.account.IconItem;
import com.example.my_project1.data.model.response.CategoryResponse;
import com.example.my_project1.databinding.FragmentIconAccountBottomSheetBinding;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
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

public class IconAccountBottomSheet extends BottomSheetDialogFragment {

    private FragmentIconAccountBottomSheetBinding binding;
    private IconListAdapter adapter;
    private List<CategoryIconGroup> fullList;
    private Call currentCall;
    private boolean onlyBanks = false;

    private static final String ICON_DATA_URL =
            "https://xdicons.oss-cn-beijing.aliyuncs.com/icons/icons.json";

    public interface OnIconSelectedListener {
        void onIconSelected(IconItem item);
    }

    private OnIconSelectedListener listener;

    public void setOnIconSelectedListener(OnIconSelectedListener listener) {
        this.listener = listener;
    }

    public void setOnlyBanks(boolean onlyBanks) {
        this.onlyBanks = onlyBanks;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIconAccountBottomSheetBinding.inflate(inflater, container, false);

        adapter = new IconListAdapter(item -> {
            if (listener != null) listener.onIconSelected(item);
            dismiss();
        });

        binding.rvIcons.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvIcons.setAdapter(adapter);

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

        List<Object> items = new ArrayList<>();

        for (CategoryIconGroup group : fullList) {
            boolean isBank = "银行".equals(group.getName());
            boolean isPlatform = "平台".equals(group.getName());

            if (onlyBanks && !isBank) continue;

            if (isBank || isPlatform) {
                items.add(group.getName());
                for (String url : group.getIcons()) {
                    String fileName = url.substring(url.lastIndexOf("/") + 1);
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        fileName = fileName.substring(0, dotIndex);
                    }
                    items.add(new IconItem(url, fileName));
                }
            }
        }

        adapter.setData(items);
    }

    private void showLoading() {
        binding.rvIcons.setVisibility(View.GONE);
        binding.lottieLoading.setVisibility(View.VISIBLE);
        binding.lottieLoading.playAnimation();
        binding.lottieError.setVisibility(View.GONE);
    }

    private void showContent() {
        binding.rvIcons.setVisibility(View.VISIBLE);
        binding.lottieLoading.setVisibility(View.GONE);
        binding.lottieLoading.pauseAnimation();
        binding.lottieError.setVisibility(View.GONE);
    }

    private void showError() {
        binding.rvIcons.setVisibility(View.GONE);
        binding.lottieLoading.setVisibility(View.GONE);
        binding.lottieError.setVisibility(View.VISIBLE);
        binding.lottieError.playAnimation();
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
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1)
                );

                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.75);
                sheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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
        binding = null;
    }

    private static class IconListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<Object> items = new ArrayList<>();
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(IconItem item);
        }

        public IconListAdapter(OnItemClickListener listener) {
            this.listener = listener;
        }

        public void setData(List<Object> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_type_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_type, parent, false);
                return new ItemViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvTitle.setText((String) items.get(position));
            } else {
                IconItem item = (IconItem) items.get(position);
                ItemViewHolder itemHolder = (ItemViewHolder) holder;
                itemHolder.tvName.setText(item.getName());
                ImageLoaderUtils.loadThumbnail(itemHolder.ivIcon.getContext(), item.getUrl(), itemHolder.ivIcon);
                itemHolder.itemView.setOnClickListener(v -> listener.onItemClick(item));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            HeaderViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_header_title);
            }
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivIcon;
            ItemViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                ivIcon = itemView.findViewById(R.id.iv_icon);
            }
        }
    }
}
