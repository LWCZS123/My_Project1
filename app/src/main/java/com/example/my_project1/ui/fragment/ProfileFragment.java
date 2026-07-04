package com.example.my_project1.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.databinding.FragmentProfileBinding;
import com.example.my_project1.ui.activity.BudgetActivity;
import com.example.my_project1.ui.activity.ChangePasswordActivity;
import com.example.my_project1.ui.activity.EditProfileActivity;
import com.example.my_project1.ui.activity.IconMarketActivity;
import com.example.my_project1.ui.activity.IconSelectionActivity;
import com.example.my_project1.ui.activity.SavingsOverviewActivity;
import com.example.my_project1.ui.viewmodel.billvm.BillViewModel;
import com.example.my_project1.ui.viewmodel.user.UserProfileViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

/**
 * ProfileFragment - 个人信息界面
 * -------------------------------------------------------
 * 与 BillRepository 保持一致的代码风格
 * 支持离线编辑，自动同步
 * 图片上传不显示进度，只显示"上传中"和"上传成功/失败"
 * 账单数量和记账天数通过观察 BillViewModel 的统计LiveData实时更新
 */
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String OSS_PUBLIC_BASE_URL = "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/";

    private FragmentProfileBinding binding;
    private UserProfileViewModel viewModel;
    // 用于获取账单统计数据
    private BillViewModel billViewModel;
    private String currentUserId;

    // ActivityResult Launchers
    private ActivityResultLauncher<Intent> pickAvatarLauncher;
    private ActivityResultLauncher<Intent> pickBackgroundLauncher;
    private ActivityResultLauncher<Intent> editProfileLauncher;

    // ==================== 生命周期 ====================

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLaunchers();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);



        initViewModel();
        initViews();
        observeViewModel();
        loadUserProfile();
    }

    // ==================== 初始化 ====================

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);
        // BillViewModel 与其他使用它的 Fragment 共享同一个实例（Activity作用域）
        billViewModel = new ViewModelProvider(requireActivity()).get(BillViewModel.class);

        BmobUser user = BmobUser.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "未登录", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = user.getObjectId();
    }

    private void initLaunchers() {
        // 头像选择
        pickAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            viewModel.uploadAndUpdateAvatar(uri, currentUserId);
                        }
                    }
                });

        // 背景图选择
        pickBackgroundLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            viewModel.uploadAndUpdateBackground(uri, currentUserId);
                        }
                    }
                });

        // 编辑资料
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // 编辑完成后刷新（Worker会自动同步）
                        loadUserProfile();
                    }
                });
    }

    private void initViews() {
        // 头像点击
        binding.ivAvatar.setOnClickListener(v -> openImagePicker(pickAvatarLauncher));

        // 背景图点击
        binding.ivTheme.setOnClickListener(v -> openImagePicker(pickBackgroundLauncher));

        // 设置按钮
        binding.ivSettings.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
        binding.llAppSettings.setOnClickListener(v -> changePasswordActivity());
        binding.llBudget.setOnClickListener(v -> budgetActivity());
        binding.llCategoryManage.setOnClickListener(v -> iconMarketActivity());
        binding.llMyWish.setOnClickListener(v -> savingsOverviewActivity());
        binding.llFinancialTips.setOnClickListener(v-> iconCheckAcitvity());

    }

    private void iconCheckAcitvity() {
        startActivity(new Intent(getActivity(), IconSelectionActivity.class));
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

    }

    private void savingsOverviewActivity() {
        startActivity(new Intent(getActivity(), SavingsOverviewActivity.class));
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

    }

    private void iconMarketActivity() {
        startActivity(new Intent(getActivity(), IconMarketActivity.class));
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

    }

    private void budgetActivity() {
        startActivity(new Intent(getActivity(), BudgetActivity.class));
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void changePasswordActivity() {
        startActivity(new Intent(getActivity(), ChangePasswordActivity.class));
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // ==================== 数据加载 ====================

    /**
     * 加载用户信息
     * LiveData会自动触发后台同步
     */
    private void loadUserProfile() {
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "用户ID为空", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.getUserProfile(currentUserId)
                .observe(getViewLifecycleOwner(), profile -> {
                    if (profile != null) {
                        updateUI(profile);
                    } else {
                        // 本地无数据时创建默认配置
                        createDefaultProfile();
                    }
                });
    }

    /**
     * 更新UI
     */
    private void updateUI(UserProfile profile) {
        // 用户名
        String username = profile.getUsername();
        binding.tvUsername.setText(
                username != null && !username.isEmpty() ? username : "未设置用户名"
        );

        // 头像
        String avatarUrl = profile.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            String fullAvatarUrl = avatarUrl.startsWith("http")
                    ? avatarUrl
                    : OSS_PUBLIC_BASE_URL + avatarUrl;

            ImageLoaderUtils.loadThumbnail(
                    requireContext(),
                    fullAvatarUrl,
                    binding.ivAvatar
            );
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }


        Integer gender = profile.getGender();
        if(gender != null){
            if(gender == 0){
                binding.tvGender.setText("未设置");
            }else if(gender == 1){
                binding.tvGender.setText("男");
            }else{
                binding.tvGender.setText("女");
            }
        }

        // 背景图
        String backgroundUrl = profile.getBackgroundUrl();
        if (backgroundUrl != null && !backgroundUrl.isEmpty()) {
            String fullBackgroundUrl = backgroundUrl.startsWith("http")
                    ? backgroundUrl
                    : OSS_PUBLIC_BASE_URL + backgroundUrl;

            ImageLoaderUtils.load(
                    requireContext(),
                    fullBackgroundUrl,
                    binding.ivBackground
            );
        }
    }

    /**
     * 创建默认用户配置
     */
    private void createDefaultProfile() {
        UserProfile profile = new UserProfile(currentUserId);
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            profile.setUsername(user.getUsername());
            profile.setEmail(user.getEmail());
        }
        viewModel.updateUserProfile(profile);
    }

    // ==================== 状态观察 ====================

    private void observeViewModel() {
        // 头像上传状态
        viewModel.avatarUploadState.observe(getViewLifecycleOwner(), state -> {
            handleImageUploadState(state, "头像", binding.ivAvatar, viewModel::resetAvatarUploadState);
        });

        // 背景图上传状态
        viewModel.backgroundUploadState.observe(getViewLifecycleOwner(), state -> {
            handleImageUploadState(state, "背景图", binding.ivBackground, viewModel::resetBackgroundUploadState);
        });

        // 观察账单总数量，实时更新 tv_record_count
        billViewModel.billCount.observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                binding.tvRecordCount.setText(String.valueOf(count));
            }
        });

        // 观察记账天数，实时更新 tv_balance
        billViewModel.billDays.observe(getViewLifecycleOwner(), days -> {
            if (days != null) {
                binding.tvBalance.setText("已记账 " + days + " 天");
            }
        });
    }

    /**
     * 处理图片上传状态
     * 只显示"上传中"和"上传成功/失败"，不显示具体进度
     */
    private void handleImageUploadState(
            ApiResponse<String> state,
            String type,
            ImageView targetView,
            Runnable resetAction
    ) {
        if (state.isIdle()) {
            return;
        }

        if (state.isLoading()) {
            Toast.makeText(requireContext(), "正在上传" + type + "...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (state.isSuccess()) {
            String objectKey = state.data;
            if (objectKey != null) {
                String fullUrl = objectKey.startsWith("http")
                        ? objectKey
                        : OSS_PUBLIC_BASE_URL + objectKey;

                Toast.makeText(requireContext(), type + "更新成功", Toast.LENGTH_SHORT).show();

                // 立即更新UI
                ImageLoaderUtils.loadFresh(
                        requireContext(),
                        fullUrl,
                        targetView
                );
            }
            resetAction.run();
            return;
        }

        if (state.isError()) {
            Toast.makeText(
                    requireContext(),
                    type + "更新失败: " + state.message,
                    Toast.LENGTH_SHORT
            ).show();
            resetAction.run();
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 打开图片选择器
     */
    private void openImagePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );
        launcher.launch(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}