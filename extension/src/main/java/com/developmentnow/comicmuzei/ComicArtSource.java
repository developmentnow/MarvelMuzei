package com.developmentnow.comicmuzei;

import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.muzei.api.Artwork;


import com.developmentnow.comicmuzei.R;
import retrofit.ErrorHandler;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

/**
 * Created by james on 2/17/14.
 */
public class ComicArtSource extends RemoteMuzeiArtSource {
    private static final String TAG = "ComicMuzei";
    private static final String SOURCE_NAME = "ComicArtSource";

    private static final int ROTATE_TIME_MILLIS = 24 * 60 * 60 * 1000; // Rotate Every Day
    private static final int RETRY_TIME_MILLIS = 60 * 60 * 1000; // Retry in an hour

    public ComicArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer("http://marvel.communityfoc.us")
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError retrofitError) {
                        if (retrofitError == null || retrofitError.getResponse() == null) {
                            scheduleUpdate(System.currentTimeMillis() + RETRY_TIME_MILLIS);
                            return retrofitError;
                        }
                        Integer statusCode = retrofitError.getResponse().getStatus();
                        if (retrofitError.isNetworkError()
                                || (500 <= statusCode && statusCode < 600)) {
                            return new RetryException();
                        }
                        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
                        return retrofitError;
                    }
                })
                .build();

        ComicService service = restAdapter.create(ComicService.class);

        ComicService.Comic comic = null;
        ComicService.Character character = null;
        if (prefs.getBoolean(SettingsActivity.KEY_PREF_BY_CHARACTER, false)) {
            String[] raw = TextUtils.split(prefs.getString(SettingsActivity.KEY_PREF_CHARACTERS, ""), SettingsActivity.SEPARATOR);
            String chars = TextUtils.join(",", raw);
            Log.d("CM", "Get Characters: " + chars);
            comic = service.getComicByCharacter(chars);
        } else if (prefs.getBoolean(SettingsActivity.KEY_PREF_BY_ARTIST, false)) {
            String[] raw = TextUtils.split(prefs.getString(SettingsActivity.KEY_PREF_ARTISTS, ""), SettingsActivity.SEPARATOR);
            String artists = TextUtils.join(",", raw);
            Log.d("CM", "Get Artists: " + artists);
            comic = service.getComicByArtist(artists);
        } else {
            character = service.getCharacter();
        }

        if (character == null && comic == null) {
            throw new RetryException();
        }
        String attribution = getResources().getString(R.string.attribution);
        String description = "";

        //Display Character of the Day
        if (character != null) {
            if (character.description != null && !character.description.equals(""))
                description = character.description + "\n";
            description += attribution;
            publishArtwork(new Artwork.Builder()
                    .title(character.name)
                    .byline(description)
                    .imageUri(Uri.parse(character.thumbnail.path + character.thumbnail.extension))
                    .token(Integer.toString(character.id))
                    .viewIntent(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(character.urls.get(0).url)))
                    .build());
        }

        // Display Comic
        else if (comic != null && comic.creators != null) {

            for (int i = 0; i < comic.creators.items.length; i++) {
                if (comic.creators.items[i].role.equals("penciller (cover)")) {
                    description = comic.creators.items[i].name + "\n";
                    break;
                }
            }
            description += attribution;
            publishArtwork(new Artwork.Builder()
                    .title(comic.title)
                    .byline(description)
                    .imageUri(Uri.parse(comic.thumbnail.path + comic.thumbnail.extension))
                    .token(Integer.toString(comic.id))
                    .viewIntent(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(comic.urls.get(0).url)))
                    .build());
        }
        scheduleUpdate(System.currentTimeMillis() + ROTATE_TIME_MILLIS);
    }


}
