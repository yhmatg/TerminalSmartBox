package com.android.terminalbox.ui.recognize;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

import com.android.terminalbox.R;
import com.android.terminalbox.app.BaseApplication;
import com.android.terminalbox.base.activity.BaseActivity;
import com.android.terminalbox.contract.RecognizeContract;
import com.android.terminalbox.core.bean.BaseResponse;
import com.android.terminalbox.core.bean.user.UserInfo;
import com.android.terminalbox.faceserver.FaceServer;
import com.android.terminalbox.model.DrawInfo;
import com.android.terminalbox.model.FacePreviewInfo;
import com.android.terminalbox.presenter.RecognizePresenter;
import com.android.terminalbox.ui.unlock.NewUnlockActivity;
import com.android.terminalbox.utils.box.ConfigUtil;
import com.android.terminalbox.utils.box.DrawHelper;
import com.android.terminalbox.utils.camera.CameraHelper;
import com.android.terminalbox.utils.camera.CameraListener;
import com.android.terminalbox.utils.face.FaceHelper;
import com.android.terminalbox.utils.face.FaceListener;
import com.android.terminalbox.utils.face.LivenessType;
import com.android.terminalbox.utils.face.RecognizeColor;
import com.android.terminalbox.utils.face.RequestFeatureStatus;
import com.android.terminalbox.utils.face.RequestLivenessStatus;
import com.android.terminalbox.widget.FaceRectView;
import com.arcsoft.face.AgeInfo;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;
import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.GenderInfo;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.enums.DetectFaceOrientPriority;
import com.arcsoft.face.enums.DetectMode;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class RecognizeActivity extends BaseActivity<RecognizePresenter> implements ViewTreeObserver.OnGlobalLayoutListener, RecognizeContract.View {
    private static final String TAG = "RegisterAndRecognize";
    private static final int MAX_DETECT_NUM = 10;
    /**
     * ???FR??????????????????????????????FR?????????????????????
     */
    private static final int WAIT_LIVENESS_INTERVAL = 100;
    /**
     * ???????????????????????????ms???
     */
    private static final long FAIL_RETRY_INTERVAL = 1000;
    /**
     * ????????????????????????
     */
    private static final int MAX_RETRY_TIME = 3;

    private CameraHelper cameraHelper;
    private DrawHelper drawHelper;
    private Camera.Size previewSize;
    /**
     * ??????????????????????????????????????????????????????RGB??????????????????????????????????????????
     */
    private Integer rgbCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;

    /**
     * VIDEO??????????????????????????????????????????????????????
     */
    private FaceEngine ftEngine;
    /**
     * ???????????????????????????
     */
    private FaceEngine frEngine;
    /**
     * IMAGE????????????????????????????????????????????????????????????
     */
    private FaceEngine flEngine;

    private int ftInitCode = -1;
    private int frInitCode = -1;
    private int flInitCode = -1;
    private FaceHelper faceHelper;
    /**
     * ?????????????????????
     */
    private boolean livenessDetect = true;
    /**
     * ????????????????????????????????????
     */
    private ConcurrentHashMap<Integer, Integer> requestFeatureStatusMap = new ConcurrentHashMap<>();
    /**
     * ????????????????????????????????????????????????
     */
    private ConcurrentHashMap<Integer, Integer> extractErrorRetryMap = new ConcurrentHashMap<>();
    /**
     * ?????????????????????
     */
    private ConcurrentHashMap<Integer, Integer> livenessMap = new ConcurrentHashMap<>();
    /**
     * ??????????????????????????????????????????
     */
    private ConcurrentHashMap<Integer, Integer> livenessErrorRetryMap = new ConcurrentHashMap<>();

    private CompositeDisposable getFeatureDelayedDisposables = new CompositeDisposable();
    private CompositeDisposable delayFaceTaskCompositeDisposable = new CompositeDisposable();
    /**
     * ????????????????????????????????????SurfaceView???TextureView
     */
    @BindView(R.id.single_camera_texture_preview)
    View previewView;
    /**
     * ????????????????????????
     */
    @BindView(R.id.single_camera_face_rect_view)
    FaceRectView faceRectView;

    @BindView(R.id.iv_outer)
    ImageView outerImg;

    private Animation mRadarAnim;


    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    /**
     * ????????????
     */
    private static final float SIMILAR_THRESHOLD = 0.8F;
    /**
     * ???????????????????????????
     */
    private static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE

    };

    //yhm end 1105
    @Override
    public RecognizePresenter initPresenter() {
        return new RecognizePresenter();
    }

    private void initAnim() {
        mRadarAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mRadarAnim.setFillAfter(true); // ?????????????????????????????????
        mRadarAnim.setDuration(2000); // ??????????????????
        mRadarAnim.setRepeatCount(Animation.INFINITE);//???????????????????????? ????????????
        mRadarAnim.setInterpolator(new LinearInterpolator());
        mRadarAnim.setRepeatMode(Animation.RESTART);
    }

    /**
     * ???????????????
     */
    private void initEngine() {
        ftEngine = new FaceEngine();
        ftInitCode = ftEngine.init(this, DetectMode.ASF_DETECT_MODE_VIDEO, ConfigUtil.getFtOrient(this),
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_DETECT);

        frEngine = new FaceEngine();
        frInitCode = frEngine.init(this, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_RECOGNITION);

        /*frEngine = new FaceEngine();
        frInitCode = frEngine.init(this, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                16, 6, FaceEngine.ASF_FACE_RECOGNITION | FaceEngine.ASF_AGE | FaceEngine.ASF_FACE_DETECT | FaceEngine.ASF_GENDER | FaceEngine.ASF_FACE3DANGLE);*/
        flEngine = new FaceEngine();
        flInitCode = flEngine.init(this, DetectMode.ASF_DETECT_MODE_IMAGE, DetectFaceOrientPriority.ASF_OP_0_ONLY,
                16, MAX_DETECT_NUM, FaceEngine.ASF_LIVENESS);

        Log.i(TAG, "initEngine:  init: " + ftInitCode);

        if (ftInitCode != ErrorInfo.MOK) {
            String error = getString(R.string.specific_engine_init_failed, "ftEngine", ftInitCode);
            Log.i(TAG, "initEngine: " + error);
            showToast(error);
        }
        if (frInitCode != ErrorInfo.MOK) {
            String error = getString(R.string.specific_engine_init_failed, "frEngine", frInitCode);
            Log.i(TAG, "initEngine: " + error);
            showToast(error);
        }
        if (flInitCode != ErrorInfo.MOK) {
            String error = getString(R.string.specific_engine_init_failed, "flEngine", flInitCode);
            Log.i(TAG, "initEngine: " + error);
            showToast(error);
        }
    }

    /**
     * ???????????????faceHelper??????????????????????????????????????????????????????????????????crash
     */
    private void unInitEngine() {
        if (ftInitCode == ErrorInfo.MOK && ftEngine != null) {
            synchronized (ftEngine) {
                int ftUnInitCode = ftEngine.unInit();
                Log.i(TAG, "unInitEngine: " + ftUnInitCode);
            }
        }
        if (frInitCode == ErrorInfo.MOK && frEngine != null) {
            synchronized (frEngine) {
                int frUnInitCode = frEngine.unInit();
                Log.i(TAG, "unInitEngine: " + frUnInitCode);
            }
        }
        if (flInitCode == ErrorInfo.MOK && flEngine != null) {
            synchronized (flEngine) {
                int flUnInitCode = flEngine.unInit();
                Log.i(TAG, "unInitEngine: " + flUnInitCode);
            }
        }
    }


    @Override
    protected void onDestroy() {

        if (cameraHelper != null) {
            cameraHelper.release();
            cameraHelper = null;
        }

        unInitEngine();
        if (getFeatureDelayedDisposables != null) {
            getFeatureDelayedDisposables.clear();
        }
        if (delayFaceTaskCompositeDisposable != null) {
            delayFaceTaskCompositeDisposable.clear();
        }
        if (faceHelper != null) {
            ConfigUtil.setTrackedFaceCount(this, faceHelper.getTrackedFaceCount());
            faceHelper.release();
            faceHelper = null;
        }
        //stopSerialPortConnect();
        FaceServer.getInstance().unInit();
        super.onDestroy();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_recognize_face;
    }

    @Override
    protected void initToolbar() {

    }

    @Override
    protected void initEventAndData() {
        //????????????
        isNeedGoHomeActivity(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initAnim();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            getWindow().setAttributes(attributes);
        }

        // Activity???????????????????????????????????????
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        //????????????????????????
        FaceServer.getInstance().init(this);
        //???????????????????????????????????????
        previewView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        outerImg.startAnimation(mRadarAnim);
    }

    private void initCamera() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        final FaceListener faceListener = new FaceListener() {
            @Override
            public void onFail(Exception e) {
                Log.e(TAG, "onFail: " + e.getMessage());
            }

            //??????FR?????????
            @Override
            public void onFaceFeatureInfoGet(final FaceFeature faceFeature, final Integer requestId, final Integer errorCode) {
                //FR??????
                if (faceFeature != null) {
//                    Log.i(TAG, "onPreview: fr end = " + System.currentTimeMillis() + " trackId = " + requestId);
                    Integer liveness = livenessMap.get(requestId);
                    //??????????????????????????????????????????
                    if (!livenessDetect) {
                        searchFace(faceFeature, requestId);
                    }
                    //?????????????????????????????????
                    else if (liveness != null && liveness == LivenessInfo.ALIVE) {
                        searchFace(faceFeature, requestId);
                    } else {
                        //??????????????????????????????????????????????????????????????????
                        if (requestFeatureStatusMap.containsKey(requestId)) {
                            Observable.timer(WAIT_LIVENESS_INTERVAL, TimeUnit.MILLISECONDS)
                                    .subscribe(new Observer<Long>() {
                                        Disposable disposable;

                                        @Override
                                        public void onSubscribe(Disposable d) {
                                            disposable = d;
                                            getFeatureDelayedDisposables.add(disposable);
                                        }

                                        @Override
                                        public void onNext(Long aLong) {
                                            onFaceFeatureInfoGet(faceFeature, requestId, errorCode);
                                        }

                                        @Override
                                        public void onError(Throwable e) {

                                        }

                                        @Override
                                        public void onComplete() {
                                            getFeatureDelayedDisposables.remove(disposable);
                                        }
                                    });
                        }
                    }

                }
                //??????????????????
                else {
                    if (increaseAndGetValue(extractErrorRetryMap, requestId) > MAX_RETRY_TIME) {
                        extractErrorRetryMap.put(requestId, 0);

                        String msg;
                        // ?????????FaceInfo????????????????????????????????????????????????????????????RGB????????????????????????????????????
                        if (errorCode != null && errorCode == ErrorInfo.MERR_FSDK_FACEFEATURE_LOW_CONFIDENCE_LEVEL) {
                            msg = getString(R.string.low_confidence_level);
                        } else {
                            msg = "ExtractCode:" + errorCode;
                        }
                        faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, msg));
                        // ??????????????????????????????????????????????????????????????????????????????
                        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
                        retryRecognizeDelayed(requestId);
                    } else {
                        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.TO_RETRY);
                    }
                }
            }

            @Override
            public void onFaceLivenessInfoGet(LivenessInfo livenessInfo, final Integer requestId, Integer errorCode) {
                if (livenessInfo != null) {
                    int liveness = livenessInfo.getLiveness();
                    livenessMap.put(requestId, liveness);
                    // ??????????????????
                    if (liveness == LivenessInfo.NOT_ALIVE) {
                        faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, "NOT_ALIVE"));
                        // ?????? FAIL_RETRY_INTERVAL ??????????????????????????????UNKNOWN????????????????????????????????????????????????
                        retryLivenessDetectDelayed(requestId);
                    }
                } else {
                    if (increaseAndGetValue(livenessErrorRetryMap, requestId) > MAX_RETRY_TIME) {
                        livenessErrorRetryMap.put(requestId, 0);
                        String msg;
                        // ?????????FaceInfo????????????????????????????????????????????????????????????RGB????????????????????????????????????
                        if (errorCode != null && errorCode == ErrorInfo.MERR_FSDK_FACEFEATURE_LOW_CONFIDENCE_LEVEL) {
                            msg = getString(R.string.low_confidence_level);
                        } else {
                            msg = "ProcessCode:" + errorCode;
                        }
                        faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, msg));
                        retryLivenessDetectDelayed(requestId);
                    } else {
                        livenessMap.put(requestId, LivenessInfo.UNKNOWN);
                    }
                }
            }


        };


        CameraListener cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(Camera camera, int cameraId, int displayOrientation, boolean isMirror) {
                Camera.Size lastPreviewSize = previewSize;
                previewSize = camera.getParameters().getPreviewSize();
                Log.d(TAG, "onCameraOpened: previewSize w" + previewSize.width + ":h" + previewSize.height);
                Log.d(TAG, "onCameraOpened: previewView w" + previewView.getWidth() + ":h" + previewView.getHeight());
                Log.d(TAG, "onCameraOpened: displayOrientation " + displayOrientation);
                drawHelper = new DrawHelper(previewSize.width, previewSize.height, previewView.getWidth(), previewView.getHeight(), displayOrientation
                        , cameraId, isMirror, false, false);
                Log.i(TAG, "onCameraOpened: " + drawHelper.toString());
                // ????????????????????????????????????????????????????????????
                if (faceHelper == null ||
                        lastPreviewSize == null ||
                        lastPreviewSize.width != previewSize.width || lastPreviewSize.height != previewSize.height) {
                    Integer trackedFaceCount = null;
                    // ??????????????????????????????
                    if (faceHelper != null) {
                        trackedFaceCount = faceHelper.getTrackedFaceCount();
                        faceHelper.release();
                    }
                    faceHelper = new FaceHelper.Builder()
                            .ftEngine(ftEngine)
                            .frEngine(frEngine)
                            .flEngine(flEngine)
                            .frQueueSize(MAX_DETECT_NUM)
                            .flQueueSize(MAX_DETECT_NUM)
                            .previewSize(previewSize)
                            .faceListener(faceListener)
                            .trackedFaceCount(trackedFaceCount == null ? ConfigUtil.getTrackedFaceCount(RecognizeActivity.this.getApplicationContext()) : trackedFaceCount)
                            .build();
                }
            }


            @Override
            public void onPreview(final byte[] nv21, Camera camera) {
                if (faceRectView != null) {
                    faceRectView.clearFaceInfo();
                }
                List<FacePreviewInfo> facePreviewInfoList = faceHelper.onPreviewFrame(nv21);
                if (facePreviewInfoList != null && faceRectView != null && drawHelper != null) {
                    drawPreviewInfo(facePreviewInfoList);
                }
                clearLeftFace(facePreviewInfoList);

                if (facePreviewInfoList != null && facePreviewInfoList.size() > 0 && previewSize != null) {
                    for (int i = 0; i < facePreviewInfoList.size(); i++) {
                        Integer status = requestFeatureStatusMap.get(facePreviewInfoList.get(i).getTrackId());
                        /**
                         * ????????????????????????????????????????????????????????????????????????????????????????????????ANALYZING???????????????????????????ALIVE???NOT_ALIVE??????????????????????????????
                         */
                        if (livenessDetect && (status == null || status != RequestFeatureStatus.SUCCEED)) {
                            Integer liveness = livenessMap.get(facePreviewInfoList.get(i).getTrackId());
                            if (liveness == null
                                    || (liveness != LivenessInfo.ALIVE && liveness != LivenessInfo.NOT_ALIVE && liveness != RequestLivenessStatus.ANALYZING)) {
                                livenessMap.put(facePreviewInfoList.get(i).getTrackId(), RequestLivenessStatus.ANALYZING);
                                faceHelper.requestFaceLiveness(nv21, facePreviewInfoList.get(i).getFaceInfo(), previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, facePreviewInfoList.get(i).getTrackId(), LivenessType.RGB);
                            }
                        }
                        /**
                         * ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                         * ??????????????????????????????????????????{@link FaceListener#onFaceFeatureInfoGet(FaceFeature, Integer, Integer)}?????????
                         */
                        if (status == null
                                || status == RequestFeatureStatus.TO_RETRY) {
                            requestFeatureStatusMap.put(facePreviewInfoList.get(i).getTrackId(), RequestFeatureStatus.SEARCHING);
                            faceHelper.requestFaceFeature(nv21, facePreviewInfoList.get(i).getFaceInfo(), previewSize.width, previewSize.height, FaceEngine.CP_PAF_NV21, facePreviewInfoList.get(i).getTrackId());
//                            Log.i(TAG, "onPreview: fr start = " + System.currentTimeMillis() + " trackId = " + facePreviewInfoList.get(i).getTrackedFaceCount());
                        }
                    }
                }
            }

            @Override
            public void onCameraClosed() {
                Log.i(TAG, "onCameraClosed: ");
            }

            @Override
            public void onCameraError(Exception e) {
                Log.i(TAG, "onCameraError: " + e.getMessage());
            }

            @Override
            public void onCameraConfigurationChanged(int cameraID, int displayOrientation) {
                if (drawHelper != null) {
                    drawHelper.setCameraDisplayOrientation(displayOrientation);
                }
                Log.i(TAG, "onCameraConfigurationChanged: " + cameraID + "  " + displayOrientation);
            }
        };
        Point point = new Point(previewView.getMeasuredWidth(), previewView.getMeasuredHeight());
        cameraHelper = new CameraHelper.Builder()
                .previewViewSize(point)
                .rotation(getWindowManager().getDefaultDisplay().getRotation())
                .additionalRotation(90)
                .specificCameraId(rgbCameraID != null ? rgbCameraID : Camera.CameraInfo.CAMERA_FACING_FRONT)
                .isMirror(false)
                .previewOn(previewView)
                .cameraListener(cameraListener)
                .build();
        cameraHelper.init();
        cameraHelper.start();
    }

    private void drawPreviewInfo(List<FacePreviewInfo> facePreviewInfoList) {
        List<DrawInfo> drawInfoList = new ArrayList<>();
        for (int i = 0; i < facePreviewInfoList.size(); i++) {
            String name = faceHelper.getName(facePreviewInfoList.get(i).getTrackId());
            Integer liveness = livenessMap.get(facePreviewInfoList.get(i).getTrackId());
            Integer recognizeStatus = requestFeatureStatusMap.get(facePreviewInfoList.get(i).getTrackId());

            // ?????????????????????????????????????????????
            int color = RecognizeColor.COLOR_UNKNOWN;
            if (recognizeStatus != null) {
                if (recognizeStatus == RequestFeatureStatus.FAILED) {
                    color = RecognizeColor.COLOR_FAILED;
                }
                if (recognizeStatus == RequestFeatureStatus.SUCCEED) {
                    color = RecognizeColor.COLOR_SUCCESS;
                }
            }
            if (liveness != null && liveness == LivenessInfo.NOT_ALIVE) {
                color = RecognizeColor.COLOR_FAILED;
            }

            drawInfoList.add(new DrawInfo(drawHelper.adjustRect(facePreviewInfoList.get(i).getFaceInfo().getRect()),
                    GenderInfo.UNKNOWN, AgeInfo.UNKNOWN_AGE, liveness == null ? LivenessInfo.UNKNOWN : liveness, color,
                    name == null ? String.valueOf(facePreviewInfoList.get(i).getTrackId()) : name));
        }
        drawHelper.draw(faceRectView, drawInfoList);
    }


    /**
     * ???????????????????????????
     *
     * @param facePreviewInfoList ?????????trackId??????
     */
    private void clearLeftFace(List<FacePreviewInfo> facePreviewInfoList) {
        if (facePreviewInfoList == null || facePreviewInfoList.size() == 0) {
            requestFeatureStatusMap.clear();
            livenessMap.clear();
            livenessErrorRetryMap.clear();
            extractErrorRetryMap.clear();
            if (getFeatureDelayedDisposables != null) {
                getFeatureDelayedDisposables.clear();
            }
            return;
        }
        Enumeration<Integer> keys = requestFeatureStatusMap.keys();
        while (keys.hasMoreElements()) {
            int key = keys.nextElement();
            boolean contained = false;
            for (FacePreviewInfo facePreviewInfo : facePreviewInfoList) {
                if (facePreviewInfo.getTrackId() == key) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                requestFeatureStatusMap.remove(key);
                livenessMap.remove(key);
                livenessErrorRetryMap.remove(key);
                extractErrorRetryMap.remove(key);
            }
        }


    }

    private void searchFace(final FaceFeature frFace, final Integer requestId) {
        Observable
                .create(new ObservableOnSubscribe<RecognizeUser>() {
                    @Override
                    public void subscribe(ObservableEmitter<RecognizeUser> emitter) {
                        RecognizeUser recognizeUser = FaceServer.getInstance().recognizeUser(frFace);
                        emitter.onNext(recognizeUser);

                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RecognizeUser>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RecognizeUser compareResult) {
                        if (compareResult != null && compareResult.getSimilar() > SIMILAR_THRESHOLD) {
                            BaseApplication.getInstance().setCurrentUer(compareResult.getUser());
                            Log.d(TAG, "??????????????????");
                            startActivity(new Intent(RecognizeActivity.this, NewUnlockActivity.class));
                            outerImg.clearAnimation();
                            finish();
                        } else {
                            if (faceHelper != null) {
                                faceHelper.setName(requestId, "???????????????");
                                retryRecognizeDelayed(requestId);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (faceHelper != null) {
                            faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, "NOT_REGISTERED"));
                            retryRecognizeDelayed(requestId);
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    /**
     * ???{@link #previewView}????????????????????????????????????????????????????????????????????????????????????
     */
    @Override
    public void onGlobalLayout() {
        previewView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            initEngine();
            initCamera();
        }
    }

    /**
     * ???map???key?????????value???1??????
     *
     * @param countMap map
     * @param key      key
     * @return ???1??????value
     */
    public int increaseAndGetValue(Map<Integer, Integer> countMap, int key) {
        if (countMap == null) {
            return 0;
        }
        Integer value = countMap.get(key);
        if (value == null) {
            value = 0;
        }
        countMap.put(key, ++value);
        return value;
    }

    /**
     * ?????? FAIL_RETRY_INTERVAL ????????????????????????
     *
     * @param requestId ??????ID
     */
    private void retryLivenessDetectDelayed(final Integer requestId) {
        Observable.timer(FAIL_RETRY_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(new Observer<Long>() {
                    Disposable disposable;

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposable = d;
                        delayFaceTaskCompositeDisposable.add(disposable);
                    }

                    @Override
                    public void onNext(Long aLong) {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        // ????????????????????????UNKNOWN????????????????????????????????????????????????
                        if (livenessDetect) {
                            faceHelper.setName(requestId, Integer.toString(requestId));
                        }
                        livenessMap.put(requestId, LivenessInfo.UNKNOWN);
                        delayFaceTaskCompositeDisposable.remove(disposable);
                    }
                });
    }

    /**
     * ?????? FAIL_RETRY_INTERVAL ????????????????????????
     *
     * @param requestId ??????ID
     */
    private void retryRecognizeDelayed(final Integer requestId) {
        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
        Observable.timer(FAIL_RETRY_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(new Observer<Long>() {
                    Disposable disposable;

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposable = d;
                        delayFaceTaskCompositeDisposable.add(disposable);
                    }

                    @Override
                    public void onNext(Long aLong) {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        // ????????????????????????????????????FAILED????????????????????????????????????????????????
                        faceHelper.setName(requestId, Integer.toString(requestId));
                        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.TO_RETRY);
                        delayFaceTaskCompositeDisposable.remove(disposable);
                    }
                });
    }


    @Override
    public void handelAllUserInfo(BaseResponse<List<UserInfo>> userInfos) {

    }

    @Override
    public void handleUpdateFeature(BaseResponse<UserInfo> userInfo) {

    }


    @Override
    public void afterRequestPermission(int requestCode, boolean isAllGranted) {
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            if (isAllGranted) {
//                activeEngine(null);
            } else {
                showToast(getString(R.string.permission_denied));
            }
        }
    }

    @OnClick({R.id.titleLeft})
    void performClick(View v) {
        switch (v.getId()) {
            case R.id.titleLeft:
                finish();
                break;
        }
    }
}