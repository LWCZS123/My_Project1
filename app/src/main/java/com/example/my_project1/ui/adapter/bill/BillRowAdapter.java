package com.example.my_project1.ui.adapter.bill;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_project1.databinding.ItemBillBinding;
import com.example.my_project1.ui.adapter.photo.PhotoAdapter;
import com.example.my_project1.ui.viewmodel.billvm.BillUiModel;
import com.example.my_project1.utils.ImageLoaderUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class BillRowAdapter extends RecyclerView.Adapter<BillRowAdapter.RowVH> {

    private final Context context;
    private final List<BillUiModel> bills = new ArrayList<>();
    private final BillAdapter.OnBillClickListener listener;
    private final RecyclerView.RecycledViewPool photoPool;

    public BillRowAdapter(Context context, BillAdapter.OnBillClickListener listener, RecyclerView.RecycledViewPool photoPool) {
        this.context = context;
        this.listener = listener;
        this.photoPool = photoPool;
    }

    public void setBills(List<BillUiModel> newBills) {
        this.bills.clear();
        if (newBills != null) this.bills.addAll(newBills);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RowVH(ItemBillBinding.inflate(LayoutInflater.from(context), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RowVH holder, int position) {
        holder.bind(bills.get(position), position == bills.size() - 1);
    }

    @Override
    public int getItemCount() {
        return bills.size();
    }

    class RowVH extends RecyclerView.ViewHolder {
        private final ItemBillBinding b;

        RowVH(ItemBillBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(BillUiModel bill, boolean isLast) {
            b.tvCategory.setText(bill.categoryName);
            b.tvAmount.setText(bill.amountText);
            b.tvAmount.setTextColor(bill.amountColor);
            
            String subInfo = bill.timeText;
            if (bill.remarkText != null && !bill.remarkText.isEmpty()) {
                subInfo += " | " + bill.remarkText;
            }
            b.tvSubInfo.setText(subInfo);
            b.tvAccount.setText(bill.accountName);

            ImageLoaderUtils.loadThumbnail(context, bill.categoryIconUrl, b.ivCategoryIcon);

            if (bill.imageUrls != null && !bill.imageUrls.isEmpty()) {
                b.ivBillImage.setVisibility(View.VISIBLE);
                b.ivBillImage.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                if (photoPool != null) b.ivBillImage.setRecycledViewPool(photoPool);
                
                PhotoAdapter photoAdapter = new PhotoAdapter(context, new ArrayList<>(), false,
                        new PhotoAdapter.OnPhotoClickListener() {
                            @Override public void onPhotoClick(String url, int pos) {
                                if (listener != null) listener.onPhotoClick(url, pos);
                            }
                            @Override public void onDeleteClick(int pos) { }
                        });
                b.ivBillImage.setAdapter(photoAdapter);
                photoAdapter.setPhotos(bill.imageUrls);
            } else {
                b.ivBillImage.setVisibility(View.GONE);
            }

            b.billDivider.setVisibility(isLast ? View.GONE : View.VISIBLE);

            b.contentView.setOnClickListener(v -> {
                if (listener != null) listener.onBillClick(bill.localId, bill.objectId, b.getRoot());
            });

            b.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onBillDelete(bill);
                b.swipeLayout.quickClose();
            });
            b.btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onBillEdit(bill);
                b.swipeLayout.quickClose();
            });
            b.btnRefund.setOnClickListener(v -> {
                if (listener != null) listener.onBillRefund(bill);
                b.swipeLayout.quickClose();
            });
        }
    }
}
