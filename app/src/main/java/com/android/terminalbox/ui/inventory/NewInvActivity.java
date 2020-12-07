package com.android.terminalbox.ui.inventory;

import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.terminalbox.R;
import com.android.terminalbox.base.activity.BaseActivity;
import com.android.terminalbox.base.presenter.AbstractPresenter;
import com.android.terminalbox.core.bean.user.EpcFile;
import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.esim.rylai.smartbox.uhf.InventoryStrategy;
import com.esim.rylai.smartbox.uhf.ReaderResult;
import com.esim.rylai.smartbox.uhf.UhfManager;
import com.esim.rylai.smartbox.uhf.UhfTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;

public class NewInvActivity extends BaseActivity {
    String TAG = "InventoryActivity";
    @BindView(R.id.tv_number)
    TextView numberText;
    @BindView(R.id.iv_round)
    ImageView roundImg;
    @BindView(R.id.rv_inv_items)
    RecyclerView mRecycleView;
    @BindView(R.id.edit_search)
    EditText editText;
    @BindView(R.id.number_layout)
    LinearLayout numberLayout;
    @BindView(R.id.tv_see_detail)
    TextView seeDetail;
    @BindView(R.id.detail_layout)
    RelativeLayout detailLayout;
    private List<EpcFile> files = new ArrayList<>();
    //存储一次盘点的数据
    private List<EpcFile> allFiles = new ArrayList<>();
    private FileBeanAdapter mAdapter;
    private Animation mRadarAnim;
    private Handler mHandler = new Handler();

    private void initAnim() {
        mRadarAnim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mRadarAnim.setFillAfter(true); // 设置保持动画最后的状态
        mRadarAnim.setDuration(2000); // 设置动画时间
        mRadarAnim.setRepeatCount(Animation.INFINITE);//设置动画重复次数 无限循环
        mRadarAnim.setInterpolator(new LinearInterpolator());
        mRadarAnim.setRepeatMode(Animation.RESTART);
    }

    @Override
    public AbstractPresenter initPresenter() {
        return null;
    }

    @Override
    protected void initEventAndData() {
        numberLayout.setVisibility(View.VISIBLE);
        roundImg.setVisibility(View.VISIBLE);
        seeDetail.setVisibility(View.VISIBLE);
        detailLayout.setVisibility(View.GONE);
        editText.setVisibility(View.GONE);
        mAdapter = new FileBeanAdapter(files, this);
        mRecycleView.setLayoutManager(new LinearLayoutManager(this));
        mRecycleView.setAdapter(mAdapter);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
                    String assetsId = editText.getText().toString();
                    editText.setSelection(assetsId.length());
                    List<EpcFile> filterList = Stream.of(allFiles).filter(new Predicate<EpcFile>() {
                        @Override
                        public boolean test(EpcFile value) {
                            int i = value.getEpcCode().indexOf(assetsId);
                            return i != -1;
                        }
                    }).collect(Collectors.toList());
                    files.clear();
                    files.addAll(filterList);
                    mAdapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            }
        });
        initAnim();
        //开始盘点
        roundImg.startAnimation(mRadarAnim);
        UhfManager.getInstance().confReadListener(uhfListener);
        UhfManager.getInstance().confReadHostIp("172.16.63.100").setShowLog(true);
        UhfManager.getInstance().confReadAntIndexs(new int[]{1, 2, 3, 4});
        UhfManager.getInstance().confReadTagFilter(null);
        InventoryStrategy inventoryStrategy = new InventoryStrategy();
        inventoryStrategy.setMaxTimesOfInv(3);
        inventoryStrategy.setMaxTimesOfUnChange(6);
        UhfManager.getInstance().confInventoryStrategy(inventoryStrategy);
        roundImg.startAnimation(mRadarAnim);
        UhfManager.getInstance().startReadTags();
        Log.e("Thread======", Thread.currentThread().toString());
    }

    private final UhfManager.EsimUhfReadListener uhfListener = new UhfManager.EsimUhfReadListener() {
        private long startTime = 0;

        @Override
        public void onStartSuc() {

            Log.d(TAG, "onStartSuc:" + "=============");
        }

        @Override
        public void onStartFail(ReaderResult reader_err) {
            Log.d(TAG, "onStartFail:" + "=============");
        }

        @Override
        public void onReadIncrementalTotal(Collection<String> epcs) {
            Log.d(TAG, "onStartSuc:" + "=============" + epcs.size());
            Log.e("Thread======", Thread.currentThread().toString());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(numberText != null){
                        numberText.setText(String.valueOf(epcs.size()));
                    }
                }
            });
        }

        @Override
        public void onReadFinish(Collection<UhfTag> tags) {
            Log.d(TAG, "onReadFinish:" + "=============");
            Log.e("Thread======", Thread.currentThread().toString());
            List<String> epcs = Stream.of(tags).map(new Function<UhfTag, String>() {
                @Override
                public String apply(UhfTag uhfTags) {
                    return uhfTags.getEpc();
                }
            }).collect(Collectors.toList());
            List<EpcFile> epcToFiles = Stream.of(epcs).map(new Function<String, EpcFile>() {
                @Override
                public EpcFile apply(String s) {
                    return new EpcFile("档案Epc", s);
                }
            }).collect(Collectors.toList());
            files.addAll(epcToFiles);//Log.d(TAG, "invTags: 本次盘点后总标签" + inBoxEpcsTemp.size() + "    " + inBoxEpcsTemp.toString());
            Log.e(TAG, files.size() + "====" + files.toString());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (numberText != null) {
                        numberText.setText("" + files.size());
                        mAdapter.notifyDataSetChanged();
                        allFiles.addAll(files);
                        if (roundImg != null) {
                            roundImg.clearAnimation();
                        }
                    }
                }
            });
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_inv;
    }

    @Override
    protected void initToolbar() {

    }

    @OnClick({R.id.titleLeft, R.id.tv_see_detail})
    void performClick(View v) {
        switch (v.getId()) {
            case R.id.titleLeft:
                finish();
                break;
            case R.id.tv_see_detail:
                numberLayout.setVisibility(View.GONE);
                roundImg.setVisibility(View.GONE);
                detailLayout.setVisibility(View.VISIBLE);
                editText.setVisibility(View.VISIBLE);
                seeDetail.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeMessages(0);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}