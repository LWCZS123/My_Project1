package com.example.my_project1.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;

import java.util.List;

/**
 * 存钱计划模块数据库访问对象
 * 提供计划、记录、步骤的CRUD操作
 */
@Dao
public interface SavingPlanDao {

    // --- 计划相关 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlan(SavingPlan plan);

    @Update
    void updatePlan(SavingPlan plan);

    @Delete
    void deletePlan(SavingPlan plan);

    @Query("DELETE FROM saving_plans WHERE id = :id")
    void deletePlanById(long id);

    @Query("SELECT * FROM saving_plans WHERE user_id = :userId AND sync_state != 'TO_DELETE' AND is_archived = 0 ORDER BY created_at DESC")
    LiveData<List<SavingPlan>> getAllPlansByUser(String userId);

    @Query("SELECT * FROM saving_plans WHERE user_id = :userId AND sync_state != 'TO_DELETE' AND is_archived = 1 ORDER BY created_at DESC")
    LiveData<List<SavingPlan>> getArchivedPlansByUser(String userId);

    @Query("SELECT * FROM saving_plans WHERE id = :id AND sync_state != 'TO_DELETE'")
    LiveData<SavingPlan> getPlanById(long id);
    
    @Query("SELECT * FROM saving_plans WHERE id = :id")
    SavingPlan getPlanByIdSync(long id);

    @Query("SELECT * FROM saving_plans WHERE user_id = :userId AND sync_state IN ('TO_CREATE', 'TO_UPDATE')")
    List<SavingPlan> getPendingSyncPlans(String userId);

    @Query("SELECT * FROM saving_plans WHERE user_id = :userId AND sync_state = 'TO_DELETE'")
    List<SavingPlan> getToDeletePlans(String userId);

    @Query("SELECT * FROM saving_plans WHERE object_id = :objectId LIMIT 1")
    SavingPlan getPlanByObjectId(String objectId);

    // --- 记录相关 ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRecord(SavingRecord record);

    @Update
    void updateRecord(SavingRecord record);

    @Delete
    void deleteRecord(SavingRecord record);

    @Query("DELETE FROM saving_records WHERE id = :id")
    void deleteRecordById(long id);

    @Query("SELECT * FROM saving_records WHERE plan_id = :planId AND sync_state != 'TO_DELETE' ORDER BY record_date DESC")
    LiveData<List<SavingRecord>> getRecordsByPlan(long planId);

    @Query("SELECT * FROM saving_records WHERE plan_id = :planId AND sync_state != 'TO_DELETE'")
    List<SavingRecord> getRecordsByPlanSync(long planId);

    @Query("SELECT * FROM saving_records WHERE id = :recordId AND sync_state != 'TO_DELETE'")
    LiveData<SavingRecord> getRecordById(long recordId);

    @Query("SELECT * FROM saving_records WHERE id = :id")
    SavingRecord getRecordByIdSync(long id);

    @Query("SELECT * FROM saving_records WHERE user_id = :userId AND sync_state IN ('TO_CREATE', 'TO_UPDATE')")
    List<SavingRecord> getPendingSyncRecords(String userId);

    @Query("SELECT * FROM saving_records WHERE user_id = :userId AND sync_state = 'TO_DELETE'")
    List<SavingRecord> getToDeleteRecords(String userId);

    @Query("SELECT * FROM saving_records WHERE object_id = :objectId LIMIT 1")
    SavingRecord getRecordByObjectId(String objectId);

    @Query("UPDATE saving_records SET sync_state = 'TO_DELETE', updated_at = :updatedAt WHERE plan_id = :planId AND sync_state != 'TO_DELETE'")
    void markRecordsDeleted(long planId, java.util.Date updatedAt);

    @Query("SELECT * FROM saving_records WHERE linked_bill_id = :billId LIMIT 1")
    SavingRecord getRecordByBillId(long billId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM saving_records WHERE plan_id = :planId AND sync_state != 'TO_DELETE'")
    double getSavedAmount(long planId);

    @Query("SELECT * FROM saving_records WHERE plan_id IN (SELECT id FROM saving_plans WHERE is_archived = 1) AND sync_state != 'TO_DELETE' ORDER BY record_date DESC")
    LiveData<List<SavingRecord>> getAllArchivedRecords();

}
