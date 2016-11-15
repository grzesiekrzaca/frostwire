package com.frostwire.android.util;

import android.net.Uri;

import com.frostwire.util.StringUtils;

import java.util.HashMap;

/**
 * Created by Grzesiek on 2016-11-15.
 */

public class SimpleUriChanger implements UriChanger {

    private HashMap<String, String> map =  new HashMap<>();

    @Override
    public Uri changeIfNeeded(Uri uri) {
        String uriString = map.get(uri.toString());
        return StringUtils.isNullOrEmpty(uriString)?uri:Uri.parse(uriString);
    }

    @Override
    public void setChangeBehaviour(Uri baseUri, Uri alternateUri) {
        map.put(baseUri.toString(),alternateUri.toString());
    }

    @Override
    public void removeChangeBehaviour(Uri baseUri) {
        map.remove(baseUri.toString());
    }
}
