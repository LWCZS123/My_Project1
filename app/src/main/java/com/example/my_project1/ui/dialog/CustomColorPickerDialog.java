package com.example.my_project1.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.my_project1.R;
import com.example.my_project1.ui.view.ColorWheelView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class CustomColorPickerDialog extends DialogFragment {

    public interface OnColorSelectedListener {
        void onColorSelected(String hexColor);
    }

    private OnColorSelectedListener listener;
    private String initialHexColor = "#FF9800";

    private float currentHue = 30f;
    private float currentSat = 1.0f;
    private float currentVal = 1.0f;

    public static CustomColorPickerDialog newInstance(String hexColor) {
        CustomColorPickerDialog fragment = new CustomColorPickerDialog();
        Bundle args = new Bundle();
        args.putString("arg_hex_color", hexColor);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().getString("arg_hex_color") != null) {
            initialHexColor = getArguments().getString("arg_hex_color");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_custom_color_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ColorWheelView colorWheelView = view.findViewById(R.id.color_wheel_view);
        MaterialCardView cardPreview = view.findViewById(R.id.card_color_preview);
        SeekBar seekBarBrightness = view.findViewById(R.id.seekBar_brightness);
        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm);

        parseInitialColor();

        colorWheelView.setColor(currentHue, currentSat);
        seekBarBrightness.setProgress((int) (currentVal * 100));
        updatePreview(cardPreview);

        colorWheelView.setOnColorChangeListener((hue, saturation) -> {
            currentHue = hue;
            currentSat = saturation;
            updatePreview(cardPreview);
        });

        seekBarBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentVal = progress / 100f;
                updatePreview(cardPreview);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnCancel.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                listener.onColorSelected(getCurrentHexColor());
            }
            dismiss();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private void parseInitialColor() {
        try {
            int color = Color.parseColor(initialHexColor);
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            currentHue = hsv[0];
            currentSat = hsv[1];
            currentVal = hsv[2];
        } catch (Exception e) {
            currentHue = 30f;
            currentSat = 1.0f;
            currentVal = 1.0f;
        }
    }

    private void updatePreview(MaterialCardView cardPreview) {
        int color = Color.HSVToColor(new float[]{currentHue, currentSat, currentVal});
        cardPreview.setCardBackgroundColor(color);
    }

    private String getCurrentHexColor() {
        int color = Color.HSVToColor(new float[]{currentHue, currentSat, currentVal});
        return String.format("#%06X", (0xFFFFFF & color));
    }
}
