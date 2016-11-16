package com.frostwire.android.util;


import android.net.Uri;

/**
 * Interface used for Uri translations used to change uris of albums, artists, playlists to custom uris
 */
public interface UriChanger {

    Uri changeIfNeeded(Uri uri);

    void setChangeBehaviour(Uri baseUri, Uri alternateUri);

    void removeChangeBehaviour(Uri baseUri);

}
