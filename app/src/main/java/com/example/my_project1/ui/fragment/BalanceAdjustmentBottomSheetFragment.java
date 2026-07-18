package com.example.my_project1.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.my_project1.R;
import com.example.my_project1.data.model.account.Account;
import com.example.my_project1.databinding.LayoutBalanceAdjustmentBottomSheetBinding;
import com.example.my_project1.utils.SnackbarUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class BalanceAdjustmentBottomSheetFragment extends BottomSheetDialogFragment {

    public interface OnBalanceAdjustedListener {
        void onAdjusted(double newBalance, boolean recordAsTransaction);
    }

    private LayoutBalanceAdjustmentBottomSheetBinding binding;
    private Account account;
    private OnBalanceAdjustedListener listener;

    private StringBuilder currentInput = new StringBuilder();
    private String fullExpression = "";
    private String pendingOperator = null;
    private String lastConfirmedOperator = null;
    private boolean isCalculating = false;

    public static BalanceAdjustmentBottomSheetFragment newInstance(Account account) {
        BalanceAdjustmentBottomSheetFragment fragment = new BalanceAdjustmentBottomSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable("account", account);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnBalanceAdjustedListener(OnBalanceAdjustedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            account = (Account) getArguments().getSerializable("account");
        }
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_bottom_sheet1));
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutBalanceAdjustmentBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (account != null) {
            binding.tvTitle.setText(account.isCredit() ? "欠款调整" : "余额调整");
            currentInput.append(String.format(Locale.US, "%.2f", Math.abs(account.getBalance())));
            if (currentInput.toString().endsWith(".00")) {
                currentInput.setLength(currentInput.length() - 3);
            }
            updateAmountDisplay();
            
            // 如果是新账户（无ID），隐藏“记为交易”复选框
            if (account.getObjectId() == null && account.getId() <= 0) {
                binding.cbRecordTransaction.setVisibility(View.GONE);
            }
        }

        setupKeyboard();

        binding.btnClose.setOnClickListener(v -> dismiss());
        binding.btnClear.setOnClickListener(v -> {
            resetCalculation();
        });
    }

    private void setupKeyboard() {
        // 数字键
        binding.getRoot().findViewById(R.id.btn_0).setOnClickListener(v -> onNumberClick("0"));
        binding.getRoot().findViewById(R.id.btn_1).setOnClickListener(v -> onNumberClick("1"));
        binding.getRoot().findViewById(R.id.btn_2).setOnClickListener(v -> onNumberClick("2"));
        binding.getRoot().findViewById(R.id.btn_3).setOnClickListener(v -> onNumberClick("3"));
        binding.getRoot().findViewById(R.id.btn_4).setOnClickListener(v -> onNumberClick("4"));
        binding.getRoot().findViewById(R.id.btn_5).setOnClickListener(v -> onNumberClick("5"));
        binding.getRoot().findViewById(R.id.btn_6).setOnClickListener(v -> onNumberClick("6"));
        binding.getRoot().findViewById(R.id.btn_7).setOnClickListener(v -> onNumberClick("7"));
        binding.getRoot().findViewById(R.id.btn_8).setOnClickListener(v -> onNumberClick("8"));
        binding.getRoot().findViewById(R.id.btn_9).setOnClickListener(v -> onNumberClick("9"));

        // 功能键
        binding.getRoot().findViewById(R.id.btn_dot).setOnClickListener(v -> onDotClick());
        binding.getRoot().findViewById(R.id.btn_backspace).setOnClickListener(v -> onDeleteClick());
        binding.getRoot().findViewById(R.id.btn_plus_multiply).setOnClickListener(v -> onOperatorClick("+", "×"));
        binding.getRoot().findViewById(R.id.btn_minus_divide).setOnClickListener(v -> onOperatorClick("-", "÷"));

        // 完成/等于按钮
        binding.getRoot().findViewById(R.id.btn_done).setOnClickListener(v -> {
            if (isCalculating) {
                onEqualsClick();
            } else {
                onCompleteClick();
            }
        });
    }

    private void onNumberClick(String number) {
        if (pendingOperator != null) confirmOperator();
        if (currentInput.length() < 12) {
            if (currentInput.toString().equals("0")) {
                currentInput = new StringBuilder(number);
            } else {
                currentInput.append(number);
            }
            updateAmountDisplay();
        }
    }

    private void onDotClick() {
        if (pendingOperator != null) confirmOperator();
        if (currentInput.length() == 0) currentInput.append("0.");
        else if (!currentInput.toString().contains(".")) currentInput.append(".");
        updateAmountDisplay();
    }

    private void onDeleteClick() {
        if (isCalculating && currentInput.length() == 0 && pendingOperator == null) {
            resetCalculation();
        } else if (currentInput.length() > 0) {
            currentInput.deleteCharAt(currentInput.length() - 1);
            updateAmountDisplay();
        } else if (pendingOperator != null) {
            pendingOperator = null;
            isCalculating = !fullExpression.isEmpty();
            updateAmountDisplay();
        }
    }

    private void onOperatorClick(String op1, String op2) {
        String nextOperator;
        String buttonId = op1 + "/" + op2;
        
        // 逻辑：如果当前已有操作符且还没有输入第二个数，直接切换操作符
        if (pendingOperator != null) {
            if (lastConfirmedOperator != null && lastConfirmedOperator.startsWith(buttonId)) {
                String currentOp = lastConfirmedOperator.split(":")[1];
                nextOperator = currentOp.equals(op1) ? op2 : op1;
            } else {
                nextOperator = op1;
            }
        } else {
            // 第一次点击操作符
            nextOperator = op1;
        }

        lastConfirmedOperator = buttonId + ":" + nextOperator;
        pendingOperator = nextOperator;
        isCalculating = true;
        updateAmountDisplay();
    }

    private void confirmOperator() {
        if (pendingOperator == null) return;
        String inputValue = currentInput.length() > 0 ? currentInput.toString() : "0";
        if (fullExpression.isEmpty()) {
            fullExpression = inputValue + pendingOperator;
        } else {
            fullExpression = fullExpression + inputValue + pendingOperator;
        }
        currentInput = new StringBuilder();
        pendingOperator = null;
    }

    private void onEqualsClick() {
        if (!isCalculating) return;
        
        String inputValue = currentInput.length() > 0 ? currentInput.toString() : "0";
        String completeExpression = fullExpression + inputValue;
        
        try {
            double result = calculateExpression(completeExpression);
            String resultStr = String.format(Locale.US, "%.2f", result);
            if (resultStr.endsWith(".00")) resultStr = resultStr.substring(0, resultStr.length() - 3);
            currentInput = new StringBuilder(resultStr);
            fullExpression = "";
            pendingOperator = null;
            isCalculating = false;
            lastConfirmedOperator = null;
            updateAmountDisplay();
        } catch (Exception e) {
            SnackbarUtils.showError(binding.getRoot(), "计算错误");
            resetCalculation();
        }
    }

    private void onCompleteClick() {
        if (listener != null) {
            double newBalance = 0;
            try {
                String amountStr = currentInput.toString();
                if (amountStr.isEmpty()) amountStr = "0";
                newBalance = Double.parseDouble(amountStr);
                if (account != null && account.isCredit()) {
                    newBalance = -newBalance;
                }
            } catch (NumberFormatException ignored) {}
            listener.onAdjusted(newBalance, binding.cbRecordTransaction.isChecked());
        }
        dismiss();
    }

    private double calculateExpression(String expression) throws Exception {
        expression = expression.replace("×", "*").replace("÷", "/");
        String[] tokens = expression.split("(?=[+\\-*/])|(?<=[+\\-*/])");
        if (tokens.length == 0) return 0;
        double result = Double.parseDouble(tokens[0]);
        for (int i = 1; i < tokens.length; i += 2) {
            String operator = tokens[i];
            if (i + 1 >= tokens.length) break;
            double operand = Double.parseDouble(tokens[i + 1]);
            switch (operator) {
                case "+": result += operand; break;
                case "-": result -= operand; break;
                case "*": result *= operand; break;
                case "/": if (operand == 0) throw new ArithmeticException(); result /= operand; break;
            }
        }
        return result;
    }

    private void resetCalculation() {
        currentInput = new StringBuilder();
        fullExpression = "";
        pendingOperator = null;
        isCalculating = false;
        lastConfirmedOperator = null;
        updateAmountDisplay();
    }

    private void updateAmountDisplay() {
        String display;
        if (isCalculating) {
            display = fullExpression + currentInput.toString() + (pendingOperator != null ? pendingOperator : "");
        } else {
            display = currentInput.length() == 0 ? "0.00" : currentInput.toString();
        }
        binding.tvAmount.setText(display);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
