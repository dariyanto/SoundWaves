package org.bottiger.podcast;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v7.app.AppCompatActivity;
import android.transition.Slide;
import android.transition.Transition;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.service.DownloadService;
import org.bottiger.podcast.service.Downloader.SoundWavesDownloadManager;
import org.bottiger.podcast.utils.PlayerHelper;
import org.bottiger.podcast.utils.TransitionUtils;
import org.bottiger.podcast.utils.UIUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class TopActivity extends AppCompatActivity {

    private static final String TAG = "TopActivity";

    // Filesystem Permisssion
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PERMISSION_TO_DOWNLOAD, PERMISSION_TO_IMPORT_EXPORT})
    public @interface PermissionCallback {}
    public static final int PERMISSION_TO_DOWNLOAD = 1;
    public static final int PERMISSION_TO_IMPORT_EXPORT = 2;
	
	private static SharedPreferences prefs;
    private Menu mMenu;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
        }

        super.onCreate(savedInstanceState);

        boolean transparentStatus = transparentNavigationBar();

        if (transparentStatus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition transition = new Slide();
            getWindow().setEnterTransition(transition);
            getWindow().setExitTransition(transition);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SoundWaves.getRxBus().toObserverable()
                .ofType(SoundWavesDownloadManager.DownloadManagerChanged.class)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SoundWavesDownloadManager.DownloadManagerChanged>() {
                    @Override
                    public void call(SoundWavesDownloadManager.DownloadManagerChanged downloadManagerChanged) {
                        Log.d(TAG, "DownloadManagerChanged, size: " + downloadManagerChanged.queueSize);
                        showDownloadManager(downloadManagerChanged);
                        return;
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.report("subscribeError" , throwable.toString());
                        Log.w(TAG, "Erorr");
                    }
                });

	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        SoundWaves.getBus().unregister(this);
        super.onPause();
        if (SoundWaves.sAnalytics != null)
            SoundWaves.sAnalytics.activityPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SoundWaves.getBus().register(this);
        if (SoundWaves.sAnalytics != null)
            SoundWaves.sAnalytics.activityResume();
        initDownloadManagerOptionsMenu(mMenu);
    }

    public static SharedPreferences getPreferences() {
		return prefs;
	}

    /**
     * Override this if the navigation bar should remain opaque
     */
    protected boolean transparentNavigationBar() {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.top_options_menu, menu);
        initDownloadManagerOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Right corner menu options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_download_manager: {
                TransitionUtils.openDownloadManager(this);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {

        if (PermissionDenied(grantResults)) {
            return;
        }

        switch (requestCode) {
            case PERMISSION_TO_DOWNLOAD: {
                // FIXME
                //SoundWaves.getDownloadManager().startDownload();
                return;
            }
            case PERMISSION_TO_IMPORT_EXPORT: {
                SubscriptionsFragment.openImportExportDialog(this);
                return;
            }
        }
    }

    protected void onServiceConnected() {
    }

    @Nullable
    public MediaControllerCompat getMediaControllerCompat() {
        return ((SoundWaves)getApplicationContext()).mMediaControllerCompat;
    }

    @NonNull
    public PlayerHelper getPlayerHelper() {
        return ((SoundWaves)getApplicationContext()).mPlayerHelper;
    }

    protected void importOPMLButtonCallback() {
        SubscriptionsFragment.openImportExportDialog(this);
    }

    /**
     *
     * @param grantResults
     * @return False if one or more permission was denied
     */
    private boolean PermissionDenied(int[] grantResults) {
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                return true;
        }

        return false;
    }

    private void initDownloadManagerOptionsMenu(@Nullable Menu argMenu) {
        if (argMenu == null)
            return;

        MenuItem item = argMenu.findItem(R.id.menu_download_manager);

        // Tint the icon
        // http://stackoverflow.com/questions/26780046/menuitem-tinting-on-appcompat-toolbar
        Drawable drawable = item.getIcon();
        drawable = DrawableCompat.wrap(drawable);
        drawable = drawable.getConstantState().newDrawable(); // clone it.
        @ColorInt int color = UIUtils.isInNightMode(this.getResources()) ? ContextCompat.getColor(this, R.color.pitch_black) : ContextCompat.getColor(this, R.color.white_opaque);
        DrawableCompat.setTint(drawable, color);
        argMenu.findItem(R.id.menu_download_manager).setIcon(drawable);

        item.setVisible(DownloadService.isRunning());
    }

    public void showDownloadManager(SoundWavesDownloadManager.DownloadManagerChanged event)
    {
        MenuItem item = TopActivity.this.mMenu.findItem(R.id.menu_download_manager);
        item.setVisible(event.queueSize > 0);
    }

}
