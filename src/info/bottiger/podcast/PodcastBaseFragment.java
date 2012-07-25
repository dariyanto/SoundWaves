package info.bottiger.podcast;

import info.bottiger.podcast.R;
import info.bottiger.podcast.service.PlayerService;
import info.bottiger.podcast.service.PodcastService;
import info.bottiger.podcast.utils.Log;
import info.bottiger.podcast.utils.StrUtils;

import android.app.Activity;
import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.support.v4.app.ListFragment;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

/* Copy of PodcastBaseActivity */
public class PodcastBaseFragment extends ListFragment {

	private static final int SWIPE_MIN_DISTANCE = 120;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

	public static final int COLUMN_INDEX_TITLE = 1;

	// protected static PodcastService mServiceBinder = null;
	public PlayerService mPlayerServiceBinder = null;
	protected static ComponentName mService = null;
	// protected final Log log = Log.getLog(getClass());

	protected SimpleCursorAdapter mAdapter;
	// protected Cursor mCursor = null;

	// protected boolean mInit = false;
	protected Intent mPrevIntent = null;

	protected Intent mNextIntent = null;

	OnItemSelectedListener mListener;

	protected final Log log = Log.getLog(getClass());

	private long mLastSeekEventTime;
	private boolean mFromTouch;

	private static final int REFRESH = 1;
	private static final int PLAYITEM = 2;

	private boolean mShow = true;

	private TextView mCurrentTime = null;
	private SeekBar mProgress = null;

	public TextView getCurrentTime() {
		return mCurrentTime;
	}

	public void setCurrentTime(TextView mCurrentTime) {
		this.mCurrentTime = mCurrentTime;
	}

	public SeekBar getProgress() {
		return mProgress;
	}

	public void setProgress(SeekBar mProgress) {
		this.mProgress = mProgress;
	}

	public ServiceConnection playerServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mPlayerServiceBinder = ((PlayerService.PlayerBinder) service)
					.getService();
			// log.debug("onServiceConnected");
		}

		public void onServiceDisconnected(ComponentName className) {
			mPlayerServiceBinder = null;
			// log.debug("onServiceDisconnected");
		}
	};

	public OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mLastSeekEventTime = 0;
			mFromTouch = true;
			log.debug("mFromTouch = false; ");

		}

		public void onProgressChanged(SeekBar bar, int progress,
				boolean fromuser) {
			log.debug("onProgressChanged");

			if (!fromuser || (mPlayerServiceBinder == null))
				return;

			long now = SystemClock.elapsedRealtime();
			if ((now - mLastSeekEventTime) > 250) {
				mLastSeekEventTime = now;
				// mPosOverride = mp.duration * progress / 1000;
				try {
					if (mPlayerServiceBinder.isInitialized())
						mPlayerServiceBinder.seek(mPlayerServiceBinder
								.duration() * progress / 1000);
				} catch (Exception ex) {
				}

				if (!mFromTouch) {
					refreshNow();
					// mPosOverride = -1;
				}
			}

		}

		public void onStopTrackingTouch(SeekBar bar) {
			// mPosOverride = -1;
			mFromTouch = false;
			log.debug("mFromTouch = false; ");

		}
	};

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (OnItemSelectedListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnArticleSelectedListener");
		}
	}

	// Container Activity must implement this interface
	public interface OnItemSelectedListener {
		public void onItemSelected(long id);
	}

	/*
	 * protected static ServiceConnection serviceConnection = new
	 * ServiceConnection() { public void onServiceConnected(ComponentName
	 * className, IBinder service) { mServiceBinder =
	 * ((PodcastService.PodcastBinder) service) .getService();
	 * mServiceBinder.start_update(); //log.debug("onServiceConnected"); }
	 * 
	 * public void onServiceDisconnected(ComponentName className) {
	 * mServiceBinder = null; //log.debug("onServiceDisconnected"); } };
	 */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mService = getActivity().startService(
				new Intent(getActivity(), PlayerService.class));
		Intent bindIntent = new Intent(getActivity(), PlayerService.class);
		getActivity().bindService(bindIntent, playerServiceConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			// unbindService(serviceConnection); TODO
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	/*
	 * @Override public void onResume() { super.onResume(); if (mInit) { mInit =
	 * false;
	 * 
	 * if (mCursor != null) mCursor.close();
	 * 
	 * getActivity().unbindService(serviceConnection);
	 * 
	 * startInit();
	 * 
	 * }
	 * 
	 * }
	 */

	@Override
	public void onPause() {
		super.onPause();

	}

	/*
	 * @Override public void onLowMemory() { super.onLowMemory(); mInit = true;
	 * 
	 * log.debug("onLowMemory()"); //finish(); }
	 */
	public void startInit() {

		SwipeActivity.mService = getActivity().startService(
				new Intent(getActivity(), PodcastService.class));

		Intent bindIntent = new Intent(getActivity(), PodcastService.class);
		getActivity().bindService(bindIntent, SwipeActivity.serviceConnection,
				Context.BIND_AUTO_CREATE);
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case REFRESH:
				long next = refreshNow();
				queueNextRefresh(next);
				// log.debug("REFRESH: "+next);
				break;

			default:
				break;
			}
		}
	};

	public void queueNextRefresh(long delay) {
		Message msg = mHandler.obtainMessage(REFRESH);
		mHandler.removeMessages(REFRESH);
		if (mShow)
			mHandler.sendMessageDelayed(msg, delay);
	}

	protected long refreshNow() {

		if (mPlayerServiceBinder == null)
			return 500;

		try {
			if (mPlayerServiceBinder.isInitialized() == false) {
				// mCurrentTime.setVisibility(View.INVISIBLE);
				// mTotalTime.setVisibility(View.INVISIBLE);
				mProgress.setProgress(0);
				return 500;
			}

			long pos = mPlayerServiceBinder.position();
			long duration = mPlayerServiceBinder.duration();

			updateCurrentPosition();
			/*
			 * String timeCounter = StrUtils.formatTime( pos ) + " / " +
			 * StrUtils.formatTime( duration );
			 * mCurrentTime.setText(timeCounter);
			 */

			// mTotalTime.setVisibility(View.VISIBLE);
			// mTotalTime.setText(formatTime( duration ));

			if (mPlayerServiceBinder.isPlaying() == false) {
				// mCurrentTime.setVisibility(View.VISIBLE);
				// mCurrentTime.setText(StrUtils.formatTime( pos ));

				mProgress.setProgress((int) (1000 * pos / duration));
				return 500;
			}

			long remaining = 1000 - (pos % 1000);
			if ((pos >= 0) && (duration > 0)) {
				// String timeCounter = StrUtils.formatTime( pos ) + " / " +
				// StrUtils.formatTime( duration );
				// mCurrentTime.setText(timeCounter);

				if (mPlayerServiceBinder.isInitialized()) {
					// mCurrentTime.setVisibility(View.VISIBLE);
					// mTotalTime.setVisibility(View.VISIBLE);
				}
				int nextPos = (int) (1000 * pos / mPlayerServiceBinder
						.duration());
				mProgress.setProgress(nextPos);
			} else {
				// mCurrentTime.setText("--:--");
				mProgress.setProgress(1000);
			}

			return remaining;
		} catch (Exception ex) {
		}
		return 500;
	}

	protected void updateCurrentPosition() {
		if (mCurrentTime != null) {
			long pos = mPlayerServiceBinder.position();
			long duration = mPlayerServiceBinder.duration();

			String timeCounter = StrUtils.formatTime(pos);
			mCurrentTime.setText(timeCounter);
		}
	}

}