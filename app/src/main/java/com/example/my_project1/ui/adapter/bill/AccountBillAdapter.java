package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.databinding.ItemAccountTransactionBinding;
import com.example.my_project1.databinding.ItemDayHeaderBinding;
import com.example.my_project1.databinding.ItemMonthHeaderBinding;
import com.example.my_project1.ui.viewmodel.accountvm.AccountBillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AccountBillAdapter - 使用 View Binding 的账户账单适配器
 */
public class AccountBillAdapter extends ListAdapter<AccountBillUiModel, RecyclerView.ViewHolder> {

    private final Context context;
    private OnBillClickListener listener;
    private OnMonthToggleListener monthToggleListener;

    public interface OnBillClickListener {
        void onBillClick(Bill bill);
        void onBillDelete(Bill bill);
        void onBillRefund(Bill bill);
        void onBillEdit(Bill bill);
    }

    public interface OnMonthToggleListener {
        void onMonthToggle(String monthKey);
    }

    public AccountBillAdapter(Context context) {
        super(new DiffUtil.ItemCallback<AccountBillUiModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AccountBillUiModel oldItem, @NonNull AccountBillUiModel newItem) {
                if (oldItem.type != newItem.type) return false;
                if (oldItem.type == AccountBillUiModel.TYPE_BILL_ITEM) {
                    return oldItem.id == newItem.id;
                }
                return oldItem.key.equals(newItem.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull AccountBillUiModel oldItem, @NonNull AccountBillUiModel newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.context = context;
    }

    public void setOnBillClickListener(OnBillClickListener listener) {
        this.listener = listener;
    }

    public void setOnMonthToggleListener(OnMonthToggleListener listener) {
        this.monthToggleListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == AccountBillUiModel.TYPE_MONTH_HEADER) {
            ItemMonthHeaderBinding binding = ItemMonthHeaderBinding.inflate(inflater, parent, false);
            return new MonthViewHolder(binding);
        } else if (viewType == AccountBillUiModel.TYPE_DAY_HEADER) {
            ItemDayHeaderBinding binding = ItemDayHeaderBinding.inflate(inflater, parent, false);
            return new DayViewHolder(binding);
        } else {
            ItemAccountTransactionBinding binding = ItemAccountTransactionBinding.inflate(inflater, parent, false);
            return new BillViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AccountBillUiModel item = getItem(position);
        if (holder instanceof MonthViewHolder) {
            ((MonthViewHolder) holder).bind(item);
        } else if (holder instanceof DayViewHolder) {
            ((DayViewHolder) holder).bind(item);
        } else if (holder instanceof BillViewHolder) {
            ((BillViewHolder) holder).bind(item);
        }
    }

    // ==================== ViewHolders ====================

    class MonthViewHolder extends RecyclerView.ViewHolder {
        private final ItemMonthHeaderBinding binding;

        MonthViewHolder(ItemMonthHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            
            binding.getRoot().setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && monthToggleListener != null) {
                    monthToggleListener.onMonthToggle(getItem(pos).key);
                }
            });
        }

        void bind(AccountBillUiModel item) {
            binding.tvMonthTitle.setText(item.title);
            binding.tvMonthRange.setText(item.subtitle);
            binding.tvInflow.setText(item.inflowText);
            binding.tvOutflow.setText(item.outflowText);
            binding.ivArrow.setRotation(item.isCollapsed ? 0 : 180);
        }
    }

    class DayViewHolder extends RecyclerView.ViewHolder {
        private final ItemDayHeaderBinding binding;

        DayViewHolder(ItemDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountBillUiModel item) {
            binding.tvDayTitle.setText(item.title);
            binding.tvDaySummary.setText(item.subtitle);
        }
    }

    public class BillViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountTransactionBinding binding;

        BillViewHolder(ItemAccountTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AccountBillUiModel item) {
            if (item.categoryIconUrl != null && !item.categoryIconUrl.isEmpty()) {
                ImageLoaderUtils.loadThumbnail(context, item.categoryIconUrl, binding.ivCategoryIcon);
            } else {
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category);
            }
            
            binding.tvCategoryName.setText(item.categoryName);
            binding.tvTransactionTime.setText(item.timeNote);
            binding.tvAmount.setText(item.amountText);
            binding.tvAmount.setTextColor(item.amountColor);
            binding.tvBalanceAfter.setText(item.balanceText);

            binding.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onBillClick(item.originalBill);
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillDelete(item.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnRefund.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillRefund(item.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });
            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBillEdit(item.originalBill);
                    binding.swipeLayout.quickClose();
                }
            });
        }
    }
}
