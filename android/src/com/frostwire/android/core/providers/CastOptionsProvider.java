package com.frostwire.android.core.providers;


import android.content.Context;

import com.frostwire.android.R;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;

import java.util.List;

public class CastOptionsProvider implements OptionsProvider {
    public CastOptions getCastOptions(Context appContext) {
        return new CastOptions.Builder()
//                .setReceiverApplicationId(appContext.getString(R.string.wait))//todo add id
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .build();
    }
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
