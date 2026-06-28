package com.example.my_project1.data.api.model;

/**
 * 批量响应通用结构，用于 Bmob 批量接口结果封装
 */
public class BmobBatchResponse {
    private boolean[] success;
    private String[] objectIds;
    private String[] errorMessages;

    public BmobBatchResponse(int size) {
        success = new boolean[size];
        objectIds = new String[size];
        errorMessages = new String[size];
    }

    public void setSuccess(int index, boolean ok) { success[index] = ok; }
    public void setObjectId(int index, String objectId) { objectIds[index] = objectId; }
    public void setErrorMessage(int index, String msg) { errorMessages[index] = msg; }

    public boolean isSuccess(int index) { return success[index]; }
    public String getObjectId(int index) { return objectIds[index]; }
    public String getErrorMessage(int index) { return errorMessages[index]; }
}
