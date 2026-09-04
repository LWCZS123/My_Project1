package com.example.my_project1.ui.viewmodel.saving;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存钱计划卡片生成器
 * 根据计划类型和规则动态生成 UI 展示所需的 SavingCardUiModel 列表
 */
public class SavingPlanCardGenerator {

    /**
     * 生成卡片列表
     */
    public static List<SavingCardUiModel> generateCards(SavingPlan plan, List<SavingRecord> records) {
        if (plan == null) return new ArrayList<>();

        // 将已完成的记录按 stepIndex 映射，方便查找
        Map<Integer, SavingRecord> recordMap = new HashMap<>();
        List<SavingRecord> extraRecords = new ArrayList<>();
        if (records != null) {
            for (SavingRecord record : records) {
                // 灵活存钱的所有记录，或结构化存钱中索引小于0的记录，都视为“额外记录”直接展示为卡片
                if (plan.getType() == SavingPlan.TYPE_FLEXIBLE || record.getStepIndex() < 0) {
                    extraRecords.add(record);
                } else {
                    recordMap.put(record.getStepIndex(), record);
                }
            }
        }

        List<SavingCardUiModel> cards;
        switch (plan.getType()) {
            case SavingPlan.TYPE_52WEEK:
                cards = generate52WeekCards(plan, recordMap);
                break;
            case SavingPlan.TYPE_365DAY:
                cards = generate365DayCards(plan, recordMap);
                break;
            case SavingPlan.TYPE_FIXED:
                cards = generateFixedCards(plan, recordMap);
                break;
            case SavingPlan.TYPE_FLEXIBLE:
            default:
                cards = new ArrayList<>();
                break;
        }

        // 将额外记录（手动添加的记录）转换为卡片并加入列表
        for (SavingRecord record : extraRecords) {
            cards.add(new SavingCardUiModel(
                    record.getStepIndex(),
                    record.getAmount(),
                    record.getRecordDate(),
                    true,
                    record.getId(),
                    record.getLinkedBillId() > 0
            ));
        }

        // 统一按时间排序：确保排序逻辑完全确定，防止进入页面时卡片跳动
        Collections.sort(cards, (c1, c2) -> {
            if (c1.getDueDate() == null || c2.getDueDate() == null) return 0;
            
            // 1. 先比较日期（精确到毫秒）
            int dateComp = c1.getDueDate().compareTo(c2.getDueDate());
            if (dateComp != 0) return dateComp;
            
            // 2. 如果日期完全一致（例如同一秒），固定步骤排在手动记录前面
            if (c1.getStepIndex() != c2.getStepIndex()) {
                return Integer.compare(c2.getStepIndex(), c1.getStepIndex()); // 较大的索引（固定步骤 >=0）在前
            }
            
            // 3. 最后按记录 ID 稳定排序
            return Long.compare(c1.getRecordId(), c2.getRecordId());
        });

        return cards;
    }

    private static List<SavingCardUiModel> generate52WeekCards(SavingPlan plan, Map<Integer, SavingRecord> recordMap) {
        List<SavingCardUiModel> cards = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plan.getStartDate());
        // 规格化日期到当天零点，避免毫秒级差异导致 UI 抖动
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        double currentAmount = plan.getFirstPeriodAmount();

        for (int i = 0; i < 52; i++) {
            SavingRecord record = recordMap.get(i);
            boolean completed = record != null;
            long recordId = completed ? record.getId() : -1;
            boolean isTransfer = completed && record.getLinkedBillId() > 0;
            // 始终使用计划的预定日期，点击完成时不更新为当前时间
            java.util.Date displayDate = calendar.getTime();

            cards.add(new SavingCardUiModel(i, currentAmount, displayDate, completed, recordId, isTransfer));

            calendar.add(Calendar.WEEK_OF_YEAR, 1);
            currentAmount += plan.getIncrementAmount();
        }
        return cards;
    }

    private static List<SavingCardUiModel> generate365DayCards(SavingPlan plan, Map<Integer, SavingRecord> recordMap) {
        List<SavingCardUiModel> cards = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plan.getStartDate());
        // 规格化日期到当天零点，避免毫秒级差异导致 UI 抖动
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < 365; i++) {
            int stepIndex = i + 1; 
            SavingRecord record = recordMap.get(stepIndex);
            boolean completed = record != null;
            long recordId = completed ? record.getId() : -1;
            boolean isTransfer = completed && record.getLinkedBillId() > 0;
            // 始终使用计划的预定日期，点击完成时不更新为当前时间
            java.util.Date displayDate = calendar.getTime();

            // 根据计划配置的金额生成，不再硬编码 1, 2, 3...
            double amount = plan.getFirstPeriodAmount() + (stepIndex - 1) * plan.getIncrementAmount();
            cards.add(new SavingCardUiModel(stepIndex, amount, displayDate, completed, recordId, isTransfer));

            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cards;
    }

    private static List<SavingCardUiModel> generateFixedCards(SavingPlan plan, Map<Integer, SavingRecord> recordMap) {
        List<SavingCardUiModel> cards = new ArrayList<>();
        if (plan.getDuration() <= 0) return cards;

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(plan.getStartDate());
        // 规格化日期到当天零点，避免毫秒级差异导致 UI 抖动
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < plan.getDuration(); i++) {
            SavingRecord record = recordMap.get(i);
            boolean completed = record != null;
            long recordId = completed ? record.getId() : -1;
            boolean isTransfer = completed && record.getLinkedBillId() > 0;
            // 始终使用计划的预定日期，点击完成时不更新为当前时间
            java.util.Date displayDate = calendar.getTime();

            cards.add(new SavingCardUiModel(i, plan.getPeriodAmount(), displayDate, completed, recordId, isTransfer));

            // 根据周期类型增加日期
            if ("每天".equals(plan.getPeriodType())) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            } else if ("每周".equals(plan.getPeriodType())) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1);
            } else if ("每月".equals(plan.getPeriodType())) {
                calendar.add(Calendar.MONTH, 1);
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return cards;
    }
}
