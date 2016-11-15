package com.frostwire.android.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

/**
 * Used to change Uri of a request to custom Uri set by the user to facilitate custom image logic
 */

public class DbUriChanger implements UriChanger{

    private static final Logger LOG = Logger.getLogger(DbUriChanger.class);

    private final AlternateUriStore mAlternateUriStore;

    public DbUriChanger(Context context){
        mAlternateUriStore = new AlternateUriStore(context);
    }

    public Uri changeIfNeeded(Uri uri) {
        Uri alternateUri = getAlternateUri(uri);
        return alternateUri==null?uri:alternateUri;
    }

    private Uri getAlternateUri(Uri uri) {
        return mAlternateUriStore.getAlternateUri(uri);
    }

    @Override
    public void setChangeBehaviour(Uri baseUri, Uri alternateUri) {
        mAlternateUriStore.associate(baseUri.toString(),alternateUri.toString());
    }

    @Override
    public void removeChangeBehaviour(Uri baseUri) {
        mAlternateUriStore.disassociate(baseUri.toString());
    }

    private class AlternateUriStore extends SQLiteOpenHelper {

        /* Version constant to increment when the database should be rebuilt */
        private static final int VERSION = 1;

        private static final String DATABASE_NAME = "alternateuri.db";

        private static final String TABLE_NAME = "uri_association";

        private static final String BASE_URI_COLUMN_NAME = "base_uri";

        private static final String ALTERNATE_URI_COLUMN_NAME = "alternate_uri";

        /**
         * Columns of interest when querying the db
         */
        private final String[] PROJECTION = new String[]{
                BASE_URI_COLUMN_NAME, ALTERNATE_URI_COLUMN_NAME
        };

        /**
         * Where clause when querying the db
         */
        private final String SELECTION = BASE_URI_COLUMN_NAME + "=?";

        AlternateUriStore(final Context context) {
            super(context, DATABASE_NAME, null, VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" + BASE_URI_COLUMN_NAME
                    + " TEXT PRIMARY KEY NOT NULL," + ALTERNATE_URI_COLUMN_NAME + " TEXT NOT NULL);");
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }

        void associate(final String baseUri, final String alternateUri) {
            if (StringUtils.isNullOrEmpty(baseUri) || StringUtils.isNullOrEmpty(alternateUri) ) {
                LOG.warn("Invalid uri association");
                return;
            }
            final SQLiteDatabase database = getWritableDatabase();
            final ContentValues values = new ContentValues(2);
            values.put(BASE_URI_COLUMN_NAME, baseUri);
            values.put(ALTERNATE_URI_COLUMN_NAME, alternateUri);

            database.beginTransaction();

            database.delete(TABLE_NAME, BASE_URI_COLUMN_NAME + " = ?", new String[] {
                    baseUri
            });
            database.insert(TABLE_NAME, null, values);
            database.setTransactionSuccessful();

            database.endTransaction();

        }

        Uri getAlternateUri(final Uri baseUri) {
            String baseUriString = baseUri.toString();
            if (StringUtils.isNullOrEmpty(baseUriString)) {
                return null;
            }

            try {
                final SQLiteDatabase database = getReadableDatabase();

                final String[] is = new String[]{
                        baseUriString
                };
                Cursor cursor = database.query(TABLE_NAME, PROJECTION, SELECTION, is, null,
                        null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    final String uri = cursor.getString(cursor.getColumnIndexOrThrow(ALTERNATE_URI_COLUMN_NAME));
                    cursor.close();

                    return Uri.parse(uri);
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable e) {
                LOG.error("Database read problem");
                return null;
            }
            return null;
        }

        /**
         * @param baseUri Key to remove
         */
        void disassociate(final String baseUri) {
            final SQLiteDatabase database = getReadableDatabase();
            database.delete(TABLE_NAME, BASE_URI_COLUMN_NAME + " = ?", new String[] {
                    baseUri
            });

        }


    }


}
