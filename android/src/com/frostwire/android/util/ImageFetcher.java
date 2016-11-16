/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.frostwire.android.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.ThemeUtils;
import com.frostwire.android.R;


/**
 * Class that translates image requests (contextual - artist album images) from inside the app for image loader (generic - images is an image)
 */
public final class ImageFetcher {

    /**
     * Default artwork
     */
    private final BitmapDrawable mDefaultArtwork;

    /**
     * Default album art
     */
    private final Bitmap mDefault;


    private static ImageFetcher sInstance = null;

    private ImageLoader mLoader;

    private UriChanger mUriChanger;

    /**
     * Creates a new instance of {@link ImageFetcher}.
     *
     * @param context The {@link Context} to use.
     */
    private ImageFetcher(final Context context) {
        mLoader = ImageLoader.getInstance(context.getApplicationContext());
        mUriChanger = new CachedDbUriChanger(context.getApplicationContext());
//        mUriChanger = new SimpleUriChanger();
        Resources mResources = context.getResources();
        // Create the default artwork
        final ThemeUtils theme = new ThemeUtils(context);
//        mDefault = ((BitmapDrawable) theme.getDrawable("default_artwork")).getBitmap();
        mDefault = ((BitmapDrawable) mResources.getDrawable(R.drawable.social_wizard_reddit)).getBitmap();
        mDefaultArtwork = new BitmapDrawable(mResources, mDefault);
        // No filter and no dither makes things much quicker
        mDefaultArtwork.setFilterBitmap(false);
        mDefaultArtwork.setDither(false);
    }

    /**
     * Used to create a singleton of the image fetcher
     *
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static final ImageFetcher getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = getInstanceSynchronized(context);
        }
        return sInstance;
    }

    private static synchronized ImageFetcher getInstanceSynchronized(final Context context) {
        if (sInstance == null) {
            sInstance = new ImageFetcher(context.getApplicationContext());
        }
        return sInstance;
    }


    /**
     * Used to fetch album images.
     */
    public void loadAlbumImage(final long albumId, final ImageView imageView) {
        final Uri albumArtUri = ImageLoader.getAlbumArtUri(albumId);
        mLoader.load(mUriChanger.changeIfNeeded(albumArtUri), imageView, R.drawable.default_artwork);
    }

    public void loadAlbumImageAndResize(final long albumId, final ImageView imageView, int width, int height, int placeholderResId) {
        final Uri albumArtUri = ImageLoader.getAlbumArtUri(albumId);
        mLoader.load(mUriChanger.changeIfNeeded(albumArtUri), imageView, width, height, placeholderResId);
    }

    public void loadAlbumImageAndResizeOrTryAlternative(final long albumId, final Uri uriRetry, ImageView target, int targetWidth, int targetHeight) {
        final Uri albumArtUri = ImageLoader.getAlbumArtUri(albumId);
        mLoader.load(mUriChanger.changeIfNeeded(albumArtUri), uriRetry, target, targetWidth, targetHeight);
    }

    /**
     * Used to fetch the current artwork.
     */
    public void loadCurrentArtwork(final ImageView imageView) {
        loadAlbumImage(MusicUtils.getCurrentAlbumId(),imageView);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadArtistImage(final String artistName, final ImageView imageView) {
        final Uri artistArtUri = ImageLoader.getArtistArtUri(artistName);
        mLoader.load(mUriChanger.changeIfNeeded(artistArtUri), imageView, R.drawable.default_artwork);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadPlaylistImage(final String playlistName, final ImageView imageView) {
        final Uri playlistArtUri = ImageLoader.getPlaylistArtUri(playlistName);
        mLoader.load(mUriChanger.changeIfNeeded(playlistArtUri), imageView, R.drawable.default_artwork);
    }

    /**
     * Methods for changing used images
     */

    public void setArtistImageUri(final String artist, Uri uri) {
        mUriChanger.setChangeBehaviour(ImageLoader.getArtistArtUri(artist), uri);
    }

    public void setAlbumImageUri(final long id, Uri uri) {
        mUriChanger.setChangeBehaviour(ImageLoader.getAlbumArtUri(id), uri);
    }

    public void setPlaylistImageUri(final String playlistName, Uri uri) {
        mUriChanger.setChangeBehaviour(ImageLoader.getPlaylistArtUri(playlistName), uri);
    }

    public void invalidateArtistImageUri(final String artist) {
        mUriChanger.removeChangeBehaviour(ImageLoader.getArtistArtUri(artist));
        mLoader.invalidate(ImageLoader.getArtistArtUri(artist));
    }

    public void invalidateAlbumImageUri(final long id) {
        mUriChanger.removeChangeBehaviour(ImageLoader.getAlbumArtUri(id));
        mLoader.invalidate(ImageLoader.getAlbumArtUri(id));
    }

    public void invalidatePlaylistImageUri(final String playlistName) {
        mUriChanger.removeChangeBehaviour(ImageLoader.getPlaylistArtUri(playlistName));
        mLoader.invalidate(ImageLoader.getPlaylistArtUri(playlistName));
    }


    public void loadAndBlur(Uri uri, ImageView mPhoto) {
        mLoader.loadAndBlur(uri, mPhoto);
    }

    public void loadAndBlurWithAlternative(Uri primaryUri, final Uri secondaryUri, final ImageView mPhoto) {
        mLoader.loadAndBlurWithAlternative(mUriChanger.changeIfNeeded(primaryUri), mUriChanger.changeIfNeeded(secondaryUri), mPhoto);
    }

    public void load(Uri uri, ImageView target) {
        mLoader.load(uri, target);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight) {
        mLoader.load(uri, target, targetWidth, targetHeight);
    }

    public void load(final Uri uri, final Uri uriRetry, final ImageView target, final int targetWidth, final int targetHeight) {
        mLoader.load(uri, uriRetry, target, targetWidth, targetHeight);
    }

    public void load(Uri uri, ImageView target, int placeholderResId) {
        mLoader.load(uri, target, placeholderResId);
    }

    public void load(Uri uri, ImageView target, int targetWidth, int targetHeight, int placeholderResId) {
        mLoader.load(uri, target, targetWidth, targetHeight, placeholderResId);
    }

    /**
     * Methods for getting the bitmap - this cannot be done on main thread
     *
     */
    public Bitmap getArtistImage(final String artist) {
        Bitmap bitmap = mLoader.get(mUriChanger.changeIfNeeded(ImageLoader.getArtistArtUri(artist)));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    public Bitmap getAlbumImage(final Long id) {
        Bitmap bitmap = mLoader.get(mUriChanger.changeIfNeeded(ImageLoader.getAlbumArtUri(id)));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    public Bitmap getPlaylistImage(final String playlistName) {
        //todo make it work
        Bitmap bitmap = mLoader.get(mUriChanger.changeIfNeeded(ImageLoader.getPlaylistArtUri(playlistName)));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    /**
     * @return The deafult artwork
     */
    public Bitmap getDefaultArtwork() {
        return mDefault;
    }

}
