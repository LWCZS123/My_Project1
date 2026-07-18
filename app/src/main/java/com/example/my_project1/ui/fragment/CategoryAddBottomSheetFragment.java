package com.example.my_project1.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.my_project1.R;
import com.example.my_project1.utils.ImageLoaderUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import io.reactivex.annotations.NonNull;

public class CategoryAddBottomSheetFragment extends BottomSheetDialogFragment {

    private EditText etName;
    private SwitchCompat switchBudget;
    private Button btnConfirm;
    private ImageView ivIcon;
    private String selectedIconUrl;
    private TextView tv_category;
    private String title,categoryName,categoryIconUrl;


    public interface OnCategoryAddedListener {
        void onCategoryAdded(String name, boolean excludeBudget, String iconUrl);
    }

    private OnCategoryAddedListener listener;

    public void setOnCategoryAddedListener(OnCategoryAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category_add_bottom_sheet, container, false);

        etName = view.findViewById(R.id.et_category_name);
        switchBudget = view.findViewById(R.id.switch_budget);
        btnConfirm = view.findViewById(R.id.btn_confirm);
        ivIcon = view.findViewById(R.id.iv_icon);
        tv_category = view.findViewById(R.id.tv_category);


        Bundle args = getArguments();
        if (args != null){
            title  = args.getString("title","添加分类");
            categoryName = args.getString("categoryName");
            categoryIconUrl = args.getString("iconUri");

        }
        tv_category.setText(title);
        etName.setText(categoryName);
        Object source = ImageLoaderUtils.getGlideSource(requireContext(),categoryIconUrl);
        selectedIconUrl = categoryIconUrl;

        Glide.with(requireContext())
                .load(source)
                .placeholder(R.drawable.ic_default_category)
                .error(R.drawable.ic_default_category)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本
                .skipMemoryCache(false)                   // 使用内存缓存
                .into(ivIcon);

        // 设置自定义 Switch 样式
        switchBudget.setTrackDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ios_switch_track));
        switchBudget.setThumbDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ios_switch_thumb));

        // 点击图标选择
        ivIcon.setOnClickListener(v -> {
            IconCategoryBottomSheet iconFragment = new IconCategoryBottomSheet();
            iconFragment.setOnIconSelectedListener(iconUrl -> {
                selectedIconUrl = iconUrl;
                ImageLoaderUtils.load(requireActivity(),selectedIconUrl,ivIcon);
            });
            iconFragment.show(getParentFragmentManager(), "icon_selector");

        });

        // 确认按钮
        btnConfirm.setOnClickListener(v -> {
             String name = etName.getText().toString().trim();
            boolean excludeBudget = switchBudget.isChecked();
            if (name.isEmpty()) {
                etName.setError("请输入分类名称");
                return;
            }
            if ( selectedIconUrl == null || selectedIconUrl.isEmpty()) {
                Toast.makeText(getActivity(), "请选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) {
                listener.onCategoryAdded(name, excludeBudget, selectedIconUrl);
            }
            dismiss();
        });

        return view;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 设置上圆角背景
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(),
                                R.drawable.bg_bottom_sheet1)
                );

                // 默认展开 BottomSheet
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });



        return bottomSheetDialog;
    }
}
