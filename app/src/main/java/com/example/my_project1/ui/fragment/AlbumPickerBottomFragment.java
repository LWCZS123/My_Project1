package com.example.my_project1.ui.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.my_project1.R;
import com.example.my_project1.databinding.FragmentAlbumPickerBottomBinding;
import com.example.my_project1.ui.adapter.photo.PhotoAdapterForUpload;
import com.example.my_project1.ui.dialog.ImagePreviewDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.annotations.NonNull;

/**
 * AlbumPickerBottomFragment - 更新版
 * ✅ 使用 PhotoAdapterForUpload 处理本地 Uri
 * ✅ 用于添加账单时选择/拍照图片
 */
public class AlbumPickerBottomFragment extends BottomSheetDialogFragment {

    private FragmentAlbumPickerBottomBinding binding;
    private PhotoAdapterForUpload photoAdapter;  // 🔑 使用上传专用适配器
    private List<Uri> selectedPhotos = new ArrayList<>();
    private Uri currentPhotoUri;
    private OnPhotosSelectedListener listener;
    private int maxImageCount = 6;

    // ActivityResultLaunchers
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    public interface OnPhotosSelectedListener {
        void onPhotosSelected(List<Uri> photos);
    }

    public static AlbumPickerBottomFragment newInstance(List<Uri> currentPhotos, int maxCount) {
        AlbumPickerBottomFragment fragment = new AlbumPickerBottomFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList("current_photos", new ArrayList<>(currentPhotos));
        args.putInt("max_count", maxCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            ArrayList<Uri> photos = getArguments().getParcelableArrayList("current_photos");
            if (photos != null) {
                selectedPhotos.addAll(photos);
            }
            maxImageCount = getArguments().getInt("max_count", 6);
        }

        setupActivityResultLaunchers();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAlbumPickerBottomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupButtons();
        updatePhotoCount();
    }

    private void setupActivityResultLaunchers() {
        // 相册选择
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handleGalleryResult(result.getData());
                    }
                });

        // 相机拍照
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        handleCameraResult();
                    }
                });

        // 相机权限请求
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchCamera();
                    } else {
                        Toast.makeText(requireContext(), "需要相机权限才能拍照",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupRecyclerView() {
        // 🔑 使用 PhotoAdapterForUpload（处理本地 Uri）
        photoAdapter = new PhotoAdapterForUpload(requireContext(), selectedPhotos,
                new PhotoAdapterForUpload.OnPhotoClickListener() {
                    @Override
                    public void onPhotoClick(Uri uri, int position) {
                        showImagePreview(uri, position);
                    }

                    @Override
                    public void onDeleteClick(int position) {
                        photoAdapter.removePhoto(position);
                        updatePhotoCount();
                    }
                });

        binding.rvPhotos.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvPhotos.setAdapter(photoAdapter);
    }

    private void setupButtons() {
        // 确认按钮
        binding.btnConfirm.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPhotosSelected(new ArrayList<>(selectedPhotos));
            }
            dismiss();
        });

        // 相册按钮
        binding.cardAlbum.setOnClickListener(v -> openGallery());

        // 拍照按钮
        binding.cardCamera.setOnClickListener(v -> openCamera());
    }

    private void openGallery() {
        int remainingSlots = maxImageCount - selectedPhotos.size();
        if (remainingSlots <= 0) {
            Toast.makeText(requireContext(),
                    "最多只能选择" + maxImageCount + "张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        // 支持多选
        if (remainingSlots > 1) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        galleryLauncher.launch(intent);
    }

    private void openCamera() {
        if (selectedPhotos.size() >= maxImageCount) {
            Toast.makeText(requireContext(),
                    "最多只能添加" + maxImageCount + "张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查相机权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        launchCamera();
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(intent);
            }
        }
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir("Pictures");

        try {
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "创建图片文件失败", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void handleGalleryResult(Intent data) {
        List<Uri> newPhotos = new ArrayList<>();
        int remainingSlots = maxImageCount - selectedPhotos.size();

        if (data.getClipData() != null) {
            // 多选
            int count = data.getClipData().getItemCount();
            int imagesToAdd = Math.min(count, remainingSlots);

            for (int i = 0; i < imagesToAdd; i++) {
                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                newPhotos.add(imageUri);
            }

            if (count > remainingSlots) {
                Toast.makeText(requireContext(),
                        "最多只能选择" + maxImageCount + "张图片", Toast.LENGTH_SHORT).show();
            }
        } else if (data.getData() != null) {
            // 单选
            if (remainingSlots > 0) {
                newPhotos.add(data.getData());
            }
        }

        if (!newPhotos.isEmpty()) {
            photoAdapter.addPhotos(newPhotos);
            updatePhotoCount();
        }
    }

    private void handleCameraResult() {
        if (currentPhotoUri != null) {
            photoAdapter.addPhoto(currentPhotoUri);
            updatePhotoCount();
            Toast.makeText(requireContext(), "照片已添加", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePhotoCount() {
        int count = selectedPhotos.size();
        binding.btnConfirm.setText("确认(" + count + "/" + maxImageCount + ")");

        // 如果没有图片，显示提示
        if (count == 0) {
            binding.rvPhotos.setVisibility(View.GONE);
        } else {
            binding.rvPhotos.setVisibility(View.VISIBLE);
        }
    }

    private void showImagePreview(Uri uri, int position) {
        // 使用 ImagePreviewDialog 预览图片
        ImagePreviewDialog dialog = new ImagePreviewDialog(
                requireContext(), selectedPhotos, position);
        dialog.show();
    }

    public void setOnPhotosSelectedListener(OnPhotosSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // 设置上圆角背景
                bottomSheet.setBackground(
                        ContextCompat.getDrawable(requireContext(),
                                R.drawable.bg_bottom_sheet1)
                );

                // 默认展开 BottomSheet
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        return bottomSheetDialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}