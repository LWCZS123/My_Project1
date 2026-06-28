package com.example.my_project1.data.api;
import com.example.my_project1.data.api.model.BmobBatchResponse;
import java.util.List;

/**
 * BmobApi 抽象接口（网络层）
 * - fetchAllCategories/fetchAllSubCategories: 从云端获取指定用户的全部分类
 * - batchUpsertCategories/batchUpsertSubCategories: 批量创建/更新分类（如果 Bmob 无批量接口，则在实现中并发多个单次请求）
 * - deleteByCloudId: 删除云端 object（单条）
 *
 * 实现注意：
 * - 返回值与异常：如果发生网络/服务端错误，应抛出异常以便上层重试处理
 */
public interface BmobApi {

    // 拉取所有该用户的主分类（若支持分页，请实现分页或在实现中统一聚合）
    List<BmobCategoryDto> fetchAllCategories(String ownerId) throws Exception;

    // 拉取所有子分类
    List<BmobSubCategoryDto> fetchAllSubCategories(String ownerId) throws Exception;

    // 批量创建/更新主分类（实现可把每条转成单个请求并并发执行）
    BmobBatchResponse batchUpsertCategories(List<BmobCategoryDto> dtos) throws Exception;

    // 批量子分类 upsert
    BmobBatchResponse batchUpsertSubCategories(List<BmobSubCategoryDto> dtos) throws Exception;

    // 删除单个云端对象（可以用 objectId）
    boolean deleteCategoryByCloudId(String cloudId) throws Exception;
    boolean deleteSubCategoryByCloudId(String cloudId) throws Exception;
}

/**
 * BmobCategoryDto：用于和网络层交换的 DTO
 */
class BmobCategoryDto {
    public String objectId;    // 云端 objectId
    public String ownerId;     // 所属用户 id
    public String type;        // expense / income
    public String name;
    public String iconUrl;
    public String color;
    public int order;
    public long lastModified;  // 云端时间戳（ms），
    // 本地 id 用于 mapping 回本地记录（非云端字段）
    public long localId;
    public int syncState;
}

/**
 * BmobSubCategoryDto：子分类 DTO
 */
class BmobSubCategoryDto {
    public String objectId;
    public String ownerId;
    public String parentCloudId; // 注意：引用父分类 cloudId（不是 localId）
    public String name;
    public String iconUrl;
    public int order;
    public long lastModified;
    public long localId;
    public int syncState;
}


