package com.example.my_project1.ui.viewmodel.saving;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.my_project1.data.dao.SavingPlanDao;
import com.example.my_project1.data.model.common.ApiResponse;
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.data.repository.saving.SavingRepository;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobUser;

/**
 * 存钱计划模块视图模型
 * 遵循单向数据流原则：
 * 1. 状态观察：通过DAO的LiveData观察数据库变化
 * 2. 行为分发：通过Repository触发写入和同步操作
 */
public class SavingViewModel extends AndroidViewModel {

    private final SavingRepository repository;
    private final SavingPlanDao savingPlanDao;
    
    /** 当前登录用户ID */
    private final MutableLiveData<String> currentUserId = new MutableLiveData<>();
    /** 操作状态反馈 */
    private final MutableLiveData<ApiResponse<Long>> operationState = 
            new MutableLiveData<>(ApiResponse.idle());

    public SavingViewModel(@NonNull Application application) {
        super(application);
        repository = SavingRepository.getInstance(application);
        savingPlanDao = repository.getSavingPlanDao();
        
        BmobUser user = BmobUser.getCurrentUser(BmobUser.class);
        currentUserId.setValue(user == null ? "" : user.getObjectId());
    }

    // --- 数据查询 ---

    /**
     * 获取主界面的统一数据流（Header + Methods + Section + Plans/Empty）
     */
    public LiveData<List<Object>> getMainSavingStream() {
        return Transformations.map(getAllPlans(), plans -> {
            List<Object> items = new ArrayList<>();
            items.add("HEADER");
            items.add("METHODS");
            items.add("SECTION");
            
            if (plans == null || plans.isEmpty()) {
                items.add("EMPTY");
            } else {
                items.addAll(plans);
            }
            return items;
        });
    }

    /**
     * 获取当前用户的所有存钱计划
     */
    public LiveData<List<SavingPlan>> getAllPlans() {
        return Transformations.switchMap(currentUserId, userId -> 
                savingPlanDao.getAllPlansByUser(userId == null ? "" : userId));
    }

    /**
     * 根据ID获取存钱计划详情
     */
    public LiveData<SavingPlan> getPlanById(long id) {
        return savingPlanDao.getPlanById(id);
    }

    /**
     * 获取详情页组合后的 UI 数据流（Header + Cards）
     * 优化：使用成员变量缓存，避免重复创建观察流，并处理双数据源同步问题
     */
    private androidx.lifecycle.MediatorLiveData<List<Object>> detailMediator;
    private long currentDetailPlanId = -1;

    public LiveData<List<Object>> getSavingDetailStream(long planId) {
        if (detailMediator != null && currentDetailPlanId == planId) {
            return detailMediator;
        }

        currentDetailPlanId = planId;
        detailMediator = new androidx.lifecycle.MediatorLiveData<>();
        
        LiveData<SavingPlan> planLiveData = savingPlanDao.getPlanById(planId);
        LiveData<List<SavingRecord>> recordsLiveData = savingPlanDao.getRecordsByPlan(planId);

        final Runnable update = () -> {
            SavingPlan plan = planLiveData.getValue();
            List<SavingRecord> records = recordsLiveData.getValue();
            
            // 只有当 plan 加载完成且 records 不为 null（Room 初始可能为 null）时才更新
            // 这样可以避免从“无记录”到“有记录”的瞬间闪烁
            if (plan != null && records != null) {
                List<Object> items = new ArrayList<>();
                items.add(plan);
                List<SavingCardUiModel> cards = SavingPlanCardGenerator.generateCards(plan, records);
                if (cards != null) {
                    items.addAll(cards);
                }
                
                List<Object> current = detailMediator.getValue();
                if (current == null || !current.equals(items)) {
                    detailMediator.setValue(items);
                }
            }
        };

        detailMediator.addSource(planLiveData, plan -> update.run());
        detailMediator.addSource(recordsLiveData, records -> update.run());

        return detailMediator;
    }

    /**
     * 获取计划下的所有存钱记录
     */
    public LiveData<List<SavingRecord>> getRecordsByPlan(long planId) {
        return savingPlanDao.getRecordsByPlan(planId);
    }

