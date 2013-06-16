package org.bottiger.podcast.service;

import java.io.IOException;

import org.bottiger.podcast.SettingsActivity;
import org.bottiger.podcast.listeners.PlayerStatusListener;
import org.bottiger.podcast.notification.NotificationPlayer;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.receiver.HeadsetReceiver;
import org.bottiger.podcast.utils.Log;
import org.bottiger.podcast.utils.Playlist;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.widget.SimpleCursorAdapter;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * The service which handles the audio player. This is responsible for playing
 * including controlling the playback. Play, pause, stop etc.
 * 
 * @author Arvid Böttiger
 */
public class PlayerService extends Service implements
		AudioManager.OnAudioFocusChangeListener {

	private static enum NextTrack {
		NONE, NEW_TRACK, NEXT_IN_PLAYLIST
	}

	private static NextTrack nextTrack = NextTrack.NEXT_IN_PLAYLIST;

	private static final int FADEIN = 0;
	private static final int TRACK_ENDED = 1;
	private static final int SERVER_DIED = 2;
	public static final int PlayerService_STATUS = 1;

	private static final long REPEAT_MODE_NO_REPEAT = 0;
	private static final long REPEAT_MODE_REPEAT = 1;
	private static final long REPEAT_MODE_REPEAT_ONE = 2;

	private static final String WHERE = ItemColumns.STATUS + ">"
			+ ItemColumns.ITEM_STATUS_MAX_DOWNLOADING_VIEW + " AND "
			+ ItemColumns.STATUS + "<"
			+ ItemColumns.ITEM_STATUS_MAX_PLAYLIST_VIEW + " AND "
			+ ItemColumns.FAIL_COUNT + " > 100";

	private static final String ORDER = ItemColumns.FAIL_COUNT + " ASC";

	private final Log log = Log.getLog(getClass());

	MyPlayer mPlayer = null;
	private NotificationManager mNotificationManager;
	private NotificationPlayer mNotificationPlayer;

	private FeedItem mItem = null;
	private boolean mUpdate = false;
	private boolean mResumeAfterCall = false;

	private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				int ringvolume = audioManager
						.getStreamVolume(AudioManager.STREAM_RING);
				if (ringvolume > 0) {
					mResumeAfterCall = (isPlaying() || mResumeAfterCall)
							&& (mItem != null);
					pause();
				}
			} else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
				mResumeAfterCall = (isPlaying() || mResumeAfterCall)
						&& (mItem != null);
				pause();
			} else if (state == TelephonyManager.CALL_STATE_IDLE) {
				if (mResumeAfterCall) {

					// never fade in before I have stopped it from doing so when
					// it shouldn't
					// startAndFadeIn();
					mResumeAfterCall = false;
				}
			}
		}
	};

	private void startAndFadeIn() {
		handler.sendEmptyMessageDelayed(FADEIN, 10);
	}

	// AudioManager
	private AudioManager mAudioManager;
	private ComponentName mControllerComponentName;

	@Override
	public void onCreate() {
		super.onCreate();
		mPlayer = new MyPlayer();
		mPlayer.setHandler(handler);
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		tmgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		this.mControllerComponentName = new ComponentName(this,
				HeadsetReceiver.class);
		log.debug("onCreate(): " + mControllerComponentName);
		this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
			// Pause playback
			pause();
		} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			// Resume playback
			start();
		} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
			mAudioManager
					.unregisterMediaButtonEventReceiver(mControllerComponentName);
			mAudioManager.abandonAudioFocus(this);
			// Stop playback
			stop();
		}
	}

	private class MyPlayer {
		private MediaPlayer mMediaPlayer = new MediaPlayer();

		private Handler mHandler;
		private boolean mIsInitialized = false;
		
		private boolean isPreparingMedia = false;
		
		int bufferProgress = 0;

		int startPos = 0;

		public MyPlayer() {
			// mMediaPlayer.setWakeMode(PlayerService.this,
			// PowerManager.PARTIAL_WAKE_LOCK);
		}

		public void setDataSourceAsync(String path, int startPos) {
			try {
				mMediaPlayer.reset();
				mMediaPlayer.setDataSource(path);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

				this.startPos = startPos;
				this.isPreparingMedia = true;
				
				mMediaPlayer.setOnPreparedListener(preparedlistener);
				mMediaPlayer.prepareAsync();
			} catch (IOException ex) {
				// TODO: notify the user why the file couldn't be opened
				mIsInitialized = false;
				return;
			} catch (IllegalArgumentException ex) {
				// TODO: notify the user why the file couldn't be opened
				mIsInitialized = false;
				return;
			}
			mMediaPlayer.setOnCompletionListener(listener);
			mMediaPlayer.setOnBufferingUpdateListener(bufferListener);
			mMediaPlayer.setOnErrorListener(errorListener);

			mIsInitialized = true;
		}

		public boolean isInitialized() {
			return mIsInitialized;
		}

		public void toggle() {
			if (mMediaPlayer.isPlaying())
				pause();
			else
				start();
		}

		public void start() {
			Notification notification = notifyStatus();

			// Request audio focus for playback
			int result = mAudioManager.requestAudioFocus(PlayerService.this,
			// Use the music stream.
					AudioManager.STREAM_MUSIC,
					// Request permanent focus.
					AudioManager.AUDIOFOCUS_GAIN);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				mAudioManager
						.registerMediaButtonEventReceiver(mControllerComponentName);
				mMediaPlayer.start();
				startForeground(mNotificationPlayer.getNotificationId(),
						notification);
				PlayerStatusListener
						.updateStatus(PlayerStatusListener.STATUS.PLAYING);
			}
		}

		public void stop() {
			mMediaPlayer.reset();
			mIsInitialized = false;
			stopForeground(true);
			PlayerStatusListener
					.updateStatus(PlayerStatusListener.STATUS.STOPPED);
		}

		public void release() {
			dis_notifyStatus();
			stop();
			mMediaPlayer.release();
			mAudioManager
					.unregisterMediaButtonEventReceiver(mControllerComponentName);
			mIsInitialized = false;
		}

		/**
		 * Test of the player is playing something right now
		 * 
		 * @return Is the player playing right now
		 */
		public boolean isPlaying() {
			return mMediaPlayer.isPlaying();
		}

		/**
		 * Pause the current playing item
		 */
		public void pause() {
			mMediaPlayer.pause();
			PlayerStatusListener
					.updateStatus(PlayerStatusListener.STATUS.PAUSED);
		}

		@Deprecated
		public int getBufferProgress() {
			return this.bufferProgress;
		}

		public void setHandler(Handler handler) {
			mHandler = handler;
		}

		MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				FeedItem item = PlayerService.this.mItem;
				// item.markAsListened();
				// item.update(getContentResolver());
				
				if (!isPreparingMedia)
					mHandler.sendEmptyMessage(TRACK_ENDED);

			}
		};

		MediaPlayer.OnBufferingUpdateListener bufferListener = new MediaPlayer.OnBufferingUpdateListener() {
			@Override
			public void onBufferingUpdate(MediaPlayer mp, int percent) {
				MyPlayer.this.bufferProgress = percent;
			}
		};

		MediaPlayer.OnPreparedListener preparedlistener = new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				// notifyChange(ASYNC_OPEN_COMPLETE);
				mp.seekTo(startPos);
				start();
				isPreparingMedia = false;
				PlayerService.setNextTrack(NextTrack.NEXT_IN_PLAYLIST);
			}
		};

		MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				log.debug("onError() " + what + " : " + extra);

				switch (what) {
				case MediaPlayer.MEDIA_ERROR_SERVER_DIED:

					dis_notifyStatus();
					mIsInitialized = false;
					mMediaPlayer.release();

					mMediaPlayer = new MediaPlayer();
					mHandler.sendMessageDelayed(
							mHandler.obtainMessage(SERVER_DIED), 2000);
					return true;
				default:
					break;
				}
				return false;
			}
		};

		public long duration() {
			return mMediaPlayer.getDuration();
		}

		public long position() {
			return mMediaPlayer.getCurrentPosition();
		}

		public long seek(long whereto) {
			mMediaPlayer.seekTo((int) whereto);
			return whereto;
		}

		public void setVolume(float vol) {
			mMediaPlayer.setVolume(vol, vol);
		}
	}

	private final Handler handler = new Handler() {
		float mCurrentVolume = 1.0f;

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case FADEIN:
				if (!isPlaying()) {
					mCurrentVolume = 0f;
					mPlayer.setVolume(mCurrentVolume);
					start();
					handler.sendEmptyMessageDelayed(FADEIN, 10);
				} else {
					mCurrentVolume += 0.01f;
					if (mCurrentVolume < 1.0f) {
						handler.sendEmptyMessageDelayed(FADEIN, 10);
					} else {
						mCurrentVolume = 1.0f;
					}
					mPlayer.setVolume(mCurrentVolume);
				}
				break;
			case TRACK_ENDED:
				long repeat_mode = getPref();

				if (mItem != null) {

					if (getNextTrack() == NextTrack.NEXT_IN_PLAYLIST) {
						long nextItemId = getNextId();

						if (nextItemId == -1) {
							dis_notifyStatus();
							mPlayer.stop();
						} else {
							playNext(nextItemId);
						}
						mUpdate = true;
					}

					// deprecated - I think
					/*
					if (repeat_mode == REPEAT_MODE_REPEAT_ONE) {
						long id = mItem.id;
						mItem = null;
						play(id);
					} else if (repeat_mode == REPEAT_MODE_REPEAT) {
						if (nextItemId == -1) {
							// nextItemId = getFirst();
						}

						if (nextItemId > -1)
							play(nextItemId);

					} else if (repeat_mode == REPEAT_MODE_NO_REPEAT) {
						if (nextItemId > -1)
							play(nextItemId);

					}
					*/

				}

				break;

			case SERVER_DIED:
				break;

			}
		}
	};

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		log.debug("onStart()");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mPlayer != null) {
			mPlayer.release();
		}

		log.debug("onDestroy()");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.debug("onLowMemory()");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	/**
	 * Hide the notification
	 */
	private void dis_notifyStatus() {
		// //mNotificationManager.cancel(R.layout.playing_episode);
		// setForeground(false);
		if (mNotificationPlayer != null)
			mNotificationPlayer.hide();
	}

	/**
	 * Display a notification with the current podcast
	 */
	private Notification notifyStatus() {

		if (mNotificationPlayer == null)
			mNotificationPlayer = new NotificationPlayer(this, mItem);

		return mNotificationPlayer.show();
	}

	public void playNext(long nextId) {
		// assert playlistAdapter != null;

		// Cursor firstItem = (Cursor) playlistAdapter.getItem(0);
		// playlistAdapter.notifyDataSetChanged();
		if (mItem != null)
			mItem.trackEnded(getContentResolver());
		play(nextId);
	}

	public void play(long id) {

		// Pause the current episode in order to save the current state
		if (mPlayer.isPlaying())
			mPlayer.pause();

		if (mItem != null) {
			if ((mItem.id == id) && mPlayer.isInitialized()) {
				if (mPlayer.isPlaying() == false) {
					start();
				}
				return;
			}

			if (mPlayer.isPlaying()) {
				mItem.updateOffset(getContentResolver(), mPlayer.position());
				stop();
			}
		}

		mItem = FeedItem.getById(getContentResolver(), id);

		if (mItem == null)
			return;

		mItem.setPriority(null, getApplication());
		mItem.update(getContentResolver());

		String dataSource = mItem.isDownloaded() ? mItem.getAbsolutePath()
				: mItem.getURL();

		int offset = mItem.offset < 0 ? 0 : mItem.offset;
		mPlayer.setDataSourceAsync(dataSource, offset);
	}

	public void toggle(long id) {
		if (mPlayer.isPlaying() == false && mItem != null) {
			play(id);
		} else {
			mPlayer.pause();
		}
	}

	@Deprecated
	public void toggle() {
		if (mPlayer.isPlaying() == false && mItem != null) {
			mPlayer.start();
		} else {
			mPlayer.pause();
		}
	}

	public void start() {
		if (mPlayer.isPlaying() == false) {
			mPlayer.start();
		}
	}

	public void pause() {
		if (mPlayer.isPlaying() == false) {
			return;
		}

		if ((mItem != null)) {
			mItem.status = ItemColumns.ITEM_STATUS_PLAY_PAUSE;
			mItem.updateOffset(getContentResolver(), mPlayer.position());
		} else {
			log.error("playing but no item!!!");

		}
		dis_notifyStatus();

		mPlayer.pause();
	}

	public void stop() {
		pause();
		mPlayer.stop();
		mItem = null;
		dis_notifyStatus();
		mUpdate = true;
	}

	public boolean isInitialized() {
		return mPlayer.isInitialized();
	}

	public boolean isPlaying() {
		return mPlayer.isPlaying();
	}

	/**
	 * Test of the player is on pause right now
	 * 
	 * @return True if the player is on pause right now
	 */
	public boolean isOnPause() {
		if (isPlaying() || getCurrentItem() == null)
			return false;

		return true;
	}

	public long seek(long offset) {
		offset = offset < 0 ? 0 : offset;

		return mPlayer.seek(offset);

	}

	public long position() {
		return mPlayer.position();
	}

	public long duration() {
		return mPlayer.duration();
	}

	public int bufferProgress() {
		int test = mPlayer.bufferProgress;
		return test;// mPlayer.bufferProgress;
	}

	public void setCurrentItem(FeedItem item) {
		stop();
		mItem = item;
	}

	public FeedItem getCurrentItem() {
		return mItem;
	}

	public boolean getUpdateStatus() {
		return mUpdate;
	}

	public void setUpdateStatus(boolean update) {
		mUpdate = update;
	}

	private FeedItem getFirst() {
		Cursor cursor = null;
		try {

			cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, WHERE, null, ORDER);
			if (cursor == null) {
				return null;
			}
			cursor.moveToFirst();

			FeedItem item = FeedItem.getByCursor(cursor);
			return item;
		} catch (Exception e) {

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	public long getNextId() {
		return (long) Playlist.nextId();
	}

	/**
	 * Returns whether the next episode to be played should come from the
	 * playlist, or somewhere else
	 * 
	 * @return The type of episode to be played next
	 */
	public static NextTrack getNextTrack() {
		return nextTrack;
	}

	/**
	 * 
	 * @param nextTrack
	 */
	public static void setNextTrack(NextTrack nextTrack) {
		PlayerService.nextTrack = nextTrack;
	}

	public FeedItem getPrev(FeedItem item) {
		FeedItem prev_item = null;
		FeedItem curr_item = null;

		Cursor cursor = null;

		if (item == null) {
			FeedItem.getByCursor(cursor);
			return null;
		}

		try {
			cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, WHERE, null, ORDER);
			if (cursor == null) {
				return null;
			}
			cursor.moveToFirst();

			do {
				prev_item = curr_item;
				curr_item = FeedItem.getByCursor(cursor);

				if ((curr_item != null) && (item.id == curr_item.id)) {
					return prev_item;
				}

			} while (cursor.moveToNext());

		} catch (Exception e) {

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	private long getPref() {
		SharedPreferences pref = getSharedPreferences(
				SettingsActivity.HAPI_PREFS_FILE_NAME, Context.MODE_PRIVATE);
		return pref.getLong("pref_repeat", 0);

	}

	private final IBinder binder = new PlayerBinder();

	public class PlayerBinder extends Binder {
		public PlayerService getService() {
			return PlayerService.this;
		}
	}

}