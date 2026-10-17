package com.example.my_project1.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import androidx.annotation.NonNull;

import com.example.my_project1.data.converter.Converters;
import com.example.my_project1.data.converter.SubCategoryListConverter;
import com.example.my_project1.data.converter.SyncStateConverter;
import com.example.my_project1.data.dao.AccountDao;
import com.example.my_project1.data.dao.BillDao;
import com.example.my_project1.data.dao.BudgetDao;
import com.example.my_project1.data.dao.CategoryDao;
import com.example.my_project1.data.dao.DownloadDao;
import com.example.my_project1.data.dao.SavingPlanDao;
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
import com.example.my_project1.data.model.saving.SavingPlan;
import com.example.my_project1.data.model.saving.SavingRecord;
import com.example.my_project1.data.model.user.UserProfile;
import com.example.my_project1.data.model.wish.Wish;
import com.example.my_project1.data.model.wish.WishRecord;
import com.example.my_project1.data.model.icon.DownloadRecord;

@Database(
        entities = {Category.class, SubCategory.class,
                AccountGroup.class, Account.class, Bill.class,
                SearchHistory.class, UserProfile.class, Budget.class,
                Wish.class, WishRecord.class,
                SavingPlan.class, SavingRecord.class, DownloadRecord.class,
                com.example.my_project1.data.model.tag.TagGroupEntity.class,
                com.example.my_project1.data.model.tag.TagEntity.class,
                com.example.my_project1.data.model.tag.BillTagCrossRef.class
        },
        version = 46,
        exportSchema = true
)

@TypeConverters({
        SubCategoryListConverter.class,
        Converters.class,
        SyncStateConverter.class
})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_user_id_billTime` "
                    + "ON `bills` (`user_id`, `billTime`)");
        }
    };
    static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_budget_stats` "
                    + "ON `bills` (`user_id`, `type`, `excludeBudget`, `billTime`, `category_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_period_lookup` "
                    + "ON `budgets` (`owner_id`, `transaction_type`, `budget_type`, `start_time`, `target_type`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_legacy_period_lookup` "
                    + "ON `budgets` (`owner_id`, `transaction_type`, `budget_type`, `year`, `month`, `target_type`)");
        }
    };
    static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_user_time_id_state` "
                    + "ON `bills` (`user_id`, `billTime`, `id`, `sync_state`)");
        }
    };
    static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `bills` ADD COLUMN `discount` REAL NOT NULL DEFAULT 0.0");
        }
    };
    static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `bills` ADD COLUMN `exclude_stat` INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `tag_groups` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`object_id` TEXT, " +
                    "`user_id` TEXT, " +
                    "`name` TEXT, " +
                    "`sort_order` INTEGER NOT NULL, " +
                    "`is_default` INTEGER NOT NULL, " +
                    "`is_folded` INTEGER NOT NULL, " +
                    "`is_deleted` INTEGER NOT NULL, " +
                    "`created_at` INTEGER, " +
                    "`updated_at` INTEGER, " +
                    "`sync_state` INTEGER)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `tags` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`object_id` TEXT, " +
                    "`user_id` TEXT, " +
                    "`group_id` INTEGER NOT NULL, " +
                    "`name` TEXT, " +
                    "`bg_color` TEXT, " +
                    "`text_color` TEXT, " +
                    "`sort_order` INTEGER NOT NULL, " +
                    "`is_archived` INTEGER NOT NULL, " +
                    "`is_deleted` INTEGER NOT NULL, " +
                    "`created_at` INTEGER, " +
                    "`updated_at` INTEGER, " +
                    "`sync_state` INTEGER)");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_user_id_group_id` ON `tags` (`user_id`, `group_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_user_id_is_archived` ON `tags` (`user_id`, `is_archived`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `bill_tag_cross_ref` (" +
                    "`bill_id` INTEGER NOT NULL, " +
                    "`tag_id` INTEGER NOT NULL, " +
                    "`user_id` TEXT, " +
                    "`tag_name` TEXT, " +
                    "`tag_bg_color` TEXT, " +
                    "`tag_text_color` TEXT, " +
                    "`is_deleted` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER, " +
                    "`sync_state` INTEGER, " +
                    "PRIMARY KEY(`bill_id`, `tag_id`))");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_tag_cross_ref_bill_id` ON `bill_tag_cross_ref` (`bill_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_tag_cross_ref_tag_id` ON `bill_tag_cross_ref` (`tag_id`)");
        }
    };

    public abstract CategoryDao categoryDao();
    public abstract SubCategoryDao subCategoryDao();
    public abstract AccountDao accountDao();
    public abstract BillDao billDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract UserProfileDao userProfileDao();
    public abstract BudgetDao budgetDao();
    public abstract WishDao wishDao();
    public abstract SavingPlanDao savingPlanDao();
    public abstract DownloadDao downloadDao();
    public abstract com.example.my_project1.data.dao.TagGroupDao tagGroupDao();
    public abstract com.example.my_project1.data.dao.TagDao tagDao();
    public abstract com.example.my_project1.data.dao.BillTagCrossRefDao billTagCrossRefDao();


    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "accounting_app_db"
                    )
                            .addMigrations(MIGRATION_27_28, MIGRATION_28_29, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_44_45)
                            .fallbackToDestructiveMigration() // 调试阶段允许重建数据库
                            .build();
                }
            }
        }
        return INSTANCE;
    }


}
