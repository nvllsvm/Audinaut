/*
  This file is part of Subsonic.
    Subsonic is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    Subsonic is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
    Copyright 2015 (C) Scott Jackson
*/

package net.nullsum.audinaut.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import android.util.SparseArray;
import android.util.TypedValue;

import net.nullsum.audinaut.R;

import java.util.WeakHashMap;

public class DrawableTint {
    private static final SparseArray<Integer> attrMap = new SparseArray<>();
    private static final WeakHashMap<Integer, Drawable> tintedDrawables = new WeakHashMap<>();

    public static Drawable getTintedDrawableFromColor(Context context) {
        if (tintedDrawables.containsKey(R.drawable.abc_spinner_mtrl_am_alpha)) {
            return tintedDrawables.get(R.drawable.abc_spinner_mtrl_am_alpha);
        }

        int color = context.getResources().getColor(android.R.color.white);
        Drawable background = context.getResources().getDrawable(R.drawable.abc_spinner_mtrl_am_alpha);
        background.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        tintedDrawables.put(R.drawable.abc_spinner_mtrl_am_alpha, background);
        return background;
    }

    public static int getColorRes(Context context, @AttrRes int colorAttr) {
        Integer color = attrMap.get(colorAttr);
        if (color == null) {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = context.getTheme();
            theme.resolveAttribute(colorAttr, typedValue, true);
            color = typedValue.data;
            attrMap.put(colorAttr, color);
        }
        return color;
    }

    public static int getDrawableRes(Context context, @AttrRes int drawableAttr) {
        Integer attr = attrMap.get(drawableAttr);
        if (attr == null) {
            int[] attrs = new int[]{drawableAttr};
            TypedArray typedArray = context.obtainStyledAttributes(attrs);
            @DrawableRes int drawableRes = typedArray.getResourceId(0, 0);
            typedArray.recycle();
            attrMap.put(drawableAttr, drawableRes);
            return drawableRes;
        }
        return attr;
    }

    public static void wipeTintCache() {
        attrMap.clear();
        tintedDrawables.clear();
    }
}
