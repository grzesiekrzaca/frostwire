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

package com.andrew.apollo.cache;

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
import com.frostwire.android.util.ImageLoader;


/**
 * Class that translates image requests (contextual - artist album images) from inside the app for image loader (generic - images is an image)
 */
public class ImageFetcher {

    /**
     * Default artwork
     */
    private final BitmapDrawable mDefaultArtwork;

    /**
     * The resources to use
     */
    private final Resources mResources;

    /**
     * Default album art
     */
    private final Bitmap mDefault;

    /**
     * The Context to use
     */
    protected Context mContext;


    private static ImageFetcher sInstance = null;

    /**
     * Creates a new instance of {@link ImageFetcher}.
     *
     * @param context The {@link Context} to use.
     */
    private ImageFetcher(final Context context) {
        mContext = context.getApplicationContext();
        mResources = mContext.getResources();
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
    public void loadAlbumImage(final String artistName, final String albumName, final long albumId,
            final ImageView imageView) {
        loadImage(artistName, albumName, albumId, imageView,
                ImageType.ALBUM);
    }

    /**
     * Used to fetch the current artwork.
     */
    public void loadCurrentArtwork(final ImageView imageView) {
        loadImage(
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                imageView, ImageType.ALBUM);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadArtistImage(final String key, final ImageView imageView) {
        loadImage(key, null, -1, imageView, ImageType.ARTIST);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadPlaylistImage(final String key, final ImageView imageView) {
        loadImage(key, null, -1, imageView, ImageType.ARTIST);
    }

    /**
     * Methods for changing the default images
     */

    public void setArtistImageUri(final String artist, Uri uri) {
        //todo save
    }

    public void setAlbumImageUri(final long id, Uri uri) {
        //todo save
    }

    public void setPlaylistImageUri (final String playlistName, Uri uri ) {
        //todo save
    }

    public void invalidateArtistImageUri(final String artist) {
        //todo save and clear cache
    }

    public void invalidateAlbumImageUri(final long id) {
        //todo save and clear cache
    }

    public void invalidatePlaylistImageUri (final String playlistName) {
        //todo save and clear cache
    }

    public Bitmap getArtistImage(final String artist) {
        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        Bitmap bitmap = loader.get(ImageLoader.getArtistArtUri(artist));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    public Bitmap getAlbumImage(final Long id) {
        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        Bitmap bitmap = loader.get(ImageLoader.getAlbumArtUri(id));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    public Bitmap getPlaylistImage(final String playlistName) {
        //todo make it work
        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        Bitmap bitmap = loader.get(ImageLoader.getPlaylistArtUri(playlistName));
        return (bitmap != null) ? bitmap : getDefaultArtwork();
    }

    /**
     * @return The deafult artwork
     */
    public Bitmap getDefaultArtwork() {
        return mDefault;
    }


    /**
     * Called to fetch the artist or ablum art.
     *
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    protected void loadImage(final String artistName, final String albumName,
                             final long albumId, final ImageView imageView, final ImageType imageType) {
        if (imageView == null) {
            return;
        }

        final ImageLoader loader = ImageLoader.getInstance(mContext.getApplicationContext());
        if (ImageType.ALBUM.equals(imageType)) {
            final Uri albumArtUri = loader.getAlbumArtUri(albumId);
            loader.load(albumArtUri, imageView, R.drawable.default_artwork);
        } else if (ImageType.ARTIST.equals(imageType)) {
            final Uri artistArtUri = loader.getArtistArtUri(artistName);
            loader.load(artistArtUri, imageView, R.drawable.default_artwork);
        }
    }

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM;
    }



}
