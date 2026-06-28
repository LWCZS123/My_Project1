package com.example.my_project1.work;

import android.content.Context;
import android.util.Log;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.my_project1.data.dao.BudgetDao;
import com.example.my_project1.data.database.AppDatabase;
import com.example.my_project1.data.model.SyncState;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.utils.BudgetPeriodHelper;

import java.util.Calendar;
import java.util.List;

import cn.bmob.v3.BmobUser;
import io.reactivex.annotations.NonNull;

/**
 * BudgetResetWorker
 *
 * 每天凌晨 0 点由 BudgetResetScheduler 触发，负责重置天/周分类预算的时间窗口。
 *
 * 重置规则：
 *   - PERIOD_DAY：当新的一天开始，且当前时间已超出预算的 endTime，则重置为今日范围。
 *   - PERIOD_WEEK：在周一凌晨，且当前时间已超出预算的 endTime，则重置为本周范围。
 *
 * 重置只更新时间窗口（startTime / endTime），预算金额保持不变。
 * 重置后将 syncState 标记为 TO_UPDATE，触发后续云端同步。
 */
public class BudgetResetWorker extends Worker {

    private static final String TAG = "BudgetResetWorker";

    public BudgetResetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context ctx = getApplicationContext();
            BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
            if (user == null) return Result.success();

            String userId = user.getObjectId();
            BudgetDao dao = AppDatabase.getInstance(ctx).budgetDao();

            Calendar now = Calendar.getInstance();
            long nowMs = now.getTimeInMillis();
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);

            // 获取所有分类预算（target_type = 1，包含天/周周期）
            List<Budget> allCategoryBudgets = dao.getCategoryBudgetsSync(userId);
            if (allCategoryBudgets == null || allCategoryBudgets.isEmpty()) {
                return Result.success();
            }

            for (Budget b : allCategoryBudgets) {
                int period = b.getPeriod();

                if (period == Budget.PERIOD_DAY) {
                    // 当当前时间超出预算窗口 endTime 时，说明旧周期已过，需要重置
                    if (nowMs > b.getEndTime()) {
                        resetBudgetPeriod(dao, b, Budget.PERIOD_DAY);
                        Log.d(TAG, "天预算已重置 id=" + b.getId());
                    }

                } else if (period == Budget.PERIOD_WEEK) {
                    // 仅在周一且旧周期已过时才重置
                    if (dayOfWeek == Calendar.MONDAY && nowMs > b.getEndTime()) {
                        resetBudgetPeriod(dao, b, Budget.PERIOD_WEEK);
                        Log.d(TAG, "周预算已重置 id=" + b.getId());
                    }
                }
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "预算重置失败：" + e.getMessage(), e);
            return Result.retry();
        }
    }

    /**
     * 将指定预算的时间窗口更新为当前周期的范围。
     * 仅修改 startTime / endTime / updatedAt / syncState，不清空 amount。
     */
    private void resetBudgetPeriod(BudgetDao dao, Budget budget, int period) {
        long[] range = BudgetPeriodHelper.getPeriodRange(period);
        budget.setStartTime(range[0]);
        budget.setEndTime(range[1]);
        budget.setUpdatedAt(System.currentTimeMillis());
        budget.setSyncState(SyncState.TO_UPDATE.getValue());
        dao.update(budget);
    }
}