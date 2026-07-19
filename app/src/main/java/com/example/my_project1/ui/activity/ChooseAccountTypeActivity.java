package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.databinding.ActivityChooseAccountTypeBinding;

import java.util.ArrayList;
import java.util.List;

public class ChooseAccountTypeActivity extends AppCompatActivity {

    private ActivityChooseAccountTypeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChooseAccountTypeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        setupRecyclerView();
    }

    private void setupRecyclerView() {
        String groupId = getIntent().getStringExtra("groupId");
        List<Object> items = new ArrayList<>();

        // 资金账户
        items.add("资金账户");
        items.add(new AccountType("现金钱包", R.drawable.ic_wallet, "资金账户"));
        items.add(new AccountType("储蓄卡", R.drawable.ic_debit_card, "资金账户"));
        items.add(new AccountType("支付宝", R.drawable.ic_wallet, "资金账户")); // 保持 ic_wallet 如果没有 ic_alipay
        items.add(new AccountType("微信钱包", R.drawable.ic_wechat, "资金账户"));

        // 信用账户
        items.add("信用账户");
        items.add(new AccountType("信用卡", R.drawable.ic_debit_card, "信用账户"));
        items.add(new AccountType("京东白条", R.drawable.ic_wallet, "信用账户"));

        // 充值账户
        items.add("充值账户");
        items.add(new AccountType("公交卡", R.drawable.ic_wallet, "充值账户"));
        items.add(new AccountType("饭卡", R.drawable.ic_wallet, "充值账户"));
        items.add(new AccountType("会员卡", R.drawable.ic_wallet, "充值账户"));

        AccountTypeAdapter adapter = new AccountTypeAdapter(items, type -> {
            Intent intent = new Intent(this, AddAccountActivity.class);
            intent.putExtra("accountName", type.name);
            intent.putExtra("accountIconRes", type.iconRes);
            intent.putExtra("accountCategory", type.category);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
        });

        binding.rvAccountTypes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAccountTypes.setAdapter(adapter);
    }

    public static class AccountType {
        String name;
        int iconRes;
        String category;

        public AccountType(String name, int iconRes, String category) {
            this.name = name;
            this.iconRes = iconRes;
            this.category = category;
        }
    }

    private static class AccountTypeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<Object> items;
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(AccountType type);
        }

        public AccountTypeAdapter(List<Object> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
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
                AccountType type = (AccountType) items.get(position);
                ItemViewHolder itemHolder = (ItemViewHolder) holder;
                itemHolder.tvName.setText(type.name);
                itemHolder.ivIcon.setImageResource(type.iconRes);
                itemHolder.itemView.setOnClickListener(v -> listener.onItemClick(type));
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
