package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemTransactionBinding;
import com.example.my_project1.databinding.ItemTransationDateHeaderBinding;
import com.example.my_project1.utils.AppExecutors;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.annotations.NonNull;

public class BillGroupedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "BillGroupedAdapter";
    private static final int VIEW_TYPE_DATE_HEADER = 1;
    private static final int VIEW_TYPE_BILL_ITEM = 2;

    private static final int DAY_START_HOUR = 6;
    private static final int NIGHT_START_HOUR = 18;

    private final Context mContext;
    private final List<Object> mItems = new ArrayList<>();
    private OnBillClickListener mListener;
    private Map<String, Account> mAccountMap = new HashMap<>();

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.mListener = listener;
        Log.d(TAG, "setOnBillClickListener: 监听器已注册");
    }

    public BillGroupedAdapter(Context context) {
        this.mContext = context;
        loadAccounts();
    }

    private void loadAccounts() {
        AppExecutors.get().diskIO().execute(() -> {
            List<Account> accounts = AppDatabase.getInstance(mContext).accountDao().getAllAccountsSync();
            Map<String, Account> map = new HashMap<>();
            if (accounts != null) {
                for (Account acc : accounts) {
                    map.put(acc.getObjectId(), acc);
                }
            }
            AppExecutors.get().mainThread().execute(() -> {
                this.mAccountMap = map;
                notifyDataSetChanged();
            });
        });
    }

    public void setBills(List<Bill> bills) {
        mItems.clear();
        if (bills != null && !bills.isEmpty()) {
            List<BillGroup> groups = groupBillsByDate(bills);
            for (BillGroup group : groups) {
                mItems.add(group);
                mItems.addAll(group.bills);
            }
        }
        Log.d(TAG, "setBills: 共 " + mItems.size() + " 项");
        notifyDataSetChanged();
    }

    private List<BillGroup> groupBillsByDate(List<Bill> bills) {
        List<BillGroup> groups = new ArrayList<>();
        Map<String, BillGroup> map = new LinkedHashMap<>();

        for (Bill bill : bills) {
            String key = getDateKey(bill.getBillTime());
            BillGroup group = map.get(key);
            if (group == null) {
                group = new BillGroup();
                group.dateKey = key;
                group.displayDate = formatDateDisplay(bill.getBillTime());
                group.bills = new ArrayList<>();
                map.put(key, group);
            }
            group.bills.add(bill);
            if (bill.getType() == 0) group.totalExpense += bill.getAmount();
            else if (bill.getType() == 1) group.totalIncome += bill.getAmount();
        }
        groups.addAll(map.values());
        return groups;
    }

    private String getDateKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
    }

    private String formatDateDisplay(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar bill = Calendar.getInstance();
        bill.setTime(date);
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        bill.set(Calendar.HOUR_OF_DAY, 0); bill.set(Calendar.MINUTE, 0); bill.set(Calendar.SECOND, 0); bill.set(Calendar.MILLISECOND, 0);
        long days = (today.getTimeInMillis() - bill.getTimeInMillis()) / (1000*60*60*24);
        if (days == 0) return "今天";
        else if (days == 1) return "昨天";
        else return new SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault()).format(date);
    }

    private boolean isDaytime(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= DAY_START_HOUR && h < NIGHT_START_HOUR;
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof BillGroup ? VIEW_TYPE_DATE_HEADER : VIEW_TYPE_BILL_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DATE_HEADER) {
            ItemTransationDateHeaderBinding bind = ItemTransationDateHeaderBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new HeaderVH(bind);
        } else {
            ItemTransactionBinding bind = ItemTransactionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new BillVH(bind);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = mItems.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind((BillGroup) obj);
        } else if (holder instanceof BillVH) {
            ((BillVH) holder).bind((Bill) obj);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    // ============== 日期头部 ==============
    static class HeaderVH extends RecyclerView.ViewHolder {
        private final ItemTransationDateHeaderBinding bind;
        public HeaderVH(ItemTransationDateHeaderBinding bind) {
            super(bind.getRoot());
            this.bind = bind;
        }
        void bind(BillGroup group) {
            bind.tvDate.setText(group.displayDate);
            DecimalFormat df = new DecimalFormat("#,##0.00");
            StringBuilder sb = new StringBuilder();
            if (group.totalExpense > 0) sb.append("支出 ¥").append(df.format(group.totalExpense));
            if (group.totalIncome > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append("收入 ¥").append(df.format(group.totalIncome));
            }
            if (sb.length() > 0) {
                bind.tvSummary.setText(sb);
                bind.tvSummary.setVisibility(View.VISIBLE);
            } else {
                bind.tvSummary.setVisibility(View.GONE);
            }
        }
    }

    // ============== 账单Item（修复点击核心）==============
    class BillVH extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding bind;
        private Bill mBill;

        public BillVH(ItemTransactionBinding bind) {
            super(bind.getRoot());
            this.bind = bind;

            // 强制不被父布局拦截
            itemView.setClickable(true);
            itemView.setFocusable(true);

            itemView.setOnClickListener(v -> {
                Log.d(TAG, "onClick: 点击账单 id=" + (mBill != null ? mBill.getId() : "null"));
                if (mBill != null && mListener != null) {
                    mListener.onBillClick(mBill);
                } else {
                    Log.w(TAG, "onClick: 无数据或无监听器");
                }
            });
        }

        void bind(Bill bill) {
            this.mBill = bill; // 强绑定
            // 时间图标
            bind.ivTimeIcon.setImageResource(isDaytime(bill.getBillTime()) ? R.drawable.ic_sun : R.drawable.ic_moon);
            // 分类图标
            if (bill.getCategoryIconUrl() != null && !bill.getCategoryIconUrl().isEmpty()) {
                bind.ivCategoryIcon.setVisibility(View.VISIBLE);
                ImageLoaderUtils.loadThumbnail(mContext, bill.getCategoryIconUrl(), bind.ivCategoryIcon);
            } else {
                bind.ivCategoryIcon.setVisibility(View.VISIBLE);
            }
            // 分类名
            bind.tvCategoryName.setText(bill.getCategoryName() == null || bill.getCategoryName().isEmpty() ? "未分类" : bill.getCategoryName());

            // 账户
            Account account = mAccountMap.get(bill.getAccountId());
            if (account != null) {
                bind.layoutAccountInfo.setVisibility(View.VISIBLE);
                bind.tvAccount.setText(account.getName());
                if (account.getIconUrl() != null && !account.getIconUrl().isEmpty()) {
                    ImageLoaderUtils.loadThumbnail(mContext, account.getIconUrl(), bind.ivAccountIcon);
                } else {
                    bind.ivAccountIcon.setImageResource(R.drawable.ic_wallet);
                }
            } else {
                bind.layoutAccountInfo.setVisibility(View.GONE);
            }

            // 时间
            bind.tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(bill.getBillTime()));
            // 金额
            DecimalFormat df = new DecimalFormat("#,##0.00");
            String amt; int color;
            switch (bill.getType()) {
                case 0: amt = "- ¥" + df.format(bill.getAmount()); color = mContext.getColor(R.color.red); break;
                case 1: amt = "+ ¥" + df.format(bill.getAmount()); color = mContext.getColor(R.color.green); break;
                case 3: amt = "¥" + df.format(bill.getAmount()); color = mContext.getColor(R.color.accent_color); break;
                default: amt = "¥" + df.format(bill.getAmount()); color = mContext.getColor(android.R.color.black); break;
            }
            bind.tvAmount.setText(amt);
            bind.tvAmount.setTextColor(color);
        }
    }

    static class BillGroup {
        String dateKey;
        String displayDate;
        List<Bill> bills;
        double totalIncome;
        double totalExpense;
    }
}