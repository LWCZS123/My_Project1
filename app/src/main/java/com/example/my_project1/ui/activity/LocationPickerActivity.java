package com.example.my_project1.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.my_project1.databinding.ActivityLocationPickerBinding;
import com.example.my_project1.ui.adapter.location.LocationAdapter;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.NonNull;

public class LocationPickerActivity extends AppCompatActivity
        implements LocationSource, AMapLocationListener,
        GeocodeSearch.OnGeocodeSearchListener, PoiSearch.OnPoiSearchListener {

    private static final String TAG = "LocationPicker";
    private ActivityLocationPickerBinding binding;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private OnLocationChangedListener locationChangedListener;
    private GeocodeSearch geocodeSearch;
    private PoiSearch.Query poiQuery;

    private Marker centerMarker;
    private LocationAdapter locationAdapter;
    private List<PoiItem> poiItems = new ArrayList<>();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int PAGE_SIZE = 30;
    private static final int SEARCH_DELAY = 500;

    private LatLng currentLatLng; // 用户当前真实位置
    private LatLng mapCenterLatLng; // 地图中心位置
    private String currentCity;
    private String currentProvince;
    private boolean isUserDragging = false;
    private boolean isFirstLocation = true;
    private boolean isLocationStarted = false; // 标记定位是否已启动

    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Runnable poiSearchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {



        super.onCreate(savedInstanceState);
        binding = ActivityLocationPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            v.setPadding(0, top, 0, 0);

            return insets;
        });

        // 设置状态栏图标为深色（因为背景是浅色 #F0F4FF）
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        initMap(savedInstanceState);
        initViews();

        // 延迟500ms后检查权限并启动定位
        mainHandler.postDelayed(() -> {
            checkPermissionAndLocation();
        }, 500);
    }

    private void initMap(Bundle savedInstanceState) {
        binding.mapView.onCreate(savedInstanceState);
        aMap = binding.mapView.getMap();

        // 性能优化
        aMap.showBuildings(false);
        aMap.showIndoorMap(false);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);

        // 定位蓝点样式
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        myLocationStyle.showMyLocation(true);
        myLocationStyle.interval(2000);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setLocationSource(this);
        aMap.setMyLocationEnabled(true);

        // 地图UI
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setCompassEnabled(false);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.getUiSettings().setMyLocationButtonEnabled(false);

        // 中心标记
        centerMarker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .draggable(false));
        centerMarker.setVisible(false);

        // 地图移动监听
        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                isUserDragging = true;
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void onCameraChangeFinish(CameraPosition cameraPosition) {
                if (isUserDragging) {
                    mapCenterLatLng = cameraPosition.target;
                    updateCenterMarker();

                    // 延迟搜索附近POI
                    searchRunnable = () -> {
                        searchNearbyPoi(mapCenterLatLng);
                        isUserDragging = false;
                    };
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
                }
            }
        });

        // 初始化地理编码
        try {
            geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(this);
        } catch (Exception e) {
            Log.e(TAG, "GeocodeSearch初始化失败: " + e.getMessage());
        }
    }

    private void initViews() {
        binding.ivBack.setOnClickListener(v -> finish());

        // RecyclerView设置
        locationAdapter = new LocationAdapter(this, poiItems);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(locationAdapter);
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setItemViewCacheSize(20);

        // POI点击事件
        locationAdapter.setOnItemClickListener(poiItem -> {
            Intent intent = new Intent();
            intent.putExtra("location_name", poiItem.getTitle());
            intent.putExtra("location_address", poiItem.getSnippet());
            intent.putExtra("location_lat", poiItem.getLatLonPoint().getLatitude());
            intent.putExtra("location_lng", poiItem.getLatLonPoint().getLongitude());
            setResult(RESULT_OK, intent);
            finish();
        });

        // 定位按钮：始终定位到用户当前位置
        binding.btnLocation.setOnClickListener(v -> {
            if (currentLatLng != null) {
                // 移动到用户当前位置
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
                mapCenterLatLng = currentLatLng;
                updateCenterMarker();
                searchNearbyPoi(currentLatLng);
            } else {
                // 重新定位
                Toast.makeText(this, "正在定位...", Toast.LENGTH_SHORT).show();
                if (!isLocationStarted) {
                    startLocation();
                }
            }
        });

        // 搜索框
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (poiSearchRunnable != null) {
                    searchHandler.removeCallbacks(poiSearchRunnable);
                }

                if (s.length() > 0) {
                    poiSearchRunnable = () -> searchPoiByKeyword(s.toString());
                    searchHandler.postDelayed(poiSearchRunnable, SEARCH_DELAY);
                } else {
                    // 输入为空时，显示地图中心附近POI
                    if (mapCenterLatLng != null) {
                        searchNearbyPoi(mapCenterLatLng);
                    }
                }
            }
        });

        // 搜索按钮
        binding.ivSearch.setOnClickListener(v -> {
            String keyword = binding.etSearch.getText().toString().trim();
            if (!keyword.isEmpty()) {
                searchPoiByKeyword(keyword);
            }
        });
    }

    private void checkPermissionAndLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocation();
        }
    }

    private void startLocation() {
        if (isLocationStarted) {
            Log.d(TAG, "定位已经启动，跳过");
            return;
        }

        try {
            Log.d(TAG, "开始初始化定位...");

            // 先清理旧的定位客户端
            if (locationClient != null) {
                locationClient.stopLocation();
                locationClient.onDestroy();
                locationClient = null;
            }

            // 创建定位客户端
            locationClient = new AMapLocationClient(getApplicationContext());

            // 配置定位参数
            AMapLocationClientOption option = new AMapLocationClientOption();

            // 使用高精度定位模式
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);

            // 单次定位
            option.setOnceLocation(true);
            option.setOnceLocationLatest(true);

            // 需要地址信息
            option.setNeedAddress(true);

            // 超时时间
            option.setHttpTimeOut(30000);

            // 禁用缓存
            option.setLocationCacheEnable(false);

            // 模拟器设置
            option.setGpsFirst(false);
            option.setMockEnable(true);

            // 定位场景
            option.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.SignIn);

            // 设置定位监听
            locationClient.setLocationListener(this);

            // 设置定位参数
            locationClient.setLocationOption(option);

            // 延迟启动定位
            mainHandler.postDelayed(() -> {
                if (locationClient != null) {
                    locationClient.startLocation();
                    isLocationStarted = true;
                    Log.d(TAG, "定位客户端已启动");
                }
            }, 300);

        } catch (Exception e) {
            Log.e(TAG, "定位初始化失败: " + e.getMessage(), e);
            Toast.makeText(this, "定位初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isLocationStarted = false;
        }
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        Log.d(TAG, "=== 定位回调 ===");

        if (aMapLocation == null) {
            Log.e(TAG, "定位对象为null");
            Toast.makeText(this, "定位失败", Toast.LENGTH_SHORT).show();
            return;
        }

        int errorCode = aMapLocation.getErrorCode();
        Log.d(TAG, "错误码: " + errorCode);
        Log.d(TAG, "错误信息: " + aMapLocation.getErrorInfo());

        if (errorCode == 0) {
            // 定位成功
            double lat = aMapLocation.getLatitude();
            double lng = aMapLocation.getLongitude();

            Log.d(TAG, "✅ 定位成功");
            Log.d(TAG, "经度: " + lng);
            Log.d(TAG, "纬度: " + lat);
            Log.d(TAG, "精度: " + aMapLocation.getAccuracy() + "米");
            Log.d(TAG, "城市: " + aMapLocation.getCity());
            Log.d(TAG, "省份: " + aMapLocation.getProvince());
            Log.d(TAG, "地址: " + aMapLocation.getAddress());

            if (locationChangedListener != null) {
                locationChangedListener.onLocationChanged(aMapLocation);
            }

            // 保存用户真实位置
            currentLatLng = new LatLng(lat, lng);
            currentCity = aMapLocation.getCity();
            currentProvince = aMapLocation.getProvince();

            // 更新城市显示
            String cityText = currentCity != null ? currentCity : "未知城市";
            binding.tvCity.setText(cityText);

            // 只在首次定位时移动地图
            if (isFirstLocation) {
                isFirstLocation = false;
                mapCenterLatLng = currentLatLng;
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
                updateCenterMarker();
                searchNearbyPoi(currentLatLng);

                Toast.makeText(this, "定位成功", Toast.LENGTH_SHORT).show();
            }

        } else {
            // 定位失败
            Log.e(TAG, "❌ 定位失败");
            Log.e(TAG, "错误码: " + errorCode);
            Log.e(TAG, "错误信息: " + aMapLocation.getErrorInfo());

            // 针对常见错误码的处理
            String errorMsg;
            switch (errorCode) {
                case 4:
                    errorMsg = "网络异常，请检查网络连接";
                    break;
                case 6:
                    errorMsg = "定位服务错误";
                    // 模拟器上使用测试位置（厦门）
                    useFallbackLocation();
                    return;
                case 12:
                    errorMsg = "缺少定位权限";
                    break;
                default:
                    errorMsg = "定位失败(" + errorCode + ")";
                    break;
            }

            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();

            // 如果是模拟器环境，使用默认位置
            if (errorCode == 6 || errorCode == 4) {
                useFallbackLocation();
            }
        }
    }

    /**
     * 使用备用位置（厦门）
     */
    private void useFallbackLocation() {
        Log.w(TAG, "使用备用位置（厦门）");

        currentLatLng = new LatLng(24.479834, 118.089425);
        currentCity = "厦门市";
        currentProvince = "福建省";

        binding.tvCity.setText(currentCity);

        if (isFirstLocation) {
            isFirstLocation = false;
            mapCenterLatLng = currentLatLng;
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17));
            updateCenterMarker();
            searchNearbyPoi(currentLatLng);

            Toast.makeText(this, "模拟器定位失败，使用默认位置（厦门）", Toast.LENGTH_LONG).show();
        }
    }

    private void updateCenterMarker() {
        if (centerMarker != null && mapCenterLatLng != null) {
            centerMarker.setPosition(mapCenterLatLng);
            centerMarker.setVisible(true);
        }
    }

    /**
     * 搜索指定位置附近的POI
     */
    private void searchNearbyPoi(LatLng latLng) {
        if (latLng == null) {
            Log.w(TAG, "搜索位置为空");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        LatLonPoint latLonPoint = new LatLonPoint(latLng.latitude, latLng.longitude);

        // 逆地理编码
        RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 1000, GeocodeSearch.AMAP);
        geocodeSearch.getFromLocationAsyn(query);

        // 搜索周边POI
        poiQuery = new PoiSearch.Query("", "", "");
        poiQuery.setPageSize(PAGE_SIZE);
        poiQuery.setPageNum(0);

        try {
            PoiSearch poiSearch = new PoiSearch(this, poiQuery);
            poiSearch.setBound(new PoiSearch.SearchBound(latLonPoint, 2000));
            poiSearch.setOnPoiSearchListener(this);
            poiSearch.searchPOIAsyn();

            Log.d(TAG, "搜索附近POI - 位置: " + latLng.latitude + ", " + latLng.longitude);
        } catch (AMapException e) {
            Log.e(TAG, "POI搜索失败: " + e.getMessage(), e);
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "搜索初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 根据关键词搜索POI
     */
    private void searchPoiByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        String searchCity = currentCity != null ? currentCity : "";

        poiQuery = new PoiSearch.Query(keyword, "", searchCity);
        poiQuery.setPageSize(PAGE_SIZE);
        poiQuery.setPageNum(0);

        try {
            PoiSearch poiSearch = new PoiSearch(this, poiQuery);

            if (currentLatLng != null) {
                poiSearch.setBound(new PoiSearch.SearchBound(
                        new LatLonPoint(currentLatLng.latitude, currentLatLng.longitude),
                        50000));
            }

            poiSearch.setOnPoiSearchListener(this);
            poiSearch.searchPOIAsyn();

            Log.d(TAG, "搜索关键词: " + keyword + ", 城市: " + searchCity);
        } catch (AMapException e) {
            Log.e(TAG, "关键词搜索失败: " + e.getMessage(), e);
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "搜索初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPoiSearched(PoiResult poiResult, int errorCode) {
        binding.progressBar.setVisibility(View.GONE);

        Log.d(TAG, "POI搜索结果 - errorCode: " + errorCode +
                ", resultCount: " + (poiResult != null && poiResult.getPois() != null ?
                poiResult.getPois().size() : 0));

        if (errorCode == 1000) {
            if (poiResult != null && poiResult.getQuery() != null) {
                poiItems.clear();

                List<PoiItem> pois = poiResult.getPois();
                if (pois != null && !pois.isEmpty()) {
                    poiItems.addAll(pois);
                    locationAdapter.notifyDataSetChanged();
                    Log.d(TAG, "搜索到 " + pois.size() + " 个POI");
                } else {
                    Toast.makeText(this, "未找到相关地点", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.e(TAG, "搜索失败 - errorCode: " + errorCode);
            Toast.makeText(this, "搜索失败: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPoiItemSearched(PoiItem poiItem, int errorCode) {
        // 不需要实现
    }

    @Override
    public void onRegeocodeSearched(RegeocodeResult regeocodeResult, int errorCode) {
        if (errorCode == 1000 && regeocodeResult != null) {
            String address = regeocodeResult.getRegeocodeAddress().getFormatAddress();
            Log.d(TAG, "逆地理编码成功: " + address);
        }
    }

    @Override
    public void onGeocodeSearched(GeocodeResult geocodeResult, int errorCode) {
        // 不需要实现
    }

    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        locationChangedListener = onLocationChangedListener;
        if (locationClient != null && !isLocationStarted) {
            startLocation();
        }
    }

    @Override
    public void deactivate() {
        locationChangedListener = null;
        if (locationClient != null) {
            locationClient.stopLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "定位权限已授予");
                startLocation();
            } else {
                Log.e(TAG, "定位权限被拒绝");
                Toast.makeText(this, "需要位置权限才能使用定位功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && binding.mapView != null) {
            binding.mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (binding != null && binding.mapView != null) {
            binding.mapView.onPause();
        }
        // 提前断开定位回调，防止 onPause 后定位线程仍向地图写入数据
        locationChangedListener = null;
        if (aMap != null) {
            aMap.setMyLocationEnabled(false);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 🔴 Activity 进入后台时停止定位，避免定位回调在销毁阶段触发 GL 操作
        if (locationClient != null && isLocationStarted) {
            locationClient.stopLocation();
            isLocationStarted = false;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null && binding.mapView != null) {
            binding.mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Activity销毁，清理资源");

        // 第一步：停所有异步回调
        if (searchHandler != null) {
            searchHandler.removeCallbacksAndMessages(null);
            searchHandler = null;
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }

        // 第二步：停定位客户端
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
            locationClient = null;
        }

        // 第三步：清理 aMap 监听器引用
        // 必须在 mapView.onDestroy() 之前断开，防止渲染线程销毁期间回调空悬引用
        if (aMap != null) {
            try {
                aMap.setMyLocationEnabled(false);
                aMap.setLocationSource(null);
                aMap.setOnCameraChangeListener(null);
                if (centerMarker != null) {
                    centerMarker.destroy();
                    centerMarker = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "清理 aMap 监听器异常: " + e.getMessage());
            }
            aMap = null;
        }

        // 第四步：销毁 TextureMapView
        // TextureMapView 使用 TextureView 渲染，不依赖 GLSurfaceView，
        // 从根本上规避了 GLThread "Pointer tag truncated" SIGABRT 崩溃。
        // 无需 try-catch，直接调用即可。
        if (binding != null && binding.mapView != null) {
            binding.mapView.onDestroy();
        }

        geocodeSearch = null;
        locationAdapter = null;
        if (poiItems != null) {
            poiItems.clear();
        }
        isLocationStarted = false;

        super.onDestroy();
    }
}