package com.example.my_project1.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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

        initSystemBars();

        binding.btnBack.setOnClickListener(v -> finish());

        setupRecyclerView();
    }

    private void initSystemBars() {
        int bgColor = Color.parseColor("#F4F4F4");
        getWindow().setStatusBarColor(bgColor);
        getWindow().setNavigationBarColor(bgColor);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    private void setupRecyclerView() {
        String groupId = getIntent().getStringExtra("groupId");

        List<AccountGroupSection> sections = new ArrayList<>();

        // 资金账户
        List<AccountType> fundingTypes = new ArrayList<>();
        fundingTypes.add(new AccountType("现金钱包", R.drawable.ic_wallet, "资金账户"));
        fundingTypes.add(new AccountType("储蓄卡", R.drawable.ic_debit_card, "资金账户"));
        fundingTypes.add(new AccountType("支付宝", R.drawable.ic_wallet, "资金账户"));
        fundingTypes.add(new AccountType("微信钱包", R.drawable.ic_wechat, "资金账户"));
        sections.add(new AccountGroupSection("资金账户", fundingTypes));

        // 信用账户
        List<AccountType> creditTypes = new ArrayList<>();
        creditTypes.add(new AccountType("信用卡", R.drawable.ic_debit_card, "信用账户"));
        creditTypes.add(new AccountType("京东白条", R.drawable.ic_wallet, "信用账户"));
        sections.add(new AccountGroupSection("信用账户", creditTypes));

        // 充值账户
        List<AccountType> topupTypes = new ArrayList<>();
        topupTypes.add(new AccountType("公交卡", R.drawable.ic_wallet, "充值账户"));
        topupTypes.add(new AccountType("饭卡", R.drawable.ic_wallet, "充值账户"));
        topupTypes.add(new AccountType("会员卡", R.drawable.ic_wallet, "充值账户"));
        sections.add(new AccountGroupSection("充值账户", topupTypes));

        AccountGroupSectionAdapter adapter = new AccountGroupSectionAdapter(sections, type -> {
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

    public static class AccountGroupSection {
        String title;
        List<AccountType> items;

        public AccountGroupSection(String title, List<AccountType> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static class AccountGroupSectionAdapter extends RecyclerView.Adapter<AccountGroupSectionAdapter.ViewHolder> {
        private final List<AccountGroupSection> sections;
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(AccountType type);
        }

        public AccountGroupSectionAdapter(List<AccountGroupSection> sections, OnItemClickListener listener) {
            this.sections = sections;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_type_group_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AccountGroupSection section = sections.get(position);
            holder.tvGroupTitle.setText(section.title);
            holder.containerItems.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (int i = 0; i < section.items.size(); i++) {
                AccountType type = section.items.get(i);
                View itemView = inflater.inflate(R.layout.item_account_type, holder.containerItems, false);

                TextView tvName = itemView.findViewById(R.id.tv_name);
                ImageView ivIcon = itemView.findViewById(R.id.iv_icon);

                tvName.setText(type.name);
                ivIcon.setImageResource(type.iconRes);
                itemView.setOnClickListener(v -> listener.onItemClick(type));

                holder.containerItems.addView(itemView);

                // 如果不是最后一条，添加分割线
                if (i < section.items.size() - 1) {
                    View divider = new View(holder.itemView.getContext());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    lp.setMargins(16, 0, 16, 0);
                    divider.setLayoutParams(lp);
                    divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
                    holder.containerItems.addView(divider);
                }
            }
        }

        @Override
        public int getItemCount() {
            return sections.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvGroupTitle;
            LinearLayout containerItems;

            ViewHolder(View itemView) {
                super(itemView);
                tvGroupTitle = itemView.findViewById(R.id.tv_group_title);
                containerItems = itemView.findViewById(R.id.container_items);
            }
        }
    }
}
