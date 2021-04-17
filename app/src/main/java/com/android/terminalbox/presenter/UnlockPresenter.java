package com.android.terminalbox.presenter;


import com.android.terminalbox.base.presenter.BasePresenter;
import com.android.terminalbox.core.bean.cmb.AssetsListPage;
import com.android.terminalbox.core.bean.user.NewOrderBody;
import com.android.terminalbox.core.bean.user.OrderResponse;
import com.android.terminalbox.contract.UnlockContract;
import com.android.terminalbox.core.DataManager;
import com.android.terminalbox.core.bean.BaseResponse;
import com.android.terminalbox.core.http.widget.BaseObserver;
import com.android.terminalbox.utils.RxUtils;

import java.util.ArrayList;


/**
 * @author yhm
 * @date 2018/2/26
 */
public class UnlockPresenter extends BasePresenter<UnlockContract.View> implements UnlockContract.Presenter {

    private static String deviceId = "15aa68f3183311ebb7260242ac120004_uniqueCode002";

    @Override
    public void fetchPageAssetsList(Integer size, Integer page, String patternName, String userRealName, String conditions) {
        addSubscribe(DataManager.getInstance().fetchPageAssetsList(size, page, patternName, userRealName, conditions)
                .compose(RxUtils.rxSchedulerHelper())
                .compose(RxUtils.handleResult())
                .subscribeWith(new BaseObserver<AssetsListPage>(mView, false) {
                    @Override
                    public void onNext(AssetsListPage assetsListPage) {
                        if (page <= assetsListPage.getPages()) {
                            mView.handleFetchPageAssetsList(assetsListPage.getList());
                        } else {
                            mView.handleFetchPageAssetsList(new ArrayList<>());
                        }
                    }
                }));
    }

}
