package com.example.my_project1.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class DownloadPathManager {

    private static final String TAG = "DownloadPathManager";
    private static final String PREF_NAME = "download_settings";
    private static final String KEY_CUSTOM_DIR_URI = "custom_dir_uri";

    private static volatile DownloadPathManager instance;
    private final Context context;

    private DownloadPathManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static DownloadPathManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DownloadPathManager.class) {
                if (instance == null) {
                    instance = new DownloadPathManager(context);
                }
            }
        }
        return instance;
    }

    public void saveCustomDownloadDir(Uri uri) {
        if (uri == null) return;
        try {
            // 申请持久化 URI 访问权限，确保 App 重启后依然可以访问该目录
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Log.e(TAG, "申请持久化权限失败，将以临时权限保存", e);
        }
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_CUSTOM_DIR_URI, uri.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "保存自定义目录字符串失败", e);
        }
    }

    public Uri getCustomDownloadDirUri() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String uriStr = sp.getString(KEY_CUSTOM_DIR_URI, null);
        if (uriStr != null) {
            try {
                return Uri.parse(uriStr);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse saved Uri", e);
            }
        }
        return null;
    }

    public DocumentFile getDownloadRoot() {
        Uri customUri = getCustomDownloadDirUri();
        if (customUri != null) {
            try {
                DocumentFile root = DocumentFile.fromTreeUri(context, customUri);
                if (root != null && root.exists() && root.canWrite()) {
                    return root;
                }
            } catch (Exception e) {
                Log.e(TAG, "自定义目录无效", e);
            }
        }
        
        // 最终兜底方案：使用 App 专用的媒体目录，确保在 Android 11+ 上也能正常写入并被相册扫描
        File mediaDir = null;
        try {
            File[] dirs = context.getExternalMediaDirs();
            if (dirs != null && dirs.length > 0) {
                mediaDir = dirs[0];
            }
        } catch (Exception e) {
            // 忽略
        }

        if (mediaDir == null) {
            mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }

        File projectDir = new File(mediaDir, "My_Project");
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }
        return DocumentFile.fromFile(projectDir);
    }

    public DocumentFile getOrCreateSubDir(String dirName) {
        DocumentFile root = getDownloadRoot();
        if (root == null) return null;
        
        String safeName = sanitizeName(dirName);
        DocumentFile subDir = root.findFile(safeName);
        if (subDir == null || !subDir.isDirectory()) {
            subDir = root.createDirectory(safeName);
        }
        return subDir;
    }

    public DocumentFile createDownloadFile(String subDirName, String fileName, String mimeType) {
        DocumentFile parent;
        if (subDirName != null && !subDirName.isEmpty()) {
            parent = getOrCreateSubDir(subDirName);
        } else {
            parent = getDownloadRoot();
        }

        if (parent == null) return null;

        String safeFileName = sanitizeName(fileName);
        DocumentFile file = parent.findFile(safeFileName);
        if (file == null) {
            file = parent.createFile(mimeType, safeFileName);
        }
        return file;
    }

    public String getDisplayPath() {
        Uri customUri = getCustomDownloadDirUri();
        if (customUri != null) {
            try {
                DocumentFile file = DocumentFile.fromTreeUri(context, customUri);
                if (file != null && file.exists()) {
                    return file.getName() != null ? file.getName() : "自定义目录";
                }
            } catch (Exception e) {
                // ignore
            }
            return "已保存的自定义目录";
        }
        return "默认公共目录 (Android/media/.../My_Project)";
    }

    public void openDownloadFolder(String subDir, String treeUriStr) {
        Uri rootUri = treeUriStr != null ? Uri.parse(treeUriStr) : getCustomDownloadDirUri();
        if (rootUri != null) {
            try {
                Uri targetUri = rootUri;
                if (subDir != null && !subDir.isEmpty()) {
                    DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
                    if (root != null) {
                        DocumentFile sub = root.findFile(sanitizeName(subDir));
                        if (sub != null && sub.isDirectory()) {
                            targetUri = sub.getUri();
                        }
                    }
                }
                
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(targetUri, "resource/folder");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    // 兜底方案：通用打开方式
                    Intent fallback = new Intent(Intent.ACTION_VIEW);
                    fallback.setData(targetUri);
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(fallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "打开文件夹失败", e);
            }
        } else {
            // 默认安全路径兜底
            File mediaDir = null;
            try {
                File[] dirs = context.getExternalMediaDirs();
                if (dirs != null && dirs.length > 0) mediaDir = dirs[0];
            } catch (Exception e) { }
            if (mediaDir == null) mediaDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            
            File defaultDir = new File(mediaDir, "My_Project");
            if (subDir != null && !subDir.isEmpty()) {
                defaultDir = new File(defaultDir, sanitizeName(subDir));
            }
            if (!defaultDir.exists()) defaultDir.mkdirs();
            
            Log.w(TAG, "已打开兜底安全目录");
        }
    }

    public static String sanitizeName(String name) {
        if (name == null) return "unnamed";
        // 过滤非法字符，确保在 Android/Windows 文件系统中路径安全
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
