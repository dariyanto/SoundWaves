package org.bottiger.podcast.cloud.drive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.provider.SubscriptionColumns;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Changes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

/**
 * From
 * https://github.com/googledrive/dredit/blob/master/android/src/com/example
 * /android/notepad/DriveSyncer.java
 * https://developers.google.com/drive/examples
 * /android#keeping_drive_in_sync_with_the_device
 * 
 * @author Arvid Böttiger
 * 
 */
public class DriveSyncer {

	/** For logging and debugging purposes */
	private static final String TAG = "DriveSyncerAdapter";

	/** Projection used for querying the database. */
	private static final String[] PROJECTION = SubscriptionColumns.ALL_COLUMNS;

	/** Field query parameter used on request to the drive.about.get endpoint. */
	private static final String ABOUT_GET_FIELDS = "largestChangeId";

	/** text/plain MIME type. */
	private static final String TEXT_PLAIN = "text/plain";

	private Context mContext;
	private ContentProviderClient mProvider;
	private Account mAccount;
	private Drive mService;
	private long mLargestChangeId;

	/**
	 * Instantiate a new DriveSyncer.
	 * 
	 * @param context
	 *            Context to use on credential requests.
	 * @param provider
	 *            Provider to use for database requests.
	 * @param account
	 *            Account to perform sync for.
	 */
	public DriveSyncer(Context context, ContentProviderClient provider,
			Account account) {
		mContext = context;
		mProvider = provider;
		mAccount = account;
		mService = getDriveService();
		mLargestChangeId = getLargestChangeId();
	}

	/**
	 * Perform a synchronization for the current account.
	 */
	public void performSync() {
		if (mService == null) {
			return;
		}

		Log.d(TAG, "Performing sync for " + mAccount.name);
		if (mLargestChangeId == -1) {
			// First time the sync adapter is run for the provided account.
			performFullSync();
		} else {
			Map<String, File> files = getChangedFiles(mLargestChangeId);
			Uri uri = getSubscriptionsUri(mAccount.name);

			try {
				Cursor cursor = mProvider.query(uri, PROJECTION, null, // NotePad.Notes.COLUMN_NAME_FILE_ID
																		// +
																		// " IS NOT NULL",
						null, null);

				Log.d(TAG, "Got local files: " + cursor.getCount());
				for (boolean more = cursor.moveToFirst(); more; more = cursor
						.moveToNext()) {

					// Merge.
					String fileId = cursor
							.getString(SubscriptionColumns.COLUMN_INDEX_ID);
					Uri localFileUri = getItemUri(mAccount.name, fileId);

					Log.d(TAG, "Processing local file with drive ID: " + fileId);
					if (files.containsKey(fileId)) {
						File driveFile = files.get(fileId);
						if (driveFile != null) {
							// Merge the files.
							mergeFiles(localFileUri, cursor, driveFile);
						} else {
							Log.d(TAG, " > Deleting local file: " + fileId);
							// The file does not exist in Drive anymore, delete
							// it.
							mProvider.delete(localFileUri, null, null);
						}
						files.remove(fileId);
					} else {
						// The file has not been updated on Drive, eventually
						// update the Drive file.
						File driveFile = mService.files().get(fileId).execute();
						mergeFiles(localFileUri, cursor, driveFile);
					}
					mContext.getContentResolver().notifyChange(localFileUri,
							null, false);
				}

				// Any remaining files in the map are files that do not exist in
				// the local database.
				insertNewDriveFiles(files.values());
				storeLargestChangeId(mLargestChangeId + 1);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			}

		}

		// Insert new local files.
		insertNewLocalFiles();

		Log.d(TAG, "Done performing sync for " + mAccount.name);
	}

	/**
	 * Performs a full sync, usually occurs the first time a sync occurs for the
	 * account.
	 */
	private void performFullSync() {
		Log.d(TAG, "Performing first sync");
		Long largestChangeId = (long) -1;
		try {
			// Get the largest change Id first to avoid race conditions.
			com.google.api.services.drive.model.About about = mService.about()
					.get().setFields(ABOUT_GET_FIELDS).execute();
			largestChangeId = about.getLargestChangeId();
		} catch (IOException e) {
			e.printStackTrace();
		}
		storeAllDriveFiles();
		storeLargestChangeId(largestChangeId);
		Log.d(TAG, "Done performing first sync: " + largestChangeId);
	}

