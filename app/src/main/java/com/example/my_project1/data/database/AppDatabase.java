package com.example.my_project1.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.my_project1.data.converter.Converters;
import com.example.my_project1.data.converter.SubCategoryListConverter;
import com.example.my_project1.data.converter.SyncStateConverter;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.BudgetDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.SearchHistoryDao;
import com.example.my_project1.data.dao.SubCategoryDao;
import com.example.my_project1.data.dao.UserProfileDao;
import com.example.my_project1.data.dao.WishDao;
import com.example.my_project1.data.model.Category;
import com.example.my_project1.data.model.SubCategory;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.data.model.account.AccountGroup;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.model.bill.SearchHistory;
import com.example.my_project1.data.model.budget.Budget;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;

@Database(
        entities = {Category.class, SubCategory.class,
                AccountGroup.class, Account.class, Bill.class,
                SearchHistory.class, UserProfile.class, Budget.class,
                Wish.class, WishRecord.class
        },
        version = 16,
        exportSchema = true
)

@TypeConverters({
        SubCategoryListConverter.class,
        Converters.class,
        SyncStateConverter.class
})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract CategoryDao categoryDao();
    public abstract SubCategoryDao subCategoryDao();
    public abstract AccountDao accountDao();
    public abstract BillDao billDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract UserProfileDao userProfileDao();
    public abstract BudgetDao budgetDao();
    public abstract WishDao wishDao();


    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "accounting_app_db"
                    )
                            .fallbackToDestructiveMigration() // 调试阶段允许重建数据库
                            .build();
                }
            }
        }
        return INSTANCE;
    }


}
