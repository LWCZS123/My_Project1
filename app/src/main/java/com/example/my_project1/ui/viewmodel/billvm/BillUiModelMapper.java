package com.example.my_project1.ui.viewmodel.billvm;

import android.content.Context;
import android.net.Uri;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.Bill;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账单 UI 模型转换映射器
 * -------------------------------------------------------
 * 职责：
 * 1. 负责将数据库实体 Bill 映射转换为 UI 层直接使用的 BillUiModel。
 * 2. 内部维护高效缓存，避免列表拉刷或重复加载时反复解析字符串和格式化金额。
 */
public class BillUiModelMapper {

    private final Map<String, BillUiModel> billUiCache = new ConcurrentHashMap<>();

    /**
     * 清空模型缓存（切换用户或数据重置时使用）
     */
    public void clearCache() {
        billUiCache.clear();
    }

    /**
     * 将账单实体列表转换为 BillUiModel 列表
     *
     * @param context  上下文，用于获取颜色与图标资源
     * @param bills    账单实体列表
     * @param accounts 账户列表，用于关联账户名称与图标
     * @return 转换后的 BillUiModel 列表
     */
    public List<BillUiModel> mapBillsToUiModels(Context context, List<Bill> bills, List<Account> accounts) {
        if (bills == null || bills.isEmpty()) {
            return new ArrayList<>();
        }

        DecimalFormat amountFormatter = new DecimalFormat("#,##0.00");
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

        Map<String, Account> accountMap = new HashMap<>();
        if (accounts != null) {
            for (Account acc : accounts) {
                accountMap.put(acc.getObjectId(), acc);
            }
        }

        List<BillUiModel> uiModels = new ArrayList<>(bills.size());

        for (Bill bill : bills) {
            if (bill == null) continue;

            // 生成唯一缓存 Key：ID + 更新时间点
            long updateTime = bill.getUpdatedAt() != null ? bill.getUpdatedAt().getTime() : 0L;
            String cacheKey = "B_" + bill.getId() + "_" + updateTime;
            BillUiModel cached = billUiCache.get(cacheKey);
            if (cached != null) {
                uiModels.add(cached);
                continue;
            }

            int billType = bill.getType();
            String prefix;
            int amountColor;
            String categoryIcon = bill.getCategoryIconUrl() != null ? bill.getCategoryIconUrl() : "";

            switch (billType) {
                case 0: // 支出
                    prefix = "- ¥";
                    amountColor = context.getColor(R.color.red);
                    break;
                case 1: // 收入
                    prefix = "+ ¥";
                    amountColor = context.getColor(R.color.green);
                    break;
                default: // 转账/借还款
                    prefix = "¥";
                    amountColor = context.getColor(R.color.orange_500);
                    Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.drawable.ic_transference);
                    categoryIcon = uri.toString();
                    break;
            }

            String amountText = prefix + amountFormatter.format(bill.getAmount());

            Account account = accountMap.get(bill.getAccountId());
            Account toAccount = (billType == 2 || billType == 3) ? accountMap.get(bill.getToAccountId()) : null;

            BillUiModel newModel = BillUiModel.builder()
                    .localId(bill.getId())
                    .objectId(bill.getObjectId())
                    .timeText(timeFormatter.format(bill.getBillTime()))
                    .categoryName(bill.getCategoryName() != null ? bill.getCategoryName() : "")
                    .categoryIconUrl(categoryIcon)
                    .categoryIconBackgroundColor(bill.getCategoryIconBackgroundColor())
                    .amountText(amountText)
                    .amountColor(amountColor)
                    .accountName(account != null ? account.getName() : "")
                    .accountIconUrl(account != null ? account.getIconUrl() : "")
                    .toAccountName(toAccount != null ? toAccount.getName() : "")
                    .billType(billType)
                    .remarkText(bill.getRemark())
                    .imageUrls(bill.getImageUrls())
                    .originalBill(bill)
                    .build();

            billUiCache.put(cacheKey, newModel);
            uiModels.add(newModel);
        }

        return uiModels;
    }
}