	/**
	 * Insert all new local files in Google Drive.
	 */
	private void insertNewLocalFiles() {
		Uri uri = getSubscriptionsUri(mAccount.name);
		try {
			Cursor cursor = mProvider.query(uri, PROJECTION, null, null, null);
			// mProvider.query(uri, PROJECTION,
			// NotePad.Notes.COLUMN_NAME_FILE_ID + " is NULL", null, null);

			Log.d(TAG, "Inserting new local files: " + cursor.getCount());

			if (cursor.moveToFirst()) {
				do {
					Subscription subscription = Subscription
							.getByCursor(cursor);

					File newFile = new File();

					newFile.setTitle(Long.toString(subscription.getId()));
					newFile.setMimeType(TEXT_PLAIN);
					String content = "test content";
					try {
						content = subscription.getURL().toString();
					} catch (MalformedURLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}

					try {
						File insertedFile = null;

						if (content != null && content.length() > 0) {
							insertedFile = mService
									.files()
									.insert(newFile,
											ByteArrayContent.fromString(
													TEXT_PLAIN, content))
									.execute();
						} else {
							insertedFile = mService.files().insert(newFile)
									.execute();
						}

						// Update the local file to add the file ID.
						subscription.synced(mContext.getContentResolver(),
								insertedFile.getId());
						/*
						 * ContentValues values = new ContentValues();
						 * values.put
						 * (NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
						 * insertedFile.getModifiedDate().getValue());
						 * values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
						 * insertedFile.getCreatedDate().getValue());
						 * values.put(NotePad.Notes.COLUMN_NAME_FILE_ID,
						 * insertedFile.getId());
						 * 
						 * mProvider.update(localFileUri, values, null, null);
						 */
					} catch (IOException e) {
						e.printStackTrace();
					}
				} while (cursor.moveToNext());
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Insert new Google Drive files in the local database.
	 * 
	 * @param driveFiles
	 *            Collection of Google Drive files to insert.
	 */
	private void insertNewDriveFiles(Collection<File> driveFiles) {
		Uri uri = getSubscriptionsUri(mAccount.name);

		Log.d(TAG, "Inserting new Drive files: " + driveFiles.size());

		for (File driveFile : driveFiles) {
			if (driveFile != null) {
				URL url = null;
				try {
					url = new URL(driveFile.getTitle());
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Subscription newSubscription = new Subscription(url.toString());
				newSubscription.subscribe(mContext);
				newSubscription.synced(mContext.getContentResolver(),
						driveFile.getId());
				/*
				 * ContentValues values = new ContentValues();
				 * values.put(NotePad.Notes.COLUMN_NAME_ACCOUNT, mAccount.name);
				 * values.put(NotePad.Notes.COLUMN_NAME_FILE_ID,
				 * driveFile.getId());
				 * values.put(NotePad.Notes.COLUMN_NAME_TITLE,
				 * driveFile.getTitle());
				 * values.put(NotePad.Notes.COLUMN_NAME_NOTE,
				 * getFileContent(driveFile));
				 * values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, driveFile
				 * .getCreatedDate().getValue());
				 * values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
				 * driveFile.getModifiedDate().getValue());
				 * 
				 * try { mProvider.insert(uri, values); } catch (RemoteException
				 * e) { e.printStackTrace(); }
				 */
			}
		}

		mContext.getContentResolver().notifyChange(uri, null, false);
	}

	/**
	 * Retrieve a Google Drive file's content.
	 * 
	 * @param driveFile
	 *            Google Drive file to retrieve content from.
	 * @return Google Drive file's content if successful, {@code null}
	 *         otherwise.
	 */
	public String getFileContent(File driveFile) {
		String result = "";

		if (driveFile.getDownloadUrl() != null
				&& driveFile.getDownloadUrl().length() > 0) {
			try {
				GenericUrl downloadUrl = new GenericUrl(
						driveFile.getDownloadUrl());

				HttpResponse resp = mService.getRequestFactory()
						.buildGetRequest(downloadUrl).execute();
				InputStream inputStream = null;

				try {
					inputStream = resp.getContent();
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(inputStream));
					StringBuilder content = new StringBuilder();
					char[] buffer = new char[1024];
					int num;

					while ((num = reader.read(buffer)) > 0) {
						content.append(buffer, 0, num);
					}
					result = content.toString();
				} finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}
			} catch (IOException e) {
				// An error occurred.
				e.printStackTrace();
				return null;
			}
		} else {
			// The file doesn't have any content stored on Drive.
			return null;
		}

		return result;
	}

	/**
	 * Retrieve a collection of files that have changed since the provided
	 * {@code changeId}.
	 * 
	 * @param changeId
	 *            Change ID to retrieve changed files from.
	 * @return Map of changed files key'ed by their file ID.
	 */
	private Map<String, File> getChangedFiles(long changeId) {
		Map<String, File> result = new HashMap<String, File>();

		try {
			Changes.List request = mService.changes().list()
					.setStartChangeId(changeId);
			do {
				ChangeList changes = request.execute();
				long largestChangeId = changes.getLargestChangeId().longValue();

				for (Change change : changes.getItems()) {
					if (change.getDeleted()) {
						result.put(change.getFileId(), null);
					} else if (TEXT_PLAIN
							.equals(change.getFile().getMimeType())) {
						result.put(change.getFileId(), change.getFile());
					}
				}

				if (largestChangeId > mLargestChangeId) {
					mLargestChangeId = largestChangeId;
				}
				request.setPageToken(changes.getNextPageToken());
			} while (request.getPageToken() != null
					&& request.getPageToken().length() > 0);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Log.d(TAG, "Got changed Drive files: " + result.size() + " - "
				+ mLargestChangeId);
		return result;
	}

	/**
	 * Retrieve a authorized service object to send requests to the Google Drive
	 * API. On failure to retrieve an access token, a notification is sent to
	 * the user requesting that authorization be granted for the
	 * {@code https://www.googleapis.com/auth/drive.file} scope.
	 * 
	 * @return An authorized service object.
	 */
	private Drive getDriveService() {
		if (mService == null) {
			try {
				GoogleAccountCredential credential = GoogleAccountCredential
						.usingOAuth2(mContext, DriveScopes.DRIVE_FILE);
				credential.setSelectedAccountName(mAccount.name);
				// Trying to get a token right away to see if we are authorized
				credential.getToken();
				mService = new Drive.Builder(
						AndroidHttp.newCompatibleTransport(),
						new GsonFactory(), credential).build();
			} catch (Exception e) {
				Log.e(TAG, "Failed to get token");
				// If the Exception is User Recoverable, we display a
				// notification that will trigger the
				// intent to fix the issue.
				if (e instanceof UserRecoverableAuthException) {
					UserRecoverableAuthException exception = (UserRecoverableAuthException) e;
					NotificationManager notificationManager = (NotificationManager) mContext
							.getSystemService(Context.NOTIFICATION_SERVICE);
					Intent authorizationIntent = exception.getIntent();
					authorizationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
							.addFlags(Intent.FLAG_FROM_BACKGROUND);
					PendingIntent pendingIntent = PendingIntent.getActivity(
							mContext, 0, authorizationIntent, 0);

					NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
							mContext)
							.setSmallIcon(android.R.drawable.ic_dialog_alert)
							.setTicker("Permission requested")
							.setContentTitle("Permission requested")
							.setContentText("for account " + mAccount.name)
							.setContentIntent(pendingIntent)
							.setAutoCancel(true);
					notificationManager.notify(0, notificationBuilder.build());
				} else {
					e.printStackTrace();
				}
			}
		}
		return mService;
	}

	/**
	 * Store all text/plain files from Drive that the app has access to. This is
	 * called the first time the app synchronize the database with Google Drive
	 * for the current user.
	 */
	private void storeAllDriveFiles() {
		try {
			Files.List request = mService.files().list()
					.setQ("mimeType = '" + TEXT_PLAIN + "'");
			Map<String, File> textFiles = new HashMap<String, File>();

			do {
				try {
					FileList files = request.execute();

					for (File file : files.getItems()) {
						textFiles.put(file.getId(), file);
					}
					request.setPageToken(files.getNextPageToken());
				} catch (IOException e) {
					System.out.println("An error occurred: " + e);
					request.setPageToken(null);
				}
			} while (request.getPageToken() != null
					&& request.getPageToken().length() > 0);

			// Merge with files that are already in the database.
			try {
				Uri uri = getSubscriptionsUri(mAccount.name);
				Cursor cursor = mProvider.query(uri, PROJECTION, null, null,
						null);

				if (cursor.moveToFirst()) {
					do {
						Subscription newSubscription = new Subscription();
						/*
						String fileId = cursor.getString(COLUMN_INDEX_FILE_ID);

						if (textFiles.containsKey(fileId)) {
							Uri localFileUri = getNoteUri(mAccount.name,
									cursor.getString(COLUMN_INDEX_ID));
							mergeFiles(localFileUri, cursor,
									textFiles.get(fileId));
							textFiles.remove(fileId);
						}
						*/
					} while (cursor.moveToNext());
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}

			insertNewDriveFiles(textFiles.values());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Store the largest change ID for the current user.
	 * 
	 * @param changeId
	 *            The largest change ID to store.
	 */
	private void storeLargestChangeId(long changeId) {
		SharedPreferences.Editor editor = PreferenceManager
				.getDefaultSharedPreferences(mContext).edit();
		editor.putLong("largest_change_" + mAccount.name, changeId);
		editor.commit();
		mLargestChangeId = changeId;
	}

	/**
	 * Merge a local file with a Google Drive File.
	 * 
	 * The last modification is used to check which file to sync from. Then, the
	 * md5 checksum of the file is used to check whether or not the file's
	 * content should be sync'ed.
	 * 
	 * @param localFileUri
	 *            Local file URI to save local changes against.
	 * @param localFile
	 *            Local file cursor to retrieve data from.
	 * @param driveFile
	 *            Google Drive file.
	 */
	
	private void mergeFiles(Uri localFileUri, Cursor localFile, File driveFile) {
		/*
		long localFileModificationDate = localFile
				.getLong(COLUMN_INDEX_MODIFICATION_DATE);

		Log.d(TAG, "Modification dates: " + localFileModificationDate + " - "
				+ driveFile.getModifiedDate().getValue());
		if (localFileModificationDate > driveFile.getModifiedDate().getValue()) {
			try {
				if (localFile.getShort(COLUMN_INDEX_DELETED) != 0) {
					Log.d(TAG, " > Deleting Drive file.");
					mService.files().delete(driveFile.getId()).execute();
					mProvider.delete(localFileUri, null, null);
				} else {
					String localNote = localFile.getString(COLUMN_INDEX_NOTE);
					File updatedFile = null;

					// Update drive file.
					Log.d(TAG, " > Updating Drive file.");
					driveFile.setTitle(localFile.getString(COLUMN_INDEX_TITLE));

					if (md5(localNote) != driveFile.getMd5Checksum()) {
						// Update both content and metadata.
						ByteArrayContent content = ByteArrayContent.fromString(
								TEXT_PLAIN, localNote);
						updatedFile = mService.files()
								.update(driveFile.getId(), driveFile, content)
								.execute();
					} else {
						// Only update the metadata.
						updatedFile = mService.files()
								.update(driveFile.getId(), driveFile).execute();
					}

					ContentValues values = new ContentValues();
					values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
							updatedFile.getModifiedDate().getValue());
					mProvider.update(localFileUri, values, null, null);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else if (localFileModificationDate < driveFile.getModifiedDate()
				.getValue()) {
			// Update local file.
			Log.d(TAG, " > Updating local file.");
			ContentValues values = new ContentValues();
			values.put(NotePad.Notes.COLUMN_NAME_TITLE, driveFile.getTitle());
			values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, driveFile
					.getModifiedDate().getValue());
			// Only download the content if it has changed.
			if (md5(localFile.getString(COLUMN_INDEX_NOTE)) != driveFile
					.getMd5Checksum()) {
				values.put(NotePad.Notes.COLUMN_NAME_NOTE,
						getFileContent(driveFile));
			}
			try {
				mProvider.update(localFileUri, values, null, null);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	*/
	}
	

	/**
	 * Retrieve the largest change ID for the current user if available.
	 * 
	 * @return The largest change ID, {@code -1} if not available.
	 */
	private long getLargestChangeId() {
		return PreferenceManager.getDefaultSharedPreferences(mContext).getLong(
				"largest_change_" + mAccount.name, -1);
	}

	private static Uri getSubscriptionsUri(String accountName) {
		return Uri.parse("content://com.google.provider.NotePad/" + accountName
				+ "/notes/");
	}

	private static Uri getSubscriptionUri(String accountName) {
		return Uri.parse("content://com.google.provider.NotePad/" + accountName
				+ "/notes/");
	}

	private static Uri getItemUri(String accountName, String noteId) {
		return Uri.parse("content://com.google.provider.NotePad/" + accountName
				+ "/notes/" + noteId);
	}

}
