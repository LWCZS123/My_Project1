package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.ui.viewmodel.accountvm.AccountViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.bmob.v3.BmobUser;

public class MultiAccountSelectBottomSheet extends BottomSheetDialogFragment {

    private MultiAccountAdapter adapter;
    private OnAccountsSelectedListener listener;
    private Set<String> initialSelectedIds = new HashSet<>();

    public interface OnAccountsSelectedListener {
        void onSelected(List<Account> accounts);
    }

    public void setOnAccountsSelectedListener(OnAccountsSelectedListener listener) {
        this.listener = listener;
    }

    public void setInitialSelectedIds(List<String> ids) {
        if (ids != null) {
            this.initialSelectedIds = new HashSet<>(ids);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_multi_account_select, container, false);
        
        RecyclerView recyclerView = view.findViewById(R.id.rvAccountList);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MultiAccountAdapter();
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelected(adapter.getSelectedAccounts());
            }
            dismiss();
        });

        AccountViewModel viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        BmobUser user = BmobUser.getCurrentUser();
        if (user != null) {
            viewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
                if (accounts != null) {
                    adapter.setData(accounts, initialSelectedIds);
                }
            });
        }

        return view;
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    static class MultiAccountAdapter extends RecyclerView.Adapter<MultiAccountAdapter.VH> {
        private final List<Account> accounts = new ArrayList<>();
        private final Set<String> selectedIds = new HashSet<>();

        void setData(List<Account> list, Set<String> initial) {
            accounts.clear();
            accounts.addAll(list);
            selectedIds.clear();
            selectedIds.addAll(initial);
            notifyDataSetChanged();
        }

        List<Account> getSelectedAccounts() {
            List<Account> selected = new ArrayList<>();
            for (Account a : accounts) {
                if (selectedIds.contains(a.getObjectId())) {
                    selected.add(a);
                }
            }
            return selected;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_multi_account_select, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Account account = accounts.get(position);
            TextView tvName = holder.itemView.findViewById(R.id.tvAccountName);
            android.widget.ImageView ivIcon = holder.itemView.findViewById(R.id.ivAccountIcon);
            android.widget.CheckBox checkBox = holder.itemView.findViewById(R.id.checkBox);

            tvName.setText(account.getName());
            boolean isSelected = selectedIds.contains(account.getObjectId());
            checkBox.setChecked(isSelected);

            // 加载图标
            if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
                com.bumptech.glide.Glide.with(ivIcon.getContext())
                        .load(account.getIconUrl())
                        .placeholder(R.drawable.ic_wallet)
                        .into(ivIcon);
            } else {
                ivIcon.setImageResource(R.drawable.ic_wallet);
            }
            
            holder.itemView.setOnClickListener(v -> {
                if (selectedIds.contains(account.getObjectId())) {
                    selectedIds.remove(account.getObjectId());
                } else {
                    selectedIds.add(account.getObjectId());
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return accounts.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) { super(itemView); }
        }
    }
}
