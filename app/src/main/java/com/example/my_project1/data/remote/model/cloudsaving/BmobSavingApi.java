package com.example.my_project1.data.remote.model.cloudsaving;

import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
import cn.bmob.v3.exception.BmobException;
import java.util.List;

public interface BmobSavingApi {
    String getCurrentUserId();
    boolean uploadPlanSync(SavingPlan local);
    boolean uploadRecordSync(SavingRecord local, String planObjectId);
    boolean deletePlanSync(String objectId);
    boolean deleteRecordSync(String objectId);
    List<CloudSavingPlan> fetchPlansSync() throws BmobException;
    List<CloudSavingRecord> fetchRecordsSync() throws BmobException;
}
