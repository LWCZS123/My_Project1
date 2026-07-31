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
import com.example.my_project1.databinding.ItemTransactionBinding;
import com.example.my_project1.databinding.ItemTransationDateHeaderBinding;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BillListAdapter extends ListAdapter<BillListAdapter.ListItem, RecyclerView.ViewHolder> {

    private final Context context;
    private OnBillClickListener listener;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BILL = 1;
    private static final int TYPE_BILLS_CARD = 2;

    public interface OnBillClickListener {
        void onBillClick(BillUiModel bill);
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

        public BillsCardViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.ll_bill_container);
        }

        public void bind(List<BillUiModel> bills) {
            container.removeAllViews();
            if (bills == null) return;
            for (int i = 0; i < bills.size(); i++) {
                BillUiModel bill = bills.get(i);
                ItemTransactionBinding b = ItemTransactionBinding.inflate(
                        LayoutInflater.from(context), container, false);

                // Bind bill data from UiModel - NO CALCULATION HERE
                b.tvTime.setText(bill.timeText);
                
                if (bill.categoryIconUrl != null && !bill.categoryIconUrl.isEmpty()) {
                    ImageLoaderUtils.loadThumbnail(context, bill.categoryIconUrl, b.ivCategoryIcon);
                } else {
                    b.ivCategoryIcon.setImageResource(R.drawable.ic_wechat);
                }
                b.tvCategoryName.setText(bill.categoryName);
                
                // Bind note
                if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                    b.tvNote.setText(bill.remarkText);
                    b.tvNote.setVisibility(View.VISIBLE);
                } else {
                    b.tvNote.setVisibility(View.GONE);
                }

                b.tvAmount.setText(bill.amountText);
                b.tvAmount.setTextColor(bill.amountColor);
                b.layoutAccountInfo.setVisibility(View.GONE);

                // Hide last separator
                if (i == bills.size() - 1) {
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

        public void bind(BillUiModel bill) {
            if (bill == null) return;
            
            binding.tvTime.setText(bill.timeText);

            // 分类图标
            if (bill.categoryIconUrl != null && !bill.categoryIconUrl.isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, bill.categoryIconUrl, binding.ivCategoryIcon);
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_wechat);
            }

            // 分类名
            binding.tvCategoryName.setText(bill.categoryName);

            // 备注
            if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                binding.tvNote.setText(bill.remarkText);
                binding.tvNote.setVisibility(View.VISIBLE);
            } else {
                binding.tvNote.setVisibility(View.GONE);
            }

            // 金额
            binding.tvAmount.setText(bill.amountText);
            binding.tvAmount.setTextColor(bill.amountColor);

            binding.layoutAccountInfo.setVisibility(View.GONE);
        }
    }

    public static class ListItem {
        public boolean isHeader;
        public String date;
        public int count;
        public BillUiModel bill;
        public List<BillUiModel> bills;

        public ListItem(String date, int count) {
            this.isHeader = true;
            this.date = date;
            this.count = count;
        }

        public ListItem(BillUiModel bill) {
            this.isHeader = false;
            this.bill = bill;
        }

        public ListItem(List<BillUiModel> bills) {
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

            if (oldItem.bills != null && newItem.bills != null) {
                return Objects.equals(oldItem.bills, newItem.bills);
            }

            if (oldItem.bill != null && newItem.bill != null) {
                return oldItem.bill.localId == newItem.bill.localId;
            }

            return oldItem.bill == null && newItem.bill == null && oldItem.bills == null && newItem.bills == null;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            return oldItem.equals(newItem);
        }
    }
}
