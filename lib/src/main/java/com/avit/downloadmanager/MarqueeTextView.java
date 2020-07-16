package com.avit.downloadmanager;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;

import androidx.appcompat.widget.AppCompatTextView;

import java.lang.reflect.Field;

/**
 * Created by Administrator on 2017/3/16.
 */

public class MarqueeTextView extends AppCompatTextView {
    public final static String TAG = "MarqueeTextView";

    private float speedUnit;
    private float speed;

    private boolean speedIsChange;

    private Object marquee;
    private Field mPixelsPer;

    private TextPaint textPaint;
    private float textSize;


    public MarqueeTextView(Context context) {
        super(context);
        init();
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setSingleLine();
        setLines(1);
        setMarqueeRepeatLimit(-1);

        textPaint = getPaint();
        speedUnit = 30 * getResources().getDisplayMetrics().density / 1000f;
        Log.d(TAG, "init: speedUnit " + speedUnit);
    }


    public final void setSpeed(float speed) {
        this.speed = speed;
        speedIsChange = true;
    }

    private void applySpeed() {

        if (!speedIsChange) {
            return;
        }

        try {

            if (marquee != null && mPixelsPer != null) {
                setPixelsPer();
                return;
            }

            Class textViewCls = getClass().getSuperclass().getSuperclass();
            Field mMarquee = textViewCls.getDeclaredField("mMarquee");
            if (mMarquee == null)
                return;

            mMarquee.setAccessible(true);
            Object marquee = mMarquee.get(this);

            if (marquee == null)
                return;

            this.marquee = marquee;

            Log.d(TAG, "applySpeed: marquee class = " + marquee.getClass());

            Class marqueeCls = marquee.getClass();
            //= marqueeCls.getDeclaredField("mPixelsPerSecond");
            //= marqueeCls.getDeclaredField("mPixelsPerMs");

            Field mPixelsPer = null;
            Field[] fields = marqueeCls.getDeclaredFields();
            for (int i = 0; i < fields.length; ++i) {
                Field field = fields[i];
                String name = field.getName();
                if (name.startsWith("mPixelsPer") || name.equals("mScrollUnit")) {
                    mPixelsPer = field;
                    break;
                }
            }

            if (mPixelsPer == null)
                return;

            Log.d(TAG, "applySpeed: mPixelsPer fields = " + mPixelsPer.getName());
            this.mPixelsPer = mPixelsPer;

            setPixelsPer();

        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "applySpeed: ", e);
        }
    }

    private void setPixelsPer() {
        Log.d(TAG, "setPixelsPer: speed = " + speed);
        try {
            float ts = getTextSize();
            Log.d(TAG, "setPixelsPer: ts = " + ts);

            mPixelsPer.setAccessible(true);
            String name = mPixelsPer.getName();
            if (name.endsWith("Second")) {
                mPixelsPer.set(marquee, speed * speedUnit * 1000);
            } else if (name.endsWith("Ms")) {
                mPixelsPer.set(marquee, speed * speedUnit);
            } else if (name.equals("mScrollUnit")){
                mPixelsPer.set(marquee, speed * speedUnit * 30);
            }

            speedIsChange = false;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "setPixelsPer: ", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        applySpeed();
    }

    private boolean isCallByMarquee(StackTraceElement[] elements) {

        for (int i = 0; i < elements.length; ++i) {
            StackTraceElement element = elements[i];
            String methodName = element.getMethodName();
            if (methodName.equals("startMarquee") || methodName.equals("tick")) {
                applySpeed();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isFocused() {
        if (isCallByMarquee(Thread.currentThread().getStackTrace()))
            return true;

        return super.isFocused();
    }
}

