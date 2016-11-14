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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import com.andrew.apollo.Config;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.ThemeUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.ImageLoader;


/**
 * Class that translates image requests (contextual - artist album images) from inside the app for image loader (generic - images is an image)
 */
public class ImageFetcher {

    /**
     * Default transition drawable fade time
     */
    private static final int FADE_IN_TIME = 200;

    /**
     * Default artwork
     */
    private final BitmapDrawable mDefaultArtwork;

    /**
     * The resources to use
     */
    private final Resources mResources;

    /**
     * First layer of the transition drawable
     */
    private final ColorDrawable mCurrentDrawable;

    /**
     * Layer drawable used to cross fade the result from the worker
     */
    private final Drawable[] mArrayDrawable;

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
    public ImageFetcher(final Context context) {
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
        // Create the transparent layer for the transition drawable
        mCurrentDrawable = new ColorDrawable(mResources.getColor(R.color.transparent));
        // A transparent image (layer 0) and the new result (layer 1)
        mArrayDrawable = new Drawable[2];
        mArrayDrawable[0] = mCurrentDrawable;
        // XXX The second layer is set in the worker task.
    }

    /**
     * Used to create a singleton of the image fetcher
     *
     * @param context The {@link Context} to use
     * @return A new instance of this class.
     */
    public static final ImageFetcher getInstance(final Context context) {
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
        loadImage(generateAlbumCacheKey(albumName, artistName), artistName, albumName, albumId, imageView,
                ImageType.ALBUM);
    }

    /**
     * Used to fetch the current artwork.
     */
    public void loadCurrentArtwork(final ImageView imageView) {
        loadImage(generateAlbumCacheKey(MusicUtils.getAlbumName(), MusicUtils.getArtistName()),
                MusicUtils.getArtistName(), MusicUtils.getAlbumName(), MusicUtils.getCurrentAlbumId(),
                imageView, ImageType.ALBUM);
    }

    /**
     * Used to fetch artist images.
     */
    public void loadArtistImage(final String key, final ImageView imageView) {
        loadImage(key, key, null, -1, imageView, ImageType.ARTIST);
    }

    /**
     * @param key The key used to find the image to remove
     */
    public void removeFromCache(final String key) {
//        if (mImageCache != null) {
//            mImageCache.removeFromCache(key);
//        }
    }

    /**
     * @param key The key used to find the image to return
     */
    public Bitmap getCachedBitmap(final String key) {
        Log.e("IF","asked for cached bitmap with key: "+(key==null?"null":key));
//        if (mImageCache != null) {
//            return mImageCache.getCachedBitmap(key);
//        }
        return getDefaultArtwork();
    }


    /**
     * Finds cached or downloads album art. Used in {@link MusicPlaybackService}
     * to set the current album art in the notification and lock screen
     *
     * @param albumName The name of the current album
     * @param albumId The ID of the current album
     * @param artistName The album artist in case we should have to download
     *            missing artwork
     * @return The album art as an {@link Bitmap}
     */

    //musicplaybackservice
    public Bitmap getArtwork(final String albumName, final long albumId, final String artistName) {
        Log.e("IF","asked for Artwork with params albumName: "+(albumName==null?"null":albumName) + " albumId: " + albumId + " artistName "+ (artistName==null?"null":artistName));
        // Check the disk cache
//        Bitmap artwork = null;
//
//        if (artwork == null && albumName != null && mImageCache != null) {
//            artwork = mImageCache.getBitmapFromDiskCache(
//                    generateAlbumCacheKey(albumName, artistName));
//        }
//        if (artwork == null && albumId >= 0 && mImageCache != null) {
//            // Check for local artwork
//            artwork = mImageCache.getArtworkFromFile(mContext, albumId);
//        }
//        if (artwork != null) {
//            return artwork;
//        }
        return getDefaultArtwork();
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
     * @param key The unique identifier for the image.
     * @param artistName The artist name for the Last.fm API.
     * @param albumName The album name for the Last.fm API.
     * @param albumId The album art index, to check for missing artwork.
     * @param imageView The {@link ImageView} used to set the cached
     *            {@link Bitmap}.
     * @param imageType The type of image URL to fetch for.
     */
    protected void loadImage(final String key, final String artistName, final String albumName,
                             final long albumId, final ImageView imageView, final ImageType imageType) {
        if (key == null || imageView == null) {
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

        // First, check the memory for the image
        /**
         final Bitmap lruBitmap = mImageCache.getBitmapFromMemCache(key);
         if (lruBitmap != null && imageView != null) {
         // Bitmap found in memory cache
         imageView.setImageBitmap(lruBitmap);
         } else if (executePotentialWork(key, imageView)
         && imageView != null && !mImageCache.isDiskCachePaused()) {
         // Otherwise run the worker task
         final BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(imageView, imageType);
         final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, mDefault,
         bitmapWorkerTask);
         imageView.setImageDrawable(asyncDrawable);
         try {
         ApolloUtils.execute(false, bitmapWorkerTask, key,
         artistName, albumName, String.valueOf(albumId));
         } catch (RejectedExecutionException e) {
         // Executor has exhausted queue space, show default artwork
         imageView.setImageBitmap(getDefaultArtwork());
         }
         }*/
    }

    /**
     * Used to define what type of image URL to fetch for, artist or album.
     */
    public enum ImageType {
        ARTIST, ALBUM;
    }



    /**
     * Generates key used by album art cache. It needs both album name and artist name
     * to let to select correct image for the case when there are two albums with the
     * same artist.
     *
     * @param albumName The album name the cache key needs to be generated.
     * @param artistName The artist name the cache key needs to be generated.
     * @return
     */
    public static String generateAlbumCacheKey(final String albumName, final String artistName) {
        if (albumName == null || artistName == null) {
            return null;
        }
        return new StringBuilder(albumName)
                .append("_")
                .append(artistName)
                .append("_")
                .append(Config.ALBUM_ART_SUFFIX)
                .toString();
    }
}
