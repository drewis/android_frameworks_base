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
public class FuzzyClock extends LinearLayout {

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
        private WeakReference<FuzzyClock> mClock;
        private Context mContext;

        public TimeChangedReceiver(FuzzyClock clock) {
            mClock = new WeakReference<FuzzyClock>(clock);
            mContext = clock.getContext();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Post a runnable to avoid blocking the broadcast.
            final boolean timezoneChanged =
                    intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED);
            final FuzzyClock clock = mClock.get();
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
        private WeakReference<FuzzyClock> mClock;
        private Context mContext;
        public FormatChangeObserver(FuzzyClock clock) {
            super(new Handler());
            mClock = new WeakReference<FuzzyClock>(clock);
            mContext = clock.getContext();
        }
        @Override
        public void onChange(boolean selfChange) {
            FuzzyClock fuzzyClock = mClock.get();
            if (fuzzyClock != null) {
                fuzzyClock.setDateFormat();
                fuzzyClock.updateTime();
            } else {
                try {
                    mContext.getContentResolver().unregisterContentObserver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    }

    public FuzzyClock(Context context) {
        this(context, null);
    }

    public FuzzyClock(Context context, AttributeSet attrs) {
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
        int mMinutes = mCalendar.get(mCalendar.MINUTE);
        int mHours = mCalendar.get(mCalendar.HOUR);
        CharSequence mTimeString;

    String mNextH, mTimeH,
        mOclock = " o\'clock",
        mFivePast = "five after ",
        mTenPast = "ten after ",
        mQuarterPast = "a quarter after ",
        mTwentyPast = " twenty",
        mTwentyFivePast = "twenty five after ",
        mHalfPast = "half past ",
        mTwentyFiveTo = "twenty five till ",
        mTwentyTo = "twenty till ",
        mQuarterTo = "a quarter till ",
        mTenTo = "ten till ",
        mFiveTo = "five till ",
        mOne = "one",
        mTwo = "two",
        mThree = "three",
        mFour = "four",
        mFive = "five",
        mSix = "six",
        mSeven = "seven",
        mEight = "eight",
        mNine = "nine",
        mTen = "ten",
        mEleven = "eleven",
        mTwelve = "twelve";

        //hours
        if(mHours == 1) { mNextH = mTwo; mTimeH = mOne; }
        else if(mHours == 2) { mNextH = mThree; mTimeH = mTwo; }
        else if(mHours == 3) { mNextH = mFour; mTimeH = mThree; }
        else if(mHours == 4) { mNextH = mFive; mTimeH = mFour; }
        else if(mHours == 5) { mNextH = mSix; mTimeH = mFive; }
        else if(mHours == 6) { mNextH = mSeven; mTimeH = mSix; }
        else if(mHours == 7) { mNextH = mEight; mTimeH = mSeven; }
        else if(mHours == 8) { mNextH = mNine; mTimeH = mEight; }
        else if(mHours == 9) { mNextH = mTen; mTimeH = mNine; }
        else if(mHours == 10) { mNextH = mEleven; mTimeH = mTen; }
        else if(mHours == 11) { mNextH = mTwelve; mTimeH = mEleven; }
        else if(mHours == 12) { mNextH = mOne; mTimeH = mTwelve; }
        else { mNextH = mTimeH = "it fuckin broke"; }// { mNextH = mOne; mTimeH = mTwelve; }

        //minutes
        if ( 0 <= mMinutes && mMinutes <= 4 ) mTimeString = mTimeH + mOclock;
        else if ( 5 <= mMinutes && mMinutes <= 9 ) mTimeString = mFivePast + mTimeH;
        else if ( 10 <= mMinutes && mMinutes <= 14 ) mTimeString = mTenPast + mTimeH;
        else if ( 15 <= mMinutes && mMinutes <= 19 ) mTimeString = mQuarterPast + mTimeH;
        else if ( 20 <= mMinutes && mMinutes <= 24 ) mTimeString = mTimeH + mTwentyPast;
        else if ( 25 <= mMinutes && mMinutes <= 29 ) mTimeString = mTwentyFivePast + mTimeH;
        else if ( 30 <= mMinutes && mMinutes <= 34 ) mTimeString = mHalfPast + mTimeH;
        else if ( 35 <= mMinutes && mMinutes <= 39 ) mTimeString = mTwentyFiveTo + mNextH;
        else if ( 40 <= mMinutes && mMinutes <= 43 ) mTimeString = mTwentyTo + mNextH;
        else if ( 44 <= mMinutes && mMinutes <= 47 ) mTimeString = mQuarterTo + mNextH;
        else if ( 48 <= mMinutes && mMinutes <= 51 ) mTimeString = mTenTo + mNextH;
        else if ( 52 <= mMinutes && mMinutes <= 55 ) mTimeString = mFiveTo + mNextH;
        else if ( 56 <= mMinutes && mMinutes <= 60 ) mTimeString = mNextH + mOclock;
        else { mTimeString = "somethin\'s broke"; }

        //print the time
        mTimeDisplay.setText(mTimeString);

    }

    private void setDateFormat() {
        mFormat = M12;
        mAmPm.setShowAmPm(false);//mFormat.equals(M12));
    }
}
