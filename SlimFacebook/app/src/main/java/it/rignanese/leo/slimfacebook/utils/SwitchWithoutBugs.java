package it.rignanese.leo.slimfacebook.utils;

import android.content.Context;
import androidx.preference.Preference;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import android.util.AttributeSet;

/**
 * SlimSocial for Facebook is an Open Source app realized by Leonardo Rignanese <rignanese.leo@gmail.com>
 * GNU GENERAL PUBLIC LICENSE  Version 2, June 1991
 * GITHUB: https://github.com/rignaneseleo/SlimSocial-for-Facebook
 */
public class SwitchWithoutBugs extends SwitchPreferenceCompat {
    public SwitchWithoutBugs(Context context) {
        super(context);
    }

    public SwitchWithoutBugs(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchWithoutBugs(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    @Override
    public void onBindViewHolder(PreferenceViewHolder holder)
    {
        super.onBindViewHolder(holder);
    }
}