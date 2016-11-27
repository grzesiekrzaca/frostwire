package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

/**
 * Created by Grzesiek on 2016-11-27.
 */

public class OverlayImageView extends ImageView {
    public OverlayImageView(Context context) {
        super(context);
    }

    public OverlayImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OverlayImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //don't interact with touch events (ie. be transparent to touch)
        return false;
    }
}
