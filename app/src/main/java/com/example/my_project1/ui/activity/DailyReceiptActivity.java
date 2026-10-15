package com.example.my_project1.ui.activity;

import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.data.model.bill.Bill;
import com.example.my_project1.data.repository.bill.BillRepository;
import com.example.my_project1.data.repository.user.UserProfileRepository;
import com.example.my_project1.databinding.ActivityDailyReceiptBinding;
import com.example.my_project1.ui.adapter.bill.ReceiptBillAdapter;
import com.example.my_project1.ui.fragment.CustomDateTimePickerFragment;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.bmob.v3.BmobUser;

/**
 * DailyReceiptActivity
 * -----------------------------------------------------------
 * 今日小票 Activity：将用户指定日期的收支账单生成购物小票风格画面，
 * 支持日期切换与导出保存图片到相册，具备小票打印滑出动画。
 */
public class DailyReceiptActivity extends AppCompatActivity {

    private static final String EXTRA_DATE_MILLIS = "extra_date_millis";

    private ActivityDailyReceiptBinding binding;
    private BillRepository billRepository;
    private UserProfileRepository userProfileRepository;
    private ReceiptBillAdapter adapter;

    private String currentUserId;
    private final Calendar selectedCalendar = Calendar.getInstance();
    private LiveData<List<Bill>> currentBillsLiveData;
    private ValueAnimator ledAnimator;
    private ValueAnimator printAnimator;

