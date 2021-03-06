package org.bottiger.podcast.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.Chronometer;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;

import org.bottiger.podcast.player.SoundWavesPlayerBase;
import org.bottiger.podcast.player.exoplayer.ExoPlayerWrapper;
import org.bottiger.podcast.provider.IEpisode;

import static android.os.SystemClock.elapsedRealtime;

/**
 * Created by apl on 25-03-2015.
 */
public class TextViewObserver extends Chronometer implements ExoPlayer.EventListener {

    protected IEpisode mEpisode = null;
    private boolean mIsTicking = false;

    public TextViewObserver(Context context) {
        super(context);
    }

    public TextViewObserver(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextViewObserver(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TextViewObserver(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setEpisode(@NonNull IEpisode argEpisode) {
        mEpisode = argEpisode;
    }

    public IEpisode getEpisode() {
        return mEpisode;
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, @SoundWavesPlayerBase.PlayerState int playbackState) {

        // This happens when the playlist is empty and an episode is about to be played.
        // The state change fires prior to the binding.
        if (mEpisode == null)
            return;

        long base = elapsedRealtime() - mEpisode.getOffset();
        setBase(base);
        boolean isPlaying = playbackState == SoundWavesPlayerBase.STATE_READY && playWhenReady;
        if (isPlaying && !mIsTicking) {
            start();
        } else if (!isPlaying && mIsTicking) {
            stop();
        }
        mIsTicking = isPlaying;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity() {

    }
}
