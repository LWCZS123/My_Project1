package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.LinearLayout;
import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemTransactionBinding;
import com.example.my_project1.databinding.ItemTransationDateHeaderBinding;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BillListAdapter extends ListAdapter<BillListAdapter.ListItem, RecyclerView.ViewHolder> {

    private final Context context;
    private OnBillClickListener listener;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BILL = 1;
    private static final int TYPE_BILLS_CARD = 2;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.listener = listener;
    }

    public BillListAdapter(Context context) {
        super(new ListItemDiffCallback());
        this.context = context;
    }

    // 为了兼容原有代码，提供 setData 方法
    public void setData(List<ListItem> data) {
        submitList(data);
    }

    @Override
    public int getItemViewType(int position) {
        ListItem item = getItem(position);
        if (item.isHeader) return TYPE_HEADER;
        if (item.bills != null) return TYPE_BILLS_CARD;
        return TYPE_BILL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            ItemTransationDateHeaderBinding binding = ItemTransationDateHeaderBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new HeaderViewHolder(binding);
        } else if (viewType == TYPE_BILLS_CARD) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bill_card, parent, false);
            return new BillsCardViewHolder(view);
        } else {
            ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new BillViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof BillViewHolder) {
            ((BillViewHolder) holder).bind(item.bill);
        } else if (holder instanceof BillsCardViewHolder) {
            ((BillsCardViewHolder) holder).bind(item.bills);
        }
    }

    class BillsCardViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final DecimalFormat amountFormat = new DecimalFormat("#,##0.00");

        public BillsCardViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.ll_bill_container);
        }

        public void bind(List<Bill> bills) {
            container.removeAllViews();
            if (bills == null) return;
            for (int i = 0; i < bills.size(); i++) {
                Bill bill = bills.get(i);
                ItemTransactionBinding b = ItemTransactionBinding.inflate(
                        LayoutInflater.from(context), container, false);

                // Bind bill data
                if (bill.getBillTime() != null) {
                    int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.getDefault()).format(bill.getBillTime()));
                    b.ivTimeIcon.setImageResource(hour >= 6 && hour < 18 ? R.drawable.ic_sun : R.drawable.ic_moon);
                    b.tvTime.setText(timeFormat.format(bill.getBillTime()));
                }
                if (bill.getCategoryIconUrl() != null && !bill.getCategoryIconUrl().isEmpty()) {
                    ImageLoaderUtils.loadThumbnail(context, bill.getCategoryIconUrl(), b.ivCategoryIcon);
                } else {
                    b.ivCategoryIcon.setImageResource(R.drawable.ic_wechat);
                }
                b.tvCategoryName.setText(bill.getCategoryName() != null ? bill.getCategoryName() : "未分类");
                
                // Bind note
                if (bill.getRemark() != null && !bill.getRemark().isEmpty()) {
                    b.tvNote.setText(bill.getRemark());
                    b.tvNote.setVisibility(View.VISIBLE);
                } else {
                    b.tvNote.setVisibility(View.GONE);
                }

                String prefix = bill.getType() == 0 ? "- ¥" : "+ ¥";
                int color = context.getColor(bill.getType() == 0 ? R.color.red : R.color.green);
                b.tvAmount.setText(prefix + amountFormat.format(bill.getAmount()));
                b.tvAmount.setTextColor(color);
                b.layoutAccountInfo.setVisibility(View.GONE);

                // Hide last separator
                if (i == bills.size() - 1) {
                    // In item_transaction.xml, the divider is the last child of the main LinearLayout
                    View root = b.getRoot();
                    if (root instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) root;
                        if (ll.getChildCount() > 0) {
                            View lastChild = ll.getChildAt(ll.getChildCount() - 1);
                            lastChild.setVisibility(View.GONE);
                        }
                    }
                }

                b.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onBillClick(bill);
                });
                container.addView(b.getRoot());
            }
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransationDateHeaderBinding binding;

        public HeaderViewHolder(ItemTransationDateHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ListItem item) {
            binding.tvDate.setText(item.date);
            binding.tvSummary.setText(item.count + "笔账单");
            binding.tvSummary.setVisibility(View.VISIBLE);
        }
    }

    class BillViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final DecimalFormat amountFormat = new DecimalFormat("#,##0.00");

        public BillViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    ListItem item = getItem(position);
                    if (!item.isHeader) {
                        listener.onBillClick(item.bill);
                    }
                }
            });
        }

        public void bind(Bill bill) {
            if (bill == null) return;
            // 时间图标
            if (bill.getBillTime() != null) {
                int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.getDefault()).format(bill.getBillTime()));
                binding.ivTimeIcon.setImageResource(hour >= 6 && hour < 18 ? R.drawable.ic_sun : R.drawable.ic_moon);
                binding.tvTime.setText(timeFormat.format(bill.getBillTime()));
            }

            // 分类图标
            if (bill.getCategoryIconUrl() != null && !bill.getCategoryIconUrl().isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, bill.getCategoryIconUrl(), binding.ivCategoryIcon);
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_wechat);
            }

            // 分类名
            binding.tvCategoryName.setText(bill.getCategoryName() != null ? bill.getCategoryName() : "未分类");

            // 备注
            if (bill.getRemark() != null && !bill.getRemark().isEmpty()) {
                binding.tvNote.setText(bill.getRemark());
                binding.tvNote.setVisibility(View.VISIBLE);
            } else {
                binding.tvNote.setVisibility(View.GONE);
            }

            // 金额
            String prefix = bill.getType() == 0 ? "- ¥" : "+ ¥";
            int color = context.getColor(bill.getType() == 0 ? R.color.red : R.color.green);
            binding.tvAmount.setText(prefix + amountFormat.format(bill.getAmount()));
            binding.tvAmount.setTextColor(color);

            binding.layoutAccountInfo.setVisibility(View.GONE);
        }
    }

    public static class ListItem {
        public boolean isHeader;
        public String date;
        public int count;
        public Bill bill;
        public List<Bill> bills;

        public ListItem(String date, int count) {
            this.isHeader = true;
            this.date = date;
            this.count = count;
        }

        public ListItem(Bill bill) {
            this.isHeader = false;
            this.bill = bill;
        }

        public ListItem(List<Bill> bills) {
            this.isHeader = false;
            this.bills = bills;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListItem listItem = (ListItem) o;
            return isHeader == listItem.isHeader &&
                    count == listItem.count &&
                    Objects.equals(date, listItem.date) &&
                    Objects.equals(bill, listItem.bill) &&
                    Objects.equals(bills, listItem.bills);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isHeader, date, count, bill, bills);
        }
    }

    static class ListItemDiffCallback extends DiffUtil.ItemCallback<ListItem> {
        @Override
        public boolean areItemsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            if (oldItem.isHeader != newItem.isHeader) return false;

            if (oldItem.isHeader) {
                return Objects.equals(oldItem.date, newItem.date);
            }

            // Both are not headers. Check types.
            if (oldItem.bills != null && newItem.bills != null) {
                // Both are bill cards. Compare lists.
                return Objects.equals(oldItem.bills, newItem.bills);
            }

            if (oldItem.bill != null && newItem.bill != null) {
                // Both are single bills.
                return oldItem.bill.getId() == newItem.bill.getId();
            }

            // Mismatched types or both null
            return oldItem.bill == null && newItem.bill == null && oldItem.bills == null && newItem.bills == null;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            return oldItem.equals(newItem);
        }
    }
}
