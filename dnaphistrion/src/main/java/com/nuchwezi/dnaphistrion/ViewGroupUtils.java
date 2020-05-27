package com.nuchwezi.dnaphistrion;

import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;
import java.util.List;

/**
 * From: https://gist.github.com/orip/5{2}6{5}
 * Based on http://stackoverflow.com/a/8831593/37020 by by Shlomi Schwartz
 * License: MIT
 */
public class ViewGroupUtils {
    public static List<View> getViewsByTag(View root, String tag) {
        List<View> result = new LinkedList<View>();

        if (root instanceof ViewGroup) {
            final int childCount = ((ViewGroup) root).getChildCount();
            for (int i = 0; i < childCount; i++) {
                result.addAll(getViewsByTag(((ViewGroup) root).getChildAt(i), tag));
            }
        }

        final Object rootTag = root.getTag();
        // handle null tags, code from Guava's Objects.equal
        if (tag == rootTag || (tag != null && tag.equals(rootTag))) {
            result.add(root);
        }

        return result;
    }

    public static View getFirstViewByTag(View root, String tag) {
        List<View> result = getViewsByTag(root, tag);
        return result.get(0);
    }
}