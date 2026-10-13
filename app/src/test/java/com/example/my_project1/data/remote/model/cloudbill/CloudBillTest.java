package com.example.my_project1.data.remote.model.cloudbill;

import com.example.my_project1.data.model.bill.Bill;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;

public class CloudBillTest {

    @Test
    public void testFromLocal_packsExtraJson() {
        Bill bill = new Bill();
        bill.setObjectId("test_obj_123");
        bill.setUserId("user_001");
        bill.setBookId("book_001");
        bill.setAccountId("acc_001");
        bill.setCategoryId("cat_meal");
        bill.setCategoryName("餐饮");
        bill.setCategoryIconUrl("http://example.com/icon.png");
        bill.setCategoryIconBackgroundColor("#FF0000");
        bill.setAmount(128.50);
        bill.setDiscount(10.0);
        bill.setType(0);
        bill.setExcludeBudget(true);
        bill.setExcludeStat(false);
        bill.setRemark("周末聚餐");
        bill.setLocation("五角场");
        bill.setImageUrls(Collections.singletonList("http://example.com/img.jpg"));

        // 报销字段
        bill.setReimbursable(true);
        bill.setReimbursed(false);
        bill.setReimburseAmount(100.0);
        bill.setReimburseTime(new Date(1700000000000L));
        bill.setReimburseAccountId("acc_reimburse");
        bill.setReimburseAccountName("招商银行");
        bill.setReimburseRemark("公司报销款");

        CloudBill cloudBill = CloudBill.fromLocal(bill);

        Assert.assertEquals("test_obj_123", cloudBill.getObjectId());
        Assert.assertEquals("cat_meal", cloudBill.getCategoryId());
        Assert.assertEquals(Double.valueOf(128.50), cloudBill.getAmount());
        Assert.assertEquals(Integer.valueOf(0), cloudBill.getType());

        String extraJson = cloudBill.getExtraJson();
        Assert.assertNotNull(extraJson);
        Assert.assertTrue(extraJson.contains("餐饮"));
        Assert.assertTrue(extraJson.contains("周末聚餐"));
        Assert.assertTrue(extraJson.contains("五角场"));
        Assert.assertTrue(extraJson.contains("公司报销款"));
    }

    @Test
    public void testToLocalEntity_restoresFieldsFromExtraJson() {
        Bill originalBill = new Bill();
        originalBill.setObjectId("test_obj_456");
        originalBill.setCategoryId("cat_transport");
        originalBill.setCategoryName("交通");
        originalBill.setAmount(45.0);
        originalBill.setDiscount(2.0);
        originalBill.setType(0);
        originalBill.setRemark("打车出行");
        originalBill.setLocation("虹桥机场");
        originalBill.setReimbursable(true);
        originalBill.setReimburseAmount(43.0);
        originalBill.setReimburseRemark("出差打车");

        CloudBill cloudBill = CloudBill.fromLocal(originalBill);

        Bill restoredBill = cloudBill.toLocalEntity();

        Assert.assertEquals("test_obj_456", restoredBill.getObjectId());
        Assert.assertEquals("cat_transport", restoredBill.getCategoryId());
        Assert.assertEquals("交通", restoredBill.getCategoryName());
        Assert.assertEquals(45.0, restoredBill.getAmount(), 0.001);
        Assert.assertEquals(2.0, restoredBill.getDiscount(), 0.001);
        Assert.assertEquals("打车出行", restoredBill.getRemark());
        Assert.assertEquals("虹桥机场", restoredBill.getLocation());
        Assert.assertTrue(restoredBill.isReimbursable());
        Assert.assertEquals(43.0, restoredBill.getReimburseAmount(), 0.001);
        Assert.assertEquals("出差打车", restoredBill.getReimburseRemark());
    }

    @Test
    public void testToLocalEntity_legacyFallback() {
        CloudBill legacyCloudBill = new CloudBill();
        legacyCloudBill.setObjectId("legacy_001");
        legacyCloudBill.setCategoryId("cat_supermarket");
        legacyCloudBill.setCategoryName("超市");
        legacyCloudBill.setAmount(88.0);
        legacyCloudBill.setType(0);
        legacyCloudBill.setRemark("买日用品");
        legacyCloudBill.setExtraJson(null); // 无 extraJson 的历史云端数据

        Bill restored = legacyCloudBill.toLocalEntity();

        Assert.assertEquals("legacy_001", restored.getObjectId());
        Assert.assertEquals("cat_supermarket", restored.getCategoryId());
        Assert.assertEquals("超市", restored.getCategoryName());
        Assert.assertEquals(88.0, restored.getAmount(), 0.001);
        Assert.assertEquals("买日用品", restored.getRemark());
    }
}