    /**
     * 获取已归档的存钱计划
     */
    public LiveData<List<SavingPlan>> getArchivedPlans() {
        return Transformations.switchMap(currentUserId, userId ->
                savingPlanDao.getArchivedPlansByUser(userId == null ? "" : userId));
    }

    /**
     * 获取所有已归档计划下的记录流
     */
    public LiveData<List<SavingCardUiModel>> getArchivedRecordsStream() {
        return Transformations.map(savingPlanDao.getAllArchivedRecords(), records -> {
            List<SavingCardUiModel> cards = new ArrayList<>();
            if (records != null) {
                for (SavingRecord record : records) {
                    cards.add(new SavingCardUiModel(
                            record.getStepIndex(),
                            record.getAmount(),
                            record.getRecordDate(),
                            true,
                            record.getId(),
                            record.getLinkedBillId() > 0
                    ));
                }
            }
            return cards;
        });
    }

    /**
     * 获取当前操作的状态反馈
     */
    public LiveData<ApiResponse<Long>> getOperationState() {
        return operationState;
    }

    // --- 业务操作 ---

    /**
     * 保存或更新存钱计划
     */
    public void savePlan(SavingPlan plan) {
        if (!validatePlan(plan)) return;
        operationState.setValue(ApiResponse.loading("正在保存计划"));
        if (plan.getId() == 0) {
            plan.setUserId(currentUserId.getValue());
            repository.insertPlan(plan, operationState::setValue);
        } else {
            repository.updatePlan(plan, operationState::setValue);
        }
    }

    /**
     * 删除指定的存钱计划
     */
    public void deletePlan(long planId, boolean deleteBills) {
        operationState.setValue(ApiResponse.loading("正在删除计划"));
        repository.deletePlan(planId, deleteBills, operationState::setValue);
    }

    /**
     * 存入一笔存钱记录
     */
    public void saveRecord(SavingRecord record, int stepIndex, long fromAccountId, long toAccountId) {
        if (record == null || record.getPlanId() <= 0) {
            operationState.setValue(ApiResponse.error("无效的记录数据"));
            return;
        }
        if (record.getAmount() <= 0) {
            operationState.setValue(ApiResponse.error("存入金额需大于0"));
            return;
        }
        operationState.setValue(ApiResponse.loading("正在记录存钱"));
        repository.insertRecord(record, stepIndex, fromAccountId, toAccountId, operationState::setValue);
    }

    /**
     * 归档或取消归档计划
     */
    public void toggleArchivePlan(SavingPlan plan) {
        plan.setArchived(!plan.isArchived());
        operationState.setValue(ApiResponse.loading(plan.isArchived() ? "正在归档计划" : "正在恢复计划"));
        repository.updatePlan(plan, operationState::setValue);
    }

    /**
     * 删除一笔存钱记录
     */
    public void deleteRecord(long recordId) {
        operationState.setValue(ApiResponse.loading("正在删除记录"));
        repository.deleteRecord(recordId, operationState::setValue);
    }

    /**
     * 重置操作状态
     */
    public void resetOperationState() {
        operationState.setValue(ApiResponse.idle());
    }

    /**
     * 刷新当前用户信息（用于切换账号场景）
     */
    public void refreshUser() {
        BmobUser user = BmobUser.getCurrentUser();
        currentUserId.setValue(user == null ? "" : user.getObjectId());
    }

    /**
     * 内部校验计划数据的合法性
     */
    private boolean validatePlan(SavingPlan plan) {
        String userId = currentUserId.getValue();
        if (userId == null || userId.trim().isEmpty()) {
            operationState.setValue(ApiResponse.error("请先登录账号"));
            return false;
        }
        if (plan == null || plan.getName() == null || plan.getName().trim().isEmpty()) {
            operationState.setValue(ApiResponse.error("计划名称不能为空"));
            return false;
        }
        if (plan.getTargetAmount() <= 0) {
            operationState.setValue(ApiResponse.error("目标金额必须大于0"));
            return false;
        }
        return true;
    }
}
