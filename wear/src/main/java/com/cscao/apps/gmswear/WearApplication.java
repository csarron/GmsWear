package com.cscao.apps.gmswear;

import android.app.Application;

import com.cscao.libs.gmswear.GmsWear;

/**
 * Created by qqcao on 11/03/16 Thursday.
 *
 * Initialize GmsWear
 */

public class WearApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GmsWear.initialize(this);
    }
}
