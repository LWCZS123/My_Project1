package com.example.my_project1.ui.popup;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.example.my_project1.databinding.LayoutAssetsMorePopupBinding;

public class AssetsMorePopup extends PopupWindow {

    private final LayoutAssetsMorePopupBinding binding;
    private OnOptionClickListener listener;

    public interface OnOptionClickListener {
        void onAssetGrouping();
        void onArchivedAccounts();
        void onHiddenAccounts();
    }

    public AssetsMorePopup(Context context, OnOptionClickListener listener) {
        super(context);
        this.listener = listener;
        binding = LayoutAssetsMorePopupBinding.inflate(LayoutInflater.from(context));
        setContentView(binding.getRoot());
        
        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        
        setFocusable(true);
        setOutsideTouchable(true);
        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setElevation(0f);
        }
        
        setupListeners();
    }

    private void setupListeners() {
        binding.btnAssetGroup.setOnClickListener(v -> {
            if (listener != null) listener.onAssetGrouping();
            dismiss();
        });
        
        binding.btnArchivedAccounts.setOnClickListener(v -> {
            if (listener != null) listener.onArchivedAccounts();
            dismiss();
        });

        binding.btnHiddenAccounts.setOnClickListener(v -> {
            if (listener != null) listener.onHiddenAccounts();
            dismiss();
        });
    }
}
