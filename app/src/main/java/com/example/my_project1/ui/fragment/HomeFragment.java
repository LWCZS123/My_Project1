package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.databinding.FragmentHomeBinding;
import com.example.my_project1.ui.activity.BillDetailActivity;
import com.example.my_project1.ui.activity.BillStatisticsActivity;
import com.example.my_project1.ui.activity.EditProfileActivity;
import com.example.my_project1.ui.activity.SearchActivity;
import com.example.my_project1.ui.adapter.bill.BillAdapter;
import com.example.my_project1.ui.adapter.bill.FooterAdapter;
import com.example.my_project1.ui.adapter.bill.HeaderAdapter;
import com.example.my_project1.ui.adapter.bill.HomeBillAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.ui.viewmodel.user.UserProfileViewModel;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.snackbar.Snackbar;

import cn.bmob.v3.BmobUser;

/**
 * HomeFragment (优化版)
 * -------------------------------------------------------
 * 1. 采用 SwipeRefreshLayout + RecyclerView 结构，提升滚动流畅度
 * 2. 使用 ConcatAdapter 组合 Header / Bill / Footer，实现多类型布局复用
 * 3. 接入 Paging 3 进行分页加载，优化大数据量下的内存表现
 * 4. 严格遵守生命周期规范，避免重复触发同步和冗余的观察者
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    
    private FragmentHomeBinding binding;
    private BillViewModel billViewModel;
    private UserProfileViewModel userViewModel;

    private HeaderAdapter headerAdapter;
    private HomeBillAdapter billAdapter;
    private FooterAdapter footerAdapter;

    private String currentUserId;
    private boolean isFirstLoad = true;
    private boolean hasResumedOnce;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 处理系统栏缩进
        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        initUserContext();
        initViewModels();
        setupRecyclerView();
        setupSwipeRefresh();
        setupClickListeners();
        observeData();
        loadUserAvatar();

        // 首次进入触发静默同步
        billViewModel.checkAndAutoSync();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 检查用户登录状态切换
        checkUserSession();
        // 从编辑页返回时立即创建新的分页代际，确保首页显示最新的本地账单。
        if (hasResumedOnce && billViewModel != null) {
            billViewModel.refreshData();
        }
        hasResumedOnce = true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // 当从其他 Tab 切换回首页时，尝试静默同步
            if (billViewModel != null) {
                billViewModel.checkAndAutoSync();
            }
            loadUserAvatar();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.rvBills.clearOnScrollListeners();
        }
        hasResumedOnce = false;
        binding = null;
    }

    private void initUserContext() {
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            currentUserId = user.getObjectId();
        }
    }

    private void initViewModels() {
        // BillViewModel 使用 activity 作用域，保证在 Tab 切换时状态不丢失
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);
    }

    private void setupRecyclerView() {
        headerAdapter = new HeaderAdapter();
        headerAdapter.setRefreshClickListener(() -> {
            showSnackbar("正在强制同步云端数据...");
            billViewModel.forceSyncFromCloud();
        });

        billAdapter = new HomeBillAdapter(requireContext(), new BillAdapter.OnBillClickListener() {
            @Override
            public void onBillClick(long localId, String objectId, View itemView) {
                openBillDetail(localId, objectId);
            }
            @Override
            public void onPhotoClick(String imageUrl, int position) {
                openImageViewer(imageUrl);
            }
            @Override
            public void onBillDelete(BillUiModel bill) {
                handleBillDelete(bill);
            }
            @Override
            public void onBillEdit(BillUiModel billUi) {
                if (billUi == null || billUi.originalBill == null) return;
                com.example.my_project1.data.model.bill.Bill bill = billUi.originalBill;
                Intent intent = new Intent(getActivity(), com.example.my_project1.ui.activity.AddBillActivity.class);
                intent.putExtra("mode", "edit");
                
                // 1. ID 处理
                if (bill.getObjectId() != null && !bill.getObjectId().isEmpty()) {
                    intent.putExtra("bill_id", bill.getObjectId());
                } else {
                    intent.putExtra("bill_local_id", bill.getId());
                }
                
                // 2. 基础字段
                intent.putExtra("bill_type", bill.getType());
                intent.putExtra("bill_amount", bill.getAmount());
                intent.putExtra("category_id", bill.getCategoryId());
                intent.putExtra("category_name", bill.getCategoryName());
                intent.putExtra("category_icon", bill.getCategoryIconUrl());
                intent.putExtra("category_icon_bg_color", bill.getCategoryIconBackgroundColor());
                
                // 3. 账户字段
                intent.putExtra("account_id", bill.getAccountId());
                intent.putExtra("local_account_id", bill.getLocalAccountId());
                intent.putExtra("to_account_id", bill.getToAccountId());
                intent.putExtra("to_local_account_id", bill.getToLocalAccountId());
                
                // 4. 其他字段
                intent.putExtra("book_id", bill.getBookId());
                if (bill.getBillTime() != null) {
                    intent.putExtra("bill_time", bill.getBillTime().getTime());
                }
                intent.putExtra("remark", bill.getRemark());
                intent.putExtra("location", bill.getLocation());
                intent.putExtra("exclude_budget", bill.isExcludeBudget());
                
                if (bill.getImageUrls() != null && !bill.getImageUrls().isEmpty()) {
                    intent.putStringArrayListExtra("image_urls", new java.util.ArrayList<>(bill.getImageUrls()));
                }

                startActivity(intent);
            }
            @Override
            public void onBillRefund(BillUiModel bill) {
                showSnackbar("退款申请已提交");
            }
        });

        // 监听 Paging 加载状态以控制 UI 显示
        billAdapter.addLoadStateListener(loadStates -> {
            LoadState refreshState = loadStates.getRefresh();
            if (refreshState instanceof LoadState.NotLoading) {
                if (isFirstLoad) {
                    hideLoadingView();
                    isFirstLoad = false;
                }
                if (binding != null) binding.swipeRefreshLayout.setRefreshing(false);
            } else if (refreshState instanceof LoadState.Error) {
                hideLoadingView();
                if (binding != null) binding.swipeRefreshLayout.setRefreshing(false);
                Throwable error = ((LoadState.Error) refreshState).getError();
                Log.e(TAG, "加载出错: " + error.getMessage());
                showSnackbar("加载失败");
            }
            return null;
        });

        footerAdapter = new FooterAdapter();
        footerAdapter.setRetryClickListener(() -> billAdapter.retry());

        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true) 
                .build();
        ConcatAdapter concatAdapter = new ConcatAdapter(config,
                headerAdapter, billAdapter.withLoadStateFooter(footerAdapter));

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setInitialPrefetchItemCount(5); // 适度预加载

        binding.rvBills.setLayoutManager(layoutManager);
        binding.rvBills.setAdapter(concatAdapter);
        binding.rvBills.setHasFixedSize(false); 
        binding.rvBills.setItemViewCacheSize(10); // 增加缓存池，平衡内存与流畅度
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.accent_color);
        binding.swipeRefreshLayout.setDistanceToTriggerSync(350);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> billViewModel.refresh());
    }

    private void setupClickListeners() {
        binding.ivSearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.ivChart.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), BillStatisticsActivity.class);
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        
        binding.ivAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void observeData() {
        // 1. 分页账单数据
        billViewModel.homeBillPagingData.observe(getViewLifecycleOwner(), pagingData -> {
            if (pagingData != null) {
                billAdapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
                if (billAdapter.getItemCount() > 0) hideLoadingView();
            }
        });

        // 2. 统计卡片数据
        billViewModel.headerData.observe(getViewLifecycleOwner(), header -> {
            if (header != null) headerAdapter.setHeader(header);
        });

        // 3. 同步状态反馈
        billViewModel.syncState.observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state.isSuccess()) {
                if (binding != null) binding.swipeRefreshLayout.setRefreshing(false);
                showSnackbar("同步成功");
            } else if (state.isError()) {
                if (binding != null) binding.swipeRefreshLayout.setRefreshing(false);
                showSnackbar("同步失败");
            }
        });
    }

    private void loadUserAvatar() {
        if (currentUserId == null) {
            if (binding != null) binding.ivAvatar.setImageResource(R.drawable.ic);
            return;
        }
        userViewModel.getUserProfile(currentUserId).observe(getViewLifecycleOwner(), this::updateUserAvatar);
    }

    private void updateUserAvatar(UserProfile profile) {
        if (profile == null || binding == null) return;
        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
            ImageLoaderUtils.loadAvatar(requireContext(), profile.getAvatarUrl(), binding.ivAvatar);
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic);
        }
    }

    private void checkUserSession() {
        BmobUser user = BmobUser.getCurrentUser();
        String newId = user != null ? user.getObjectId() : null;
        if (!isUserIdEqual(currentUserId, newId)) {
            currentUserId = newId;
            billViewModel.checkUserSwitch();
            loadUserAvatar();
        }
    }

    private boolean isUserIdEqual(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private void handleBillDelete(BillUiModel billUiModel) {
        if (billUiModel == null) return;
        new com.example.my_project1.ui.dialog.ConfirmDialog(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除这笔账单吗？删除后将无法恢复。")
                .setConfirmText("删除")
                .setConfirmListener(() -> {
                    AppExecutors.get().diskIO().execute(() -> {
                        Bill bill = billViewModel.saveBillLocal(billUiModel.localId);
                        if (bill != null) {
                            AppExecutors.get().mainThread().execute(() -> billViewModel.deleteBill(bill));
                        }
                    });
                })
                .show();
    }

    private void openBillDetail(long localId, String objectId) {
        Intent intent = new Intent(getActivity(), BillDetailActivity.class);
        if (objectId != null && !objectId.isEmpty()) {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, objectId);
        } else {
            intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, localId);
        }
        startActivity(intent);
        if (getActivity() != null)
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openImageViewer(String imageUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(imageUrl), "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            showSnackbar("无法打开图片");
        }
    }

    private void showSnackbar(String message) {
        if (binding != null && isAdded()) {
            View fab = getActivity() != null ? getActivity().findViewById(R.id.fab) : null;
            Snackbar snackbar = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT);
            if (fab != null) snackbar.setAnchorView(fab);
            snackbar.show();
        }
    }

    private void hideLoadingView() {
        if (binding == null || binding.loadingLayout.getVisibility() == View.GONE) return;
        binding.loadingLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> {
                    if (binding != null) binding.loadingLayout.setVisibility(View.GONE);
                })
                .start();
    }

    public static HomeFragment newInstance() { return new HomeFragment(); }
}
