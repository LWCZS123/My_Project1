package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.my_project1.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 存钱周期选择底栏（ActionSheet 风格）
 */
public class SimpleSelectBottomSheet extends BottomSheetDialogFragment {

    private String title;
    private List<String> options;
    private OnItemSelectedListener listener;

    public interface OnItemSelectedListener {
        void onSelected(String item);
    }

    public static SimpleSelectBottomSheet newInstance(String title, ArrayList<String> options) {
        SimpleSelectBottomSheet fragment = new SimpleSelectBottomSheet();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putStringArrayList("options", options);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            title = getArguments().getString("title");
            options = getArguments().getStringArrayList("options");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_select_saving_cycle, container, false);
        
        LinearLayout llOptions = view.findViewById(R.id.ll_options);
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                String item = options.get(i);
                View itemView = inflater.inflate(R.layout.item_simple_list, llOptions, false);
                TextView tv = itemView.findViewById(R.id.tv_text);
                tv.setText(item);
                tv.setTextColor(android.graphics.Color.parseColor("#315CF5"));
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(17);
                
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onSelected(item);
                    dismiss();
                });
                
                llOptions.addView(itemView);
                
                if (i < options.size() - 1) {
                    View divider = new View(requireContext());
                    divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"));
                    llOptions.addView(divider);
                }
            }
        }

        view.findViewById(R.id.tv_cancel_btn).setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            View sheet = ((com.google.android.material.bottomsheet.BottomSheetDialog) getDialog()).findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogStyle;
    }
}
