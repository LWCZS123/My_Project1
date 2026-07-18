package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.databinding.FragmentAddAccountGroupBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import cn.bmob.v3.BmobUser;

public class AddAccountGroupFragment extends BottomSheetDialogFragment {

    private static final String TAG = "AddAccountGroup";
    private FragmentAddAccountGroupBinding binding;
    private AccountViewModel accountViewModel;

    public interface OnAccountGroupListener {
        void onGroupAdded(AccountGroup group);
        void onGroupUpdated(AccountGroup group);
    }

    private OnAccountGroupListener listener;

    public void setOnAccountGroupListener(OnAccountGroupListener listener) {
        this.listener = listener;
    }

    private String selectedIconUrl = null;
    private String groupId = null;
    private String userId = null; // 存储用户ID

    public static AddAccountGroupFragment newInstance() {
        return new AddAccountGroupFragment();
    }

    public static AddAccountGroupFragment newInstance(AccountGroup group) {
        AddAccountGroupFragment fragment = new AddAccountGroupFragment();
        Bundle args = new Bundle();
        args.putString("id", group.getObjectId());
        args.putString("name", group.getName());
        args.putString("iconUrl", group.getIconUrl());
        args.putString("userId", group.getUserId()); // 传递userId
        Log.d(TAG, "编辑模式 - GroupId: " + group.getObjectId());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAddAccountGroupBinding.inflate(inflater, container, false);

        accountViewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        // 获取当前用户ID
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            userId = user.getObjectId();
        }

        // 读取参数
        Bundle args = getArguments();
        if (args != null) {
            groupId = args.getString("id", null);
            String name = args.getString("name");
            String iconUrl = args.getString("iconUrl");
            String argUserId = args.getString("userId");
            if (argUserId != null) {
                userId = argUserId;
            }

            if (name != null) binding.etGroupName.setText(name);
            if (iconUrl != null) {
                selectedIconUrl = iconUrl;
                ImageLoaderUtils.load(requireActivity(), iconUrl, binding.imgGroupIcon);
            }

            binding.tvTitleGroup.setText("修改账户组");
            binding.btnCreateGroup.setText("保存修改");

            Log.d(TAG, "编辑模式初始化 - ID: " + groupId + ", Name: " + name);
        }

        initClick();
        return binding.getRoot();
    }

    private void initClick() {
        binding.cardGroupIcon.setOnClickListener(v -> {
            IconAccountBottomSheet iconSheet = new IconAccountBottomSheet();
            iconSheet.setOnIconSelectedListener(icon -> {
                selectedIconUrl = icon.getUrl();
                ImageLoaderUtils.load(requireActivity(), selectedIconUrl, binding.imgGroupIcon);
            });
            iconSheet.show(getParentFragmentManager(), "icon_group_selector");
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnCreateGroup.setOnClickListener(v -> submit());
    }

    private void submit() {
        String groupName = binding.etGroupName.getText().toString().trim();

        if (TextUtils.isEmpty(groupName)) {
            Toast.makeText(getActivity(), "请输入分组名称", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedIconUrl == null) {
            Toast.makeText(getActivity(), "请选择分组图标", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userId == null) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        // 判断是新增还是更新
        if (groupId == null) {
            insertGroup(groupName, selectedIconUrl, userId);
        } else {
            updateGroup(groupId, groupName, selectedIconUrl, userId);
        }
    }

    private void insertGroup(String name, String iconUrl, String userId) {
        AccountGroup group = new AccountGroup();
        group.setName(name);
        group.setIconUrl(iconUrl);
        group.setUserId(userId);

        Log.d(TAG, "准备插入账户组: " + name);

        accountViewModel.insertAccountGroup(group, (success, message) -> {
            if (success) {
                Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onGroupAdded(group);
                }
                dismiss();
            } else {
                Toast.makeText(requireContext(), message != null ? message : "添加失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateGroup(String id, String name, String iconUrl, String userId) {
        AccountGroup group = new AccountGroup();
        group.setObjectId(id);
        group.setName(name);
        group.setIconUrl(iconUrl);
        group.setUserId(userId);

        Log.d(TAG, "准备更新账户组 - ID: " + id + ", Name: " + name);

        accountViewModel.updateAccountGroup(group, (success, message) -> {
            if (success) {
                Toast.makeText(requireContext(), "修改成功", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onGroupUpdated(group);
                }
                dismiss();
            } else {
                Toast.makeText(requireContext(), message != null ? message : "修改失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "更新失败: " + message);
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_bottom_sheet1));

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = (int) (getScreenHeight() * 0.75);
                bottomSheet.setLayoutParams(params);

                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
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
        binding = null;
    }
}