    private static final String[] POETIC_QUOTES = {
            "晚风轻拂过窗沿\n细碎的声音漫进房间\n那些拢起的暖意\n都在夜色里慢慢沉淀",
            "日落归山海\n山海藏深意\n每一笔平凡碎碎念\n都是生活的温存印记",
            "四季更迭，烟火漫漫\n将琐碎的生活装进口袋\n愿你所念皆如愿\n所行皆坦途",
            "时光静好，微风不燥\n把清晨的光与夜晚的星\n一并收入人生的账单里",
            "平凡的日子里\n藏着不平凡的微光\n用心记录每一刻\n生活自会闪闪发光"
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, DailyReceiptActivity.class);
        context.startActivity(intent);
    }

    public static void start(Context context, long timeMillis) {
        Intent intent = new Intent(context, DailyReceiptActivity.class);
        intent.putExtra(EXTRA_DATE_MILLIS, timeMillis);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDailyReceiptBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 状态栏颜色同步为页面背景色 #E4EEF9，图标设为深色
        getWindow().setStatusBarColor(Color.parseColor("#E4EEF9"));
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        // 动态适配 WindowInsets 系统安全区域，避开顶部状态栏与全面屏底部手势区域
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            View topBar = findViewById(R.id.topBar);
            if (topBar != null) {
                topBar.setPadding(
                        topBar.getPaddingLeft(),
                        systemBars.top,
                        topBar.getPaddingRight(),
                        topBar.getPaddingBottom()
                );
            }

            View btnSaveImage = findViewById(R.id.btnSaveImage);
            if (btnSaveImage != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) btnSaveImage.getLayoutParams();
                lp.bottomMargin = systemBars.bottom + dpToPx(24);
                btnSaveImage.setLayoutParams(lp);
            }

            View scrollView = findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.setPadding(
                        scrollView.getPaddingLeft(),
                        dpToPx(60),
                        scrollView.getPaddingRight(),
                        systemBars.bottom + dpToPx(120)
                );
            }

            return insets;
        });

        billRepository = new BillRepository(getApplication());
        userProfileRepository = UserProfileRepository.getInstance(getApplication());

        long millis = getIntent().getLongExtra(EXTRA_DATE_MILLIS, System.currentTimeMillis());
        selectedCalendar.setTimeInMillis(millis);

        initUserAndHeader();
        initRecyclerView();
        initClickListeners();

        loadReceiptForDate(selectedCalendar);
    }

    private void initUserAndHeader() {
        BmobUser currentUser = BmobUser.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getObjectId();

            // 优先查询用户 Profile 获取用户名
            userProfileRepository.getUserProfile(currentUserId).observe(this, profile -> {
                if (profile != null && profile.getUsername() != null && !profile.getUsername().isEmpty()) {
                    binding.tvOrderUser.setText("下单人：" + profile.getUsername());
                } else if (currentUser.getUsername() != null && !currentUser.getUsername().isEmpty()) {
                    binding.tvOrderUser.setText("下单人：" + currentUser.getUsername());
                } else {
                    binding.tvOrderUser.setText("下单人：小管家");
                }
            });
        } else {
            binding.tvOrderUser.setText("下单人：未登录用户");
        }

        binding.tvCashier.setText("收银员：胖咔");
    }

    private void initRecyclerView() {
        adapter = new ReceiptBillAdapter();
        binding.rvReceiptBills.setLayoutManager(new LinearLayoutManager(this));
        binding.rvReceiptBills.setAdapter(adapter);
    }

    private void initClickListeners() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.btnSelectDate.setOnClickListener(v -> showDatePickerDialog());

        binding.btnSaveImage.setOnClickListener(v -> saveReceiptAsImage());
    }

    private boolean hasPrintedCurrentDate = false;

    private void resetPrinterAndPaperState() {
        stopLedBlinking();

        View layoutPrinter = findViewById(R.id.layoutPrinter);
        View target = binding.llReceiptPaperContainer;
        View btnSaveImage = findViewById(R.id.btnSaveImage);

        if (layoutPrinter != null) {
            layoutPrinter.animate().cancel();
            layoutPrinter.setVisibility(View.VISIBLE);
            layoutPrinter.setAlpha(1.0f);
            layoutPrinter.setTranslationY(0f);
        }

        if (target != null) {
            target.animate().cancel();
            target.setAlpha(0.0f);
            target.setVisibility(View.INVISIBLE);
            target.setTranslationY(0f);
        }

        if (btnSaveImage != null) {
            btnSaveImage.animate().cancel();
            btnSaveImage.setVisibility(View.GONE);
            btnSaveImage.setAlpha(0.0f);
        }
    }

    private void loadReceiptForDate(Calendar calendar) {
        hasPrintedCurrentDate = false;
        resetPrinterAndPaperState();

        SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        String dateStr = dateSdf.format(calendar.getTime());
        binding.tvCurrentDate.setText(dateStr);

        Calendar startCal = (Calendar) calendar.clone();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        Calendar endCal = (Calendar) calendar.clone();
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);

        Date start = startCal.getTime();
        Date end = endCal.getTime();

        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        binding.tvOrderTime.setText("结算时间：" + dateStr + " " + timeSdf.format(new Date()));

        // 依据日期和用户哈希生成唯一的交易号
        long tradeSeed = Math.abs((start.getTime() / 1000) ^ (currentUserId != null ? currentUserId.hashCode() : 12345));
        binding.tvTradeNo.setText("交易号：" + tradeSeed);

        // 随机指定诗意短语（固定对应日期的哈希）
        int quoteIndex = Math.abs(dateStr.hashCode()) % POETIC_QUOTES.length;
        binding.tvPoeticQuote.setText(POETIC_QUOTES[quoteIndex]);

        // 移除旧观察者
        if (currentBillsLiveData != null) {
            currentBillsLiveData.removeObservers(this);
        }

        if (currentUserId == null || currentUserId.isEmpty()) {
            updateUI(Collections.emptyList());
            playPrintAnimation();
            return;
        }

        currentBillsLiveData = billRepository.getBillsInTimeRange(currentUserId, start, end);
        currentBillsLiveData.observe(this, bills -> {
            updateUI(bills);
            if (!hasPrintedCurrentDate) {
                hasPrintedCurrentDate = true;
                playPrintAnimation();
            }
        });
    }

    /**
     * 模拟小票从动漫打印机出纸口缓慢吐出的打印滑出动画
     */
    private void playPrintAnimation() {
        View target = binding.llReceiptPaperContainer;
        View layoutPrinter = findViewById(R.id.layoutPrinter);
        View btnSaveImage = findViewById(R.id.btnSaveImage);
        View scrollView = findViewById(R.id.scrollView);
        if (target == null) return;

        // 1. 隐藏保存图片按钮
        if (btnSaveImage != null) {
            btnSaveImage.animate().cancel();
            btnSaveImage.setVisibility(View.GONE);
            btnSaveImage.setAlpha(0.0f);
        }

        // 2. 立即隐藏小票，彻底防止 Activity 启动或重选日期时小票提前露出/闪烁
        target.animate().cancel();
        target.setAlpha(0.0f);
        target.setVisibility(View.INVISIBLE);

        // 3. 重置并显现打印机
        if (layoutPrinter != null) {
            layoutPrinter.animate().cancel();
            layoutPrinter.setVisibility(View.VISIBLE);
            layoutPrinter.setAlpha(1.0f);
            layoutPrinter.setTranslationY(0f);
        }

        target.post(() -> {
            int targetHeight = target.getHeight();
            if (targetHeight <= 0) targetHeight = target.getMeasuredHeight();
            if (targetHeight <= 0) targetHeight = 1000;

            // 重置 ScrollView 滚动位置到顶部
            if (scrollView != null) {
                scrollView.scrollTo(0, 0);
            }

            // 先将小票平移到出纸槽上方（此时处于隐藏状态，无任何视觉闪烁）
            target.setTranslationY(-targetHeight);
            // 恢复可见度与不透明度，准备开始从打印机内部滑出
            target.setVisibility(View.VISIBLE);
            target.setAlpha(1.0f);

            // 启动指示灯呼吸闪烁效果，模拟打印工作状态
            startLedBlinking();

            // 开启硬件加速图层（withLayer），采用线性匀速插值器（LinearInterpolator），控制整个出纸过程严格匀速 6 秒 (6000ms)
            target.animate()
                    .translationY(0f)
                    .setDuration(4000)
                    .setInterpolator(new LinearInterpolator())
                    .withLayer()
                    .withEndAction(this::onPrintFinished)
                    .start();
        });
    }

    /**
     * 小票完全吐出后的联动动画：打印机滑出淡出隐藏，小票顺畅上移贴合顶部，随后显示保存按钮
     */
    private void onPrintFinished() {
        stopLedBlinking();

        View layoutPrinter = findViewById(R.id.layoutPrinter);
        if (layoutPrinter != null) {
            layoutPrinter.animate()
                    .alpha(0.0f)
                    .translationY(-60f)
                    .setDuration(450)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> layoutPrinter.setVisibility(View.GONE))
                    .start();
        }

        // 小票容器向上平滑归位（translationY = -44dp，顶部距离 topBar 16dp，绝不会遮挡）
        binding.llReceiptPaperContainer.animate()
                .translationY(-44f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::showSaveButton)
                .start();
    }

    private void showSaveButton() {
        View btnSaveImage = findViewById(R.id.btnSaveImage);
        if (btnSaveImage == null) return;
        btnSaveImage.setVisibility(View.VISIBLE);
        btnSaveImage.animate()
                .alpha(1.0f)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startLedBlinking() {
        stopLedBlinking();
        View vLedGlow = findViewById(R.id.vLedGlow);
        View vLedCore = findViewById(R.id.vLedCore);
        if (vLedGlow == null) return;

        ledAnimator = ValueAnimator.ofFloat(1.0f, 0.2f, 1.0f);
        ledAnimator.setDuration(400); // 400ms 快速闪烁
        ledAnimator.setRepeatCount(ValueAnimator.INFINITE);
        ledAnimator.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            vLedGlow.setAlpha(alpha);
            if (vLedCore != null) vLedCore.setAlpha(alpha);
        });
        ledAnimator.start();
    }

    private void stopLedBlinking() {
        if (ledAnimator != null) {
            ledAnimator.cancel();
            ledAnimator = null;
        }
        View vLedGlow = findViewById(R.id.vLedGlow);
        View vLedCore = findViewById(R.id.vLedCore);
        if (vLedGlow != null) vLedGlow.setAlpha(1.0f);
        if (vLedCore != null) vLedCore.setAlpha(1.0f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLedBlinking();
    }

    private void updateUI(List<Bill> bills) {
        if (bills == null || bills.isEmpty()) {
            adapter.setBills(Collections.emptyList());
            binding.rvReceiptBills.setVisibility(View.GONE);
            binding.tvEmptyHint.setVisibility(View.VISIBLE);

            binding.tvRecordCount.setText("0 笔");
            binding.tvIncomeTotal.setText("¥0.00");
            binding.tvExpenseTotal.setText("0.00");
        } else {
            adapter.setBills(bills);
            binding.rvReceiptBills.setVisibility(View.VISIBLE);
            binding.tvEmptyHint.setVisibility(View.GONE);

            int count = bills.size();
            double totalIncome = 0.0;
            double totalExpense = 0.0;

            for (Bill b : bills) {
                if (b.isExcludeStat()) continue;
                if (b.getType() == 1) { // 收入
                    totalIncome += b.getAmount();
                } else if (b.getType() == 0) { // 支出
                    totalExpense += b.getAmount();
                }
            }

            binding.tvRecordCount.setText(String.format(Locale.CHINA, "%d 笔", count));
            binding.tvIncomeTotal.setText(String.format(Locale.CHINA, "¥%.2f", totalIncome));
            binding.tvExpenseTotal.setText(String.format(Locale.CHINA, "%.2f", totalExpense));
        }
    }

    private void showDatePickerDialog() {
        CustomDateTimePickerFragment.show(
                getSupportFragmentManager(),
                selectedCalendar,
                calendar -> {
                    selectedCalendar.setTimeInMillis(calendar.getTimeInMillis());
                    loadReceiptForDate(selectedCalendar);
                }
        );
    }

    private void saveReceiptAsImage() {
        View targetView = binding.llReceiptPaperContainer;
        if (targetView == null || targetView.getWidth() <= 0 || targetView.getHeight() <= 0) {
            Toast.makeText(this, "小票未完全渲染，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(
                targetView.getWidth(),
                targetView.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        targetView.draw(canvas);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
        String fileName = "Receipt_" + sdf.format(new Date()) + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DailyReceipt");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        boolean isSaved = false;
        if (uri != null) {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    isSaved = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
        }

        if (isSaved) {
            Toast.makeText(this, "小票保存成功！已保存至相册", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "保存图片失败，请检查手机存储权限", Toast.LENGTH_SHORT).show();
        }
    }
}
