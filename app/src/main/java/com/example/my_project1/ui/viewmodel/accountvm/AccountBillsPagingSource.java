package com.example.my_project1.ui.viewmodel.accountvm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.paging.PagingState;

import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.bill.BillWithBalance;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AccountBillsPagingSource extends PagingSource<Integer, BillWithBalance> {

    private final BillDao billDao;
    private final AccountDao accountDao;
    private final String userId;
    private final String accountId;
    private final long localAccountId;
    private final Date startTime;
    private final Date endTime;

    public AccountBillsPagingSource(BillDao billDao, AccountDao accountDao, String userId, String accountId, 
                                   long localAccountId, Date startTime, Date endTime) {
        this.billDao = billDao;
        this.accountDao = accountDao;
        this.userId = userId;
        this.accountId = accountId;
        this.localAccountId = localAccountId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, BillWithBalance> state) {
        return state.getAnchorPosition();
    }

    @NonNull
    @Override
    public Object load(@NonNull LoadParams<Integer> params, 
                       @NonNull kotlin.coroutines.Continuation<? super LoadResult<Integer, BillWithBalance>> continuation) {
        try {
            int offset = params.getKey() != null ? params.getKey() : 0;
            int loadSize = params.getLoadSize();

            final List<BillWithBalance>[] result = new List[1];
            Thread thread = new Thread(() -> {
                double currentBal = 0;
                if (accountDao != null) {
                    Account acc = null;
                    if (accountId != null && !accountId.isEmpty()) {
                        acc = accountDao.getAccountByCloudId(accountId);
                    }
                    if (acc == null && localAccountId > 0) {
                        acc = accountDao.getAccountByLocalId(localAccountId);
                    }
                    if (acc != null) {
                        currentBal = acc.getBalance();
                    }
                }
                result[0] = billDao.getAccountBillsWithBalancePaged(userId, accountId, localAccountId, 
                        currentBal, startTime, endTime, loadSize, offset);
            });
            thread.start();
            thread.join();
            
            List<BillWithBalance> bills = result[0] != null ? result[0] : new ArrayList<>();
            
            return new LoadResult.Page<>(
                    bills,
                    offset == 0 ? null : offset - loadSize,
                    bills.size() < loadSize ? null : offset + loadSize
            );
        } catch (Exception e) {
            return new LoadResult.Error<>(e);
        }
    }
}
