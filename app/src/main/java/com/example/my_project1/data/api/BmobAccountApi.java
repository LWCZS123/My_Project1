package com.example.my_project1.data.api;

import com.example.my_project1.data.api.model.BmobBatchResponse;

import java.util.List;

/**
 * ============================================================
 * 🧩 BmobAccountApi 抽象接口（网络层）
 * ============================================================
 * 用途：
 *   - 定义账户（Account）与账户组（AccountGroup）在云端的交互规范。
 *   - Repository 层通过该接口访问 Bmob 云端，实现上传 / 更新 / 删除 / 拉取等功能。
 *
 * 实现类示例：
 *   - {@link com.example.my_project1.data.remote.model.cloudaccount.BmobAccountApiImpl}
 *
 * 设计说明：
 *   - 与 BmobApi 分类模块接口保持一致风格。
 *   - 如果 Bmob 不支持批量接口，需在实现中通过并发多次单请求模拟批量操作。
 *   - 若请求失败，应抛出异常供上层（如 Repository）处理或重试。
 */
public interface BmobAccountApi {

    // -----------------------------------------------------------
    // 🟢 账户组相关接口（AccountGroup）
    // -----------------------------------------------------------

    /**
     * 从云端拉取当前用户的全部账户组
     * @param ownerId 当前用户的 objectId
     * @return 云端账户组 DTO 列表
     * @throws Exception 网络或服务器异常时抛出
     */
    List<BmobAccountGroupDto> fetchAllAccountGroups(String ownerId) throws Exception;

    /**
     * 批量创建 / 更新账户组（Upsert）
     * @param dtos 要同步的账户组列表
     * @return 批量操作的执行结果（每条成功 / 失败状态、objectId等）
     * @throws Exception 网络或服务器异常时抛出
     */
    BmobBatchResponse batchUpsertAccountGroups(List<BmobAccountGroupDto> dtos) throws Exception;

    /**
     * 删除单个云端账户组对象
     * @param cloudId 云端 objectId
     * @return 是否删除成功
     * @throws Exception 网络或服务器异常时抛出
     */
    boolean deleteAccountGroupByCloudId(String cloudId) throws Exception;


    // -----------------------------------------------------------
    // 🟡 账户相关接口（Account）
    // -----------------------------------------------------------

    /**
     * 从云端拉取当前用户的全部账户
     * @param ownerId 当前用户的 objectId
     * @return 云端账户 DTO 列表
     * @throws Exception 网络或服务器异常时抛出
     */
    List<BmobAccountDto> fetchAllAccounts(String ownerId) throws Exception;

    /**
     * 批量创建 / 更新账户（Upsert）
     * @param dtos 要同步的账户列表
     * @return 批量操作结果
     * @throws Exception 网络或服务器异常时抛出
     */
    BmobBatchResponse batchUpsertAccounts(List<BmobAccountDto> dtos) throws Exception;

    /**
     * 删除单个云端账户对象
     * @param cloudId 云端 objectId
     * @return 是否删除成功
     * @throws Exception 网络或服务器异常时抛出
     */
    boolean deleteAccountByCloudId(String cloudId) throws Exception;
}


/**
 * ============================================================
 * 📦 BmobAccountGroupDto：账户组 DTO（数据传输对象）
 * ============================================================
 * 用于在应用层与云端之间传递账户组数据。
 */
class BmobAccountGroupDto {

    /** 云端 objectId（若本地新建则为空） */
    public String objectId;

    /** 所属用户 ID（即 BmobUser 的 objectId） */
    public String ownerId;

    /** 账户组名称 */
    public String name;

    /** 排序序号 */
    public int order;

    /** 图标 URL */
    public String iconUrl;

    /** 最后修改时间戳（毫秒） */
    public long lastModified;

    /** 本地数据库主键 ID（非云端字段） */
    public long localId;

    /** 同步状态（0=已同步，1=待创建，2=待更新，3=待删除） */
    public int syncState;
}


/**
 * ============================================================
 * 💰 BmobAccountDto：账户 DTO（数据传输对象）
 * ============================================================
 * 用于账户模块的云端交互。
 */
class BmobAccountDto {

    /** 云端 objectId */
    public String objectId;

    /** 所属用户 ID（BmobUser.objectId） */
    public String ownerId;

    /** 所属账户组的云端 ID（group.objectId） */
    public String groupCloudId;

    /** 账户名称 */
    public String name;

    /** 当前余额 */
    public double balance;

    /** 是否为信用账户 */
    public boolean isCredit;

    /** 信用额度（仅信用账户有效） */
    public double creditLimit;

    /** 备注信息 */
    public String remark;

    /** 卡号（可选） */
    public String cardNumber;

    /** 图标 URL */
    public String iconUrl;

    /** 最后修改时间戳（毫秒） */
    public long lastModified;

    /** 本地数据库主键 ID（非云端字段） */
    public long localId;

    /** 同步状态（0=已同步，1=待创建，2=待更新，3=待删除） */
    public int syncState;
}
