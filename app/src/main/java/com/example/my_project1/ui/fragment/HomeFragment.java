package com.example.my_project1.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * HomeFragment (重构版)
 * -------------------------------------------------------
 * ✅ 布局改为 SwipeRefreshLayout + RecyclerView，彻底去除 NestedScrollView
 * ✅ ConcatAdapter 组合三段 Adapter：Header / Bill / Footer
 * ✅ Paging 3 自动按需加载账单页
 * ✅ 下拉刷新：重置页码并重新拉取
 * ✅ onBindViewHolder 只做 setText，计算全在 ViewModel 后台线程完成
 */
public class HomeFragment extends Fragment {

    private static final String TAG            = "HomeFragment";
    // ── 视图绑定 ─────────────────────────────────────
    private FragmentHomeBinding binding;

    // ── ViewModel ────────────────────────────────────
    private BillViewModel        billViewModel;
    private UserProfileViewModel userViewModel;

    // ── Adapter 三件套 ────────────────────────────────
    private HeaderAdapter     headerAdapter;
    private HomeBillAdapter   billAdapter;
    private FooterAdapter     footerAdapter;

    // ── 用户 ─────────────────────────────────────────
    private String  currentUserId;
    private boolean isDataObserved = false;

    // ════════════════════════════════════════════════════
    //  生命周期
    // ════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (v, insets) -> {

            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());

            v.setPadding(0, bars.top, 0, 0);

            return insets;
        });

        initCurrentUser();

        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);

        setupRecyclerView();
        setupSwipeRefresh();
        setupClickListeners();
        loadUserAvatar();

        // 每个新 ViewLifecycleOwner 都需要重新订阅；旧观察者会随旧 View 自动移除。
        observeData();
        isDataObserved = true;

        billViewModel.checkAndAutoSync();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkUserSwitch();
        loadUserAvatar();
        // 每次回到首页时，尝试静默同步
        if (billViewModel != null) {
            billViewModel.checkAndAutoSync();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // 当从其他 Tab 切换回首页时（MainActivity show/hide）
            Log.d(TAG, "首页可见，执行静默同步检查");
            if (billViewModel != null) {
                billViewModel.checkAndAutoSync();
            }
            loadUserAvatar();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 避免 RecyclerView 滚动监听在 View 销毁后回调
        if (binding != null) binding.rvBills.clearOnScrollListeners();
        binding = null;
        isDataObserved = false;
    }

    // ════════════════════════════════════════════════════
    //  RecyclerView 初始化
    // ════════════════════════════════════════════════════

    private void setupRecyclerView() {
        // ── 三段 Adapter ─────────────────────────────
        headerAdapter = new HeaderAdapter();
        headerAdapter.setRefreshClickListener(() -> {
            showSnackbar("正在同步...");
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
            public void onBillEdit(BillUiModel bill) {
                if (bill == null) return;
                Intent intent = new Intent(getActivity(), com.example.my_project1.ui.activity.AddBillActivity.class);
                intent.putExtra("editBillLocalId", bill.localId);
                startActivity(intent);
            }
            @Override
            public void onBillRefund(BillUiModel bill) {
                showSnackbar("已触发退款申请");
            }
        });

        footerAdapter = new FooterAdapter();
        footerAdapter.setRetryClickListener(() -> billAdapter.retry());

        // ── ConcatAdapter 组合 ────────────────────────
        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)   // 允许三个子 Adapter 共用 ViewHolder 类型，提升复用率
                .build();
        ConcatAdapter concatAdapter = new ConcatAdapter(config,
                headerAdapter, billAdapter.withLoadStateFooter(footerAdapter));


        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setInitialPrefetchItemCount(10);

        binding.rvBills.setLayoutManager(layoutManager);
        binding.rvBills.setAdapter(concatAdapter);
        binding.rvBills.setHasFixedSize(false); // Header 高度固定，但Footer变化，设为 false
        binding.rvBills.setItemViewCacheSize(30);

    }

    // ════════════════════════════════════════════════════
    //  下拉刷新
    // ════════════════════════════════════════════════════

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.accent_color);
        // Increase distance to trigger sync to prevent accidental refreshes
        binding.swipeRefreshLayout.setDistanceToTriggerSync(350);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            // 重置页码 + 清除缓存 + 重新请求
            billViewModel.refresh();
        });
    }

    // ════════════════════════════════════════════════════
    //  点击事件
    // ════════════════════════════════════════════════════

    private void setupClickListeners() {
        binding.ivSearch.setOnClickListener(v -> {
            if (getActivity() == null) return;
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.ivChart.setOnClickListener(v -> {
            if (getActivity() == null) return;
            Intent intent = new Intent(getActivity(), BillStatisticsActivity.class);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    // ════════════════════════════════════════════════════
    //  数据观察（核心：UI 只做赋值）
    // ════════════════════════════════════════════════════

    private void observeData() {
        // ── 1. 首页账单（Paging 3 根据滚动位置自动追加）──
        billViewModel.homeBillPagingData.observe(getViewLifecycleOwner(), pagingData -> {
            if (pagingData != null) {
                billAdapter.submitData(getViewLifecycleOwner().getLifecycle(), pagingData);
            }
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        // ── 2. 顶部统计卡片 ────────────────────────────
        billViewModel.headerData.observe(getViewLifecycleOwner(), header -> {
            if (header == null) return;
            headerAdapter.setHeader(header);
        });

        // ── 3. 同步状态 ────────────────────────────────
        billViewModel.syncState.observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state.isSuccess() && state.data != null) {
                binding.swipeRefreshLayout.setRefreshing(false);
                String msg = String.format("同步完成: 新增%d, 更新%d, 删除%d",
                        state.data.newCount, state.data.updateCount, state.data.deleteCount);
                showSnackbar(msg);
            } else if (state.isError()) {
                binding.swipeRefreshLayout.setRefreshing(false);
                showSnackbar("同步失败: " + state.message);
            }
        });

        // ── 4. Toast 消息 ──────────────────────────────

    }

    // ════════════════════════════════════════════════════
    //  用户相关
    // ════════════════════════════════════════════════════

    private void initCurrentUser() {
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) currentUserId = user.getObjectId();
    }

    private void checkUserSwitch() {
        String newId = null;
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) newId = user.getObjectId();

        if (!isUserIdEqual(currentUserId, newId)) {
            currentUserId = newId;
            if (billViewModel != null) billViewModel.checkUserSwitch();
            loadUserAvatar();
        }
    }

    private boolean isUserIdEqual(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void loadUserAvatar() {
        if (currentUserId == null) {
            if (binding != null) binding.ivAvatar.setImageResource(R.drawable.ic);
            return;
        }
        userViewModel.getUserProfile(currentUserId)
                .observe(getViewLifecycleOwner(), this::updateUserAvatar);
    }

    private void updateUserAvatar(UserProfile profile) {
        if (profile == null || binding == null) return;
        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
            ImageLoaderUtils.loadAvatar(requireContext(), profile.getAvatarUrl(), binding.ivAvatar);
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic);
        }
        binding.ivAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    // ════════════════════════════════════════════════════
    //  页面跳转
    // ════════════════════════════════════════════════════

    private void handleBillDelete(BillUiModel billUiModel) {
        if (billUiModel == null) return;
        
        AppExecutors.get().diskIO().execute(() -> {
            Bill bill = billViewModel.saveBillLocal(billUiModel.localId);
            if (bill != null) {
                AppExecutors.get().mainThread().execute(() -> {
                    billViewModel.deleteBill(bill);
                });
            }
        });
    }

    private void openBillDetail(long localId, String objectId) {
        try {
            Intent intent = new Intent(getActivity(), BillDetailActivity.class);
            if (objectId != null && !objectId.isEmpty()) {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_ID, objectId);
            } else {
                intent.putExtra(BillDetailActivity.EXTRA_BILL_LOCAL_ID, localId);
            }
            startActivity(intent);
            if (getActivity() != null)
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } catch (Exception e) {
            Log.e(TAG, "打开详情页失败", e);
            showSnackbar("打开详情页失败");
        }
    }

    private void openImageViewer(String imageUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(imageUrl), "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                showSnackbar("没有可用的图片查看器");
            }
        } catch (Exception e) {
            showSnackbar("打开图片失败");
        }
    }

    private void showSnackbar(String message) {
        if (binding != null && isAdded() && getActivity() != null) {
            // 获取MainActivity的根视图和FAB按钮
            View rootView = getActivity().findViewById(R.id.root);
            View fabButton = getActivity().findViewById(R.id.fab);
            
            Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT);
            
            // 设置anchor view为FAB按钮，让Snackbar显示在FAB上方
            if (fabButton != null) {
                snackbar.setAnchorView(fabButton);
            }
            
            snackbar.show();
        }
    }

    public static HomeFragment newInstance() { return new HomeFragment(); }
}
