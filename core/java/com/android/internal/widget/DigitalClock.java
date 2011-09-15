/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.widget;

import com.android.internal.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.util.Calendar;

/**
 * Displays the time
 */
public class DigitalClock extends LinearLayout {

    private final static String M12 = "hh:mm";
    private final static String M24 = "kk:mm";

    private Calendar mCalendar;
    private String mFormat;
    private TextView mTimeDisplay;
    private AmPm mAmPm;
    private ContentObserver mFormatChangeObserver;
    private int mAttached = 0; // for debugging - tells us whether attach/detach is unbalanced

    /* called by system on minute ticks */
    private final Handler mHandler = new Handler();
    private BroadcastReceiver mIntentReceiver;

    private static class TimeChangedReceiver extends BroadcastReceiver {
        private WeakReference<DigitalClock> mClock;
        private Context mContext;

        public TimeChangedReceiver(DigitalClock clock) {
            mClock = new WeakReference<DigitalClock>(clock);
            mContext = clock.getContext();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Post a runnable to avoid blocking the broadcast.
            final boolean timezoneChanged =
                    intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED);
            final DigitalClock clock = mClock.get();
            if (clock != null) {
                clock.mHandler.post(new Runnable() {
                    public void run() {
                        if (timezoneChanged) {
                            clock.mCalendar = Calendar.getInstance();
                        }
                        clock.updateTime();
                    }
                });
            } else {
                try {
                    mContext.unregisterReceiver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    };

    static class AmPm {
        private TextView mAmPm;
        private String mAmString, mPmString;

        AmPm(View parent, Typeface tf) {
            mAmPm = (TextView) parent.findViewById(R.id.am_pm);
            if (tf != null) {
                mAmPm.setTypeface(tf);
            }

            String[] ampm = new DateFormatSymbols().getAmPmStrings();
            mAmString = ampm[0];
            mPmString = ampm[1];
        }

        void setShowAmPm(boolean show) {
            mAmPm.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        void setIsMorning(boolean isMorning) {
            mAmPm.setText(isMorning ? mAmString : mPmString);
        }
    }

    private static class FormatChangeObserver extends ContentObserver {
        private WeakReference<DigitalClock> mClock;
        private Context mContext;
        public FormatChangeObserver(DigitalClock clock) {
            super(new Handler());
            mClock = new WeakReference<DigitalClock>(clock);
            mContext = clock.getContext();
        }
        @Override
        public void onChange(boolean selfChange) {
            DigitalClock digitalClock = mClock.get();
            if (digitalClock != null) {
                digitalClock.setDateFormat();
                digitalClock.updateTime();
            } else {
                try {
                    mContext.getContentResolver().unregisterContentObserver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    }

    public DigitalClock(Context context) {
        this(context, null);
    }

    public DigitalClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTimeDisplay = (TextView) findViewById(R.id.timeDisplay);
        mTimeDisplay.setTypeface(Typeface.createFromFile("/system/fonts/DroidSans.ttf"));
        mAmPm = new AmPm(this, Typeface.createFromFile("/system/fonts/DroidSans-Bold.ttf"));
        mCalendar = Calendar.getInstance();

        setDateFormat();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mAttached++;

        /* monitor time ticks, time changed, timezone */
        if (mIntentReceiver == null) {
            mIntentReceiver = new TimeChangedReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        /* monitor 12/24-hour display preference */
        if (mFormatChangeObserver == null) {
            mFormatChangeObserver = new FormatChangeObserver(this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, mFormatChangeObserver);
        }

        updateTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttached--;

        if (mIntentReceiver != null) {
            mContext.unregisterReceiver(mIntentReceiver);
        }
        if (mFormatChangeObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(
                    mFormatChangeObserver);
        }

        mFormatChangeObserver = null;
        mIntentReceiver = null;
    }

    void updateTime(Calendar c) {
        mCalendar = c;
        updateTime();
    }

    private void updateTime() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        int minutes = mCalendar.get(mCalendar.MINUTE);
        int hours = mCalendar.get(mCalendar.HOUR);

        //find hours string equivalents
        CharSequence nextH,timeH,timestring;
        switch (hours) {
            case 1: nextH = "two";timeH = "one"; break;
            case 2: nextH = "three";timeH = "two"; break;
            case 3: nextH = "four";timeH = "three"; break;
            case 4: nextH = "five";timeH = "four"; break;
            case 5: nextH = "six";timeH = "five"; break;
            case 6: nextH = "seven";timeH = "six"; break;
            case 7: nextH = "eight";timeH = "seven"; break;
            case 8: nextH = "nine";timeH = "eight"; break;
            case 9: nextH = "ten";timeH = "nine"; break;
            case 10: nextH = "eleven";timeH = "ten"; break;
            case 11: nextH = "twelve";timeH = "eleven"; break;
            case 12: nextH = "one";timeH = "twelve"; break;
            default: nextH = timeH = ""; break;
        }

        switch (minutes) {
            //oclock
            case 0: case 1:case 2:case 3: case 4:
                timestring = timeH + " o\'clock";break;
            //five past
            case 5: case 6: case 7: case 8: case 9:
                timestring = "five past " + timeH; break;
            //ten past
            case 10: case 11: case 12: case 13: case 14:
                timestring = "ten past " + timeH; break;
            //a quarter past
            case 15: case 16: case 17: case 18: case 19:
                timestring = "a quarter past " + timeH; break;
            //twenty past
            case 20: case 21: case 22: case 23: case 24:
                timestring = "twenty past " + timeH; break;
            //twenty-five past
            case 25: case 26: case 27: case 28: case 29:
                timestring = "twenty five past " + timeH; break;
            //half past
            case 30: case 31: case 32: case 33: case 34:
                timestring = "half past " + timeH; break;
            //twenty-five till
            case 35: case 36: case 37: case 38: case 39:
                timestring = "twenty five till " + nextH; break;
            //twenty till
            case 40: case 41: case 42: case 43: case 44:
                timestring = "twenty till " + nextH; break;
            //a quarter till
            case 45: case 46: case 47: case 48: case 49:
                timestring = "a quarter till " + nextH; break;
            //ten till
            case 50: case 51: case 52: case 53:
                timestring = "ten till " + nextH; break;
            //five till
            case 54: case 55: case 56: 
                timestring = "five till " + nextH; break;
            //oclock
            case 57: case 58: case 59: case 60:
                timestring = nextH + " o\'clock"; break;
            default: 
                timestring = timeH; break;
        }
        
        mTimeDisplay.setText(timestring);
        //ugly if else to find the minutes string
//        if        ( minutes < 3 ) {
//            mTimeDisplay.setText(timeH + " o\'clock");
//        } else if ( minutes < 8 ) {
//            mTimeDisplay.setText("five past " + timeH);
//        } else if ( minutes < 13 ) {
//            mTimeDisplay.setText("ten past " + timeH);
//        } else if ( minutes < 18 ) {
//            mTimeDisplay.setText("quarter past " + timeH);
//        } else if ( minutes < 23 ) {
//            mTimeDisplay.setText("twenty past " + timeH);
//        } else if ( minutes < 28 ) {
//            mTimeDisplay.setText("twenty-five past " + timeH);
//        } else if ( minutes < 33 ){
//            mTimeDisplay.setText("half past " + timeH);
//        } else if ( minutes < 38 ){
//            mTimeDisplay.setText("twenty-five till " + nextH);
//        } else if ( minutes < 43 ){
//            mTimeDisplay.setText("twenty till " + nextH);
//        } else if ( minutes < 48 ){
//            mTimeDisplay.setText("quarter till " + nextH);
//        } else if ( minutes < 53 ){
//            mTimeDisplay.setText("ten till " + nextH);
//        } else if ( minutes < 58 ){
//            mTimeDisplay.setText("five till " + nextH);
//        } else {
//            mTimeDisplay.setText(nextH + " o\'clock");
//        }
    }

    private void setDateFormat() {
        mFormat = M12;
        mAmPm.setShowAmPm(false);//mFormat.equals(M12));
    }
}
