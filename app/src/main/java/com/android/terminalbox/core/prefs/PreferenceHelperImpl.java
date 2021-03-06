package com.android.terminalbox.core.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.terminalbox.app.BaseApplication;
import com.android.terminalbox.app.Constants;

/**
 * @author yhm
 * @date 2017/11/27
 */

public class PreferenceHelperImpl implements PreferenceHelper {

    private final SharedPreferences mPreferences;
    private volatile static PreferenceHelperImpl INSTANCE = null;

    private PreferenceHelperImpl() {
        mPreferences = BaseApplication.getInstance().getSharedPreferences(Constants.MY_SHARED_PREFERENCE, Context.MODE_PRIVATE);
    }

    public static PreferenceHelperImpl getInstance() {
        if (INSTANCE == null) {
            synchronized (PreferenceHelperImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PreferenceHelperImpl();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

    @Override
    public void saveHostUrl(String hostUrl) {
        mPreferences.edit().putString(Constants.HOSTURL, hostUrl).apply();
    }

    @Override
    public String getHostUrl() {
        return mPreferences.getString(Constants.HOSTURL, "http://117.34.118.157:20000/");
    }

    @Override
    public void saveMixTime(int mixTime) {
        mPreferences.edit().putInt(Constants.MIX_TIME, mixTime).apply();
    }

    @Override
    public int getMixTime() {
        return mPreferences.getInt(Constants.MIX_TIME, 4);
    }

    @Override
    public void saveMixTimeUnchange(int mixTimeUnchange) {
        mPreferences.edit().putInt(Constants.MIX_TIME_UNCHANGE, mixTimeUnchange).apply();
    }

    @Override
    public int getMixTimeUnchange() {
        return mPreferences.getInt(Constants.MIX_TIME_UNCHANGE, 6);
    }

    @Override
    public void saveIpOne(String ipOne) {
        mPreferences.edit().putString(Constants.IP_ONE, ipOne).apply();
    }

    @Override
    public String getIpOne() {
        return mPreferences.getString(Constants.IP_ONE, "172.16.68.97");
    }

    @Override
    public void saveIpTwo(String ipTwo) {
        mPreferences.edit().putString(Constants.IP_TWO, ipTwo).apply();
    }

    @Override
    public String getIpTwo() {
        return mPreferences.getString(Constants.IP_TWO, "172.16.68.98");
    }

    @Override
    public void setToken(String token) {
        mPreferences.edit().putString(Constants.TOKEN, token).apply();
    }

    @Override
    public String getToken() {
        return mPreferences.getString(Constants.TOKEN, "8c72bc61-65ea-4814-aef9-d947ae3f9c5c");
    }


}
