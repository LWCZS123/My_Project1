package com.example.my_project1.ui.activity;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.databinding.ActivityEditProfileBinding;
import com.example.my_project1.databinding.DialogEditTextBinding;
import com.example.my_project1.ui.viewmodel.user.UserProfileViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cn.bmob.v3.BmobUser;

/**
 * EditProfileActivity - 编辑用户信息页面 (优化版)
 * -------------------------------------------------------
 * ✅ 使用 MaterialCardView 统一布局
 * ✅ 优化控件间距和视觉效果
 * ✅ 删除背景图设置功能
 * ✅ 弹窗编辑各个字段
 * ✅ 数据本地存储后自动同步到Bmob云端
 * ✅ 再次进入页面时显示已保存的信息
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private static final String OSS_PUBLIC_BASE_URL = "https://xd-user-image.oss-cn-hangzhou.aliyuncs.com/";


    private ActivityEditProfileBinding binding;
    private UserProfileViewModel viewModel;

    private UserProfile currentProfile;
    private String currentUserId;

    // 临时存储编辑的数据
    private String editedUsername;
    private Integer editedGender;
    private Date editedBirthday;
    private String editedSchool;
    private String editedSignature;

    // 头像选择器
    private ActivityResultLauncher<Intent> pickAvatarLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        initViewModel();
        initViews();
        initImagePicker();
        loadUserProfile();
        observeViewModel();
    }

    // ==================== 初始化 ====================

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(UserProfileViewModel.class);

        // 获取当前用户ID
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();
        } else {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        // 返回按钮
        binding.ivBack.setOnClickListener(v -> finish());

        // 头像卡片 - 更换头像
        binding.cardAvatar.setOnClickListener(v -> pickAvatarImage());

        // 用户名卡片 - 弹窗编辑
        binding.cardUsername.setOnClickListener(v -> showEditUsernameDialog());

        // 性别卡片 - 选择器
        binding.cardGender.setOnClickListener(v -> showGenderDialog());

        // 生日卡片 - 日期选择器
        binding.cardBirthday.setOnClickListener(v -> showDatePicker());

        // 学校卡片 - 弹窗编辑
        binding.cardSchool.setOnClickListener(v -> showEditSchoolDialog());

        // 个性签名卡片 - 弹窗编辑
        binding.cardSignature.setOnClickListener(v -> showEditSignatureDialog());

        // 退出账号按钮 - 弹窗确认
        binding.btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    private void initImagePicker() {
        pickAvatarLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadAvatar(imageUri);
                        }
                    }
                }
        );
    }

    // ==================== 加载用户信息 ====================

    private void loadUserProfile() {
        if (currentUserId == null) {
            return;
        }

        viewModel.getUserProfile(currentUserId).observe(this, profile -> {
            if (profile != null) {
                currentProfile = profile;
                updateUI(profile);
            }
        });
    }

    /**
     * ✅ 更新UI - 显示已保存的用户信息
     */
    private void updateUI(UserProfile profile) {
        // 头像
        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
            ImageLoaderUtils.loadAvatar(this, profile.getAvatarUrl(), binding.ivAvatar);

        }

        // 用户名
        editedUsername = profile.getUsername();
        binding.tvUsernameValue.setText(
                editedUsername != null && !editedUsername.isEmpty()
                        ? editedUsername
                        : "未设置"
        );

        // 性别
        editedGender = profile.getGender();
        binding.tvGenderValue.setText(profile.getGenderText());

        // 生日
        editedBirthday = profile.getBirthday();
        if (editedBirthday != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            binding.tvBirthdayValue.setText(sdf.format(editedBirthday));
        } else {
            binding.tvBirthdayValue.setText("未设置");
        }

        // 学校
        editedSchool = profile.getSchool();
        binding.tvSchoolValue.setText(
                editedSchool != null && !editedSchool.isEmpty()
                        ? editedSchool
                        : "未设置"
        );

        // 个性签名
        editedSignature = profile.getSignature();
        binding.tvSignatureValue.setText(
                editedSignature != null && !editedSignature.isEmpty()
                        ? editedSignature
                        : "未设置"
        );
    }

    // ==================== 观察ViewModel ====================

    private void observeViewModel() {
        // 观察用户信息更新状态
        viewModel.updateState.observe(this, response -> {
            if (response == null) return;

            if (response.isLoading()) {
                showLoading();
            } else if (response.isSuccess()) {
                hideLoading();
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                // 不关闭页面,用户可以继续编辑
            } else if (response.isError()) {
                hideLoading();
                Toast.makeText(this, "保存失败: " + response.message,
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 观察头像上传状态
        viewModel.avatarUploadState.observe(this, response -> {
            if (response == null) return;

            if (response.isLoading()) {
                showLoading();
            } else if (response.isSuccess()) {
                hideLoading();
                Toast.makeText(this, "头像更新成功", Toast.LENGTH_SHORT).show();
                // 更新预览
                if (response.data != null) {
                    String fullAvatarUrl = response.data.startsWith("http")
                            ? response.data
                            : OSS_PUBLIC_BASE_URL + response.data;

                    Log.d(TAG,"data数据为"+response.data.trim());
                    ImageLoaderUtils.loadAvatar(this, fullAvatarUrl, binding.ivAvatar);
                }
                viewModel.resetAvatarUploadState();
            } else if (response.isError()) {
                hideLoading();
                Toast.makeText(this, "头像更新失败: " + response.message,
                        Toast.LENGTH_SHORT).show();
                viewModel.resetAvatarUploadState();
            }
        });

        // 观察退出登录状态
        viewModel.logoutState.observe(this, response -> {
            if (response == null) return;

            if (response.isLoading()) {
                showLoading();
            } else if (response.isSuccess()) {
                hideLoading();
                Toast.makeText(this, "退出成功", Toast.LENGTH_SHORT).show();
                // 跳转到登录页面
                navigateToLogin();
            } else if (response.isError()) {
                hideLoading();
                Toast.makeText(this, "退出失败: " + response.message,
                        Toast.LENGTH_SHORT).show();
                viewModel.resetLogoutState();
            }
        });
    }

    // ==================== 编辑对话框 ====================

    /**
     * ✅ 显示编辑用户名对话框
     */
    private void showEditUsernameDialog() {
        showEditTextDialog(
                "修改昵称",
                editedUsername,
                20,
                InputType.TYPE_CLASS_TEXT,
                newValue -> {
                    editedUsername = newValue;
                    binding.tvUsernameValue.setText(
                            newValue.isEmpty() ? "未设置" : newValue
                    );
                    saveProfileToLocal();
                }
        );
    }

    /**
     * ✅ 显示编辑学校对话框
     */
    private void showEditSchoolDialog() {
        showEditTextDialog(
                "修改学校",
                editedSchool,
                50,
                InputType.TYPE_CLASS_TEXT,
                newValue -> {
                    editedSchool = newValue;
                    binding.tvSchoolValue.setText(
                            newValue.isEmpty() ? "未设置" : newValue
                    );
                    saveProfileToLocal();
                }
        );
    }

    /**
     * ✅ 显示编辑个性签名对话框
     */
    private void showEditSignatureDialog() {
        showEditTextDialog(
                "修改个性签名",
                editedSignature,
                100,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                newValue -> {
                    editedSignature = newValue;
                    binding.tvSignatureValue.setText(
                            newValue.isEmpty() ? "未设置" : newValue
                    );
                    saveProfileToLocal();
                }
        );
    }

    /**
     * ✅ 通用文本编辑对话框
     */
    private void showEditTextDialog(
            String title,
            String currentValue,
            int maxLength,
            int inputType,
            OnTextEditedListener listener) {

        // 使用自定义布局
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_text, null);

        DialogEditTextBinding dialogBinding = DialogEditTextBinding.bind(dialogView);

        // 设置标题和提示
        dialogBinding.tvDialogTitle.setText(title);
        dialogBinding.etInput.setInputType(inputType);

        // 设置最大长度
        TextInputEditText editText = dialogBinding.etInput;
        editText.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(maxLength)
        });

        // 设置当前值
        if (currentValue != null && !currentValue.isEmpty()) {
            dialogBinding.etInput.setText(currentValue);
            dialogBinding.etInput.setSelection(currentValue.length());
        }

        // 创建对话框
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true);

        AlertDialog alertDialog = dialog.create();

        // 取消按钮
        dialogBinding.btnCancel.setOnClickListener(v -> alertDialog.dismiss());

        // 确定按钮
        dialogBinding.btnConfirm.setOnClickListener(v -> {
            String newValue = dialogBinding.etInput.getText().toString().trim();
            listener.onTextEdited(newValue);
            alertDialog.dismiss();
        });


        // 设置圆角背景
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded1);
        }


        alertDialog.show();
    }

    /**
     * ✅ 显示性别选择对话框
     */
    private void showGenderDialog() {
        String[] items = {"未设置", "男", "女"};
        int checkedItem = editedGender != null ? editedGender : 0;

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择性别")
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    editedGender = which;
                    binding.tvGenderValue.setText(items[which]);
                    saveProfileToLocal();
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * ✅ 显示日期选择器
     */
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (editedBirthday != null) {
            calendar.setTime(editedBirthday);
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(selectedYear, selectedMonth, selectedDay);
                    editedBirthday = selectedDate.getTime();

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd",
                            Locale.getDefault());
                    binding.tvBirthdayValue.setText(sdf.format(editedBirthday));

                    saveProfileToLocal();
                }, year, month, day);

        // 设置最大日期为今天
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    /**
     * ✅ 显示退出登录确认对话框
     * 🎨 带圆角效果
     */
    private void showLogoutDialog() {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("退出账号")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        AlertDialog alertDialog = builder.create();

        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded1);
            alertDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        alertDialog.show();

        // 重新设置确定按钮点击
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            // 先关闭dialog（触发退出动画）
            alertDialog.dismiss();

            // 延迟执行退出登录
            binding.getRoot().postDelayed(() -> {
                viewModel.logout();
            }, 200); // 动画时间
        });
    }
    // ==================== 头像上传 ====================

    private void pickAvatarImage() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickAvatarLauncher.launch(intent);
    }

    private void uploadAvatar(Uri imageUri) {
        if (currentUserId == null) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.uploadAndUpdateAvatar(imageUri, currentUserId);
    }

    // ==================== 保存逻辑 ====================

    /**
     * ✅ 保存到本地并自动同步到云端
     */
    private void saveProfileToLocal() {
        if (currentProfile == null) {
            currentProfile = new UserProfile(currentUserId);
        }

        // 更新用户信息
        currentProfile.setUsername(editedUsername);
        currentProfile.setGender(editedGender != null ? editedGender : 0);
        currentProfile.setBirthday(editedBirthday);
        currentProfile.setSchool(editedSchool);
        currentProfile.setSignature(editedSignature);

        // ✅ 提交更新 - Repository会自动同步到云端
        viewModel.updateUserProfile(currentProfile);
    }

    // ==================== UI工具方法 ====================

    private void showLoading() {
        binding.loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        binding.loadingOverlay.setVisibility(View.GONE);
    }

    /**
     * ✅ 跳转到登录页面
     * 🔑 清空任务栈，防止返回
     */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        // 清空任务栈，用户无法通过返回键回到之前的页面
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // ==================== 回调接口 ====================

    private interface OnTextEditedListener {
        void onTextEdited(String newValue);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void finish() {
        super.finish();
        // 退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

}