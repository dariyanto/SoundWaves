package org.bottiger.podcast.Player;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.RemoteControlClient;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.bottiger.podcast.R;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.service.PlayerService;

/**
 * Created by apl on 12-02-2015.
 *
 * http://stackoverflow.com/questions/28124708/android-notification-mediastyle-not-responding-to-mediasession-updates
 *
 */
@TargetApi(21)
public class PlayerStateManager {

    private static final String SESSION_TAG = "SWMediaSession";

    private FeedItem mEpisode;
    private Bitmap mAlbumArt;

    private MediaSession mSession;
    private PlayerService mPlaserService;

    private Target target = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            Log.d("RemoteController", "Updating remote control (with background)");
            mAlbumArt = bitmap;
            //updateSimpleMetaData(editor, episode);
            updateState(mEpisode, false);
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            Log.d("RemoteController", "BACKGROUND failed to load");
            return;
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            return;
        }
    };

    public PlayerStateManager(@NonNull PlayerService argService) {
        if (Build.VERSION.SDK_INT < 21) {
            throw new IllegalStateException("This should never have been called using this SDK level");
        }
        mPlaserService = argService;
        mSession = new MediaSession(argService, SESSION_TAG);
        mSession.setActive(true);
        mSession.setFlags(  MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    public void release() {
        mSession.release();
    }

    public MediaSession.Token getToken() {
        return mSession.getSessionToken();
    }

    public void updateState(@NonNull FeedItem argEpisode) {
        updateState(argEpisode, true);
    }

    public void updateState(@NonNull FeedItem argEpisode, boolean updateAlbumArt) {
        String albumNull = mAlbumArt == null ? "Null" : "Not null";
        Log.d("PlayerStateManager", "updateState: updateAlbumState: " + updateAlbumArt + " album: " + albumNull);
        MediaMetadata.Builder mMetaBuilder = new MediaMetadata.Builder();

        populateFastMediaMetadata(mMetaBuilder, argEpisode);

        if (updateAlbumArt || mAlbumArt == null) {
            mEpisode = argEpisode;
            argEpisode.getArtworAsync(mPlaserService, target);
        } else {
            
            mMetaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mAlbumArt);
            mMetaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, mAlbumArt);
            mMetaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, mAlbumArt);
        }

        PlaybackState.Builder stateBuilder = getPlaybackState();

        mSession.setMetadata(mMetaBuilder.build());
        mSession.setPlaybackState(stateBuilder.build());
    }

    private void populateFastMediaMetadata(@NonNull MediaMetadata.Builder mMetaBuilder, @NonNull FeedItem argEpisode) {
        //Subscription subscription = argEpisode.getSubscription(mPlaserService);

        mMetaBuilder.putText(MediaMetadata.METADATA_KEY_TITLE, argEpisode.getTitle());
        mMetaBuilder.putText(MediaMetadata.METADATA_KEY_ALBUM, "yo");
        mMetaBuilder.putText(MediaMetadata.METADATA_KEY_ARTIST, argEpisode.getAuthor());
        mMetaBuilder.putText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, "ko");
        mMetaBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, 3);
        mMetaBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, 15);
        mMetaBuilder.putLong(MediaMetadata.METADATA_KEY_DISC_NUMBER, 1);
    }

    private PlaybackState.Builder getPlaybackState() {
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder();

        stateBuilder.setActiveQueueItemId(MediaSession.QueueItem.UNKNOWN_ID);

        long actions = PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

        stateBuilder.setActions(actions);
        stateBuilder.setState(PlaybackState.STATE_PLAYING, 0, 1.0f);

        return stateBuilder;
    }
}
