/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencv.samples.facedetect;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import ncit.android.moodplayer.DialogError;
import ncit.android.moodplayer.Facebook;
import ncit.android.moodplayer.FacebookError;
import ncit.android.moodplayer.SessionStore;
import ncit.android.moodplayer.Facebook.DialogListener;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.samples.facedetect.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.PlayerStateChangeListener;
import com.google.android.youtube.player.YouTubePlayerView;
import com.google.android.youtube.player.YouTubePlayer.ErrorReason;

public class PlayerViewDemoActivity extends YouTubeFailureRecoveryActivity
		implements CvCameraViewListener2 {

	private Facebook mFacebook;
	private CheckBox mFacebookBtn;
	private ProgressDialog mProgress;

	private static final String[] PERMISSIONS = new String[] { "publish_stream",
			"read_stream", "offline_access" };

	private static final String APP_ID = "482636805164043";

	private int cameraId = 0;
	private int progress1;

	private Camera camera;

	private String htmlSource1, htmlSource2;
	private String currentMood = "happy";
	private String currentSong;

	private Mat mGray;
	private Mat mRgba;

	private YouTubePlayer player;
	private ArrayList<String> songs;
	private Mat mSad, mHappy, mMoodGray;
	private MenuItem mDeleteTrainingSet, mExit, mChooseThreshold;
	private File root, moodFile, happyFile, sadFile;
	private TextView tvTop;
	private FaceDetectAsyncTask detect;
	boolean faceDetection;
	private Button playListButton;
	private ImageView sadImage, happyImage;
	final Drawable gauges[] = new Drawable[16];
	static long faceDetectThreshold = 5000;// don't change it unless you are a

	// moron

	// update playlist
	public void setIdArray() {
		// set the button unclickable while the playlist is downloaded
		playListButton.setEnabled(false);

		ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (mWifi.isConnected()) {
			final String tag = getTag();
			String URL = "http://www.last.fm/tag/";
			URL = URL + tag + "/videos";

			ServerFetchAsyncTask down1 = new ServerFetchAsyncTask(URL,
					PlayerViewDemoActivity.this, new ServerFetchAsyncTask.MyCallBack() {
						public void run(String[] sv) {
							htmlSource1 = sv[0];
							htmlSource2 = sv[1];

							String separator = new String("<img src=");

							// extract the first id array
							String parts1[] = htmlSource1.split(separator);
							// first and last 3 lines are not interesting
							for (int i = 1; i < parts1.length - 3; i++) {
								parts1[i] = parts1[i].substring(parts1[i].indexOf("vi/"),
										parts1[i].indexOf(".jpg"));
								parts1[i] = parts1[i].substring(3, parts1[i].length() - 2);
							}

							String parts2[] = htmlSource2.split(separator);
							for (int i = 1; i < parts2.length - 3; i++) {
								parts2[i] = parts2[i].substring(parts2[i].indexOf("vi/"),
										parts2[i].indexOf(".jpg"));
								parts2[i] = parts2[i].substring(3, parts2[i].length() - 2);
							}

							// final id array
							songs = new ArrayList<String>();

							InputStream inputStream = getResources().openRawResource(
									getRawId(tag));
							BufferedReader br = new BufferedReader(new InputStreamReader(
									inputStream));
							String s;
							try {
								while ((s = br.readLine()) != null) {
									songs.add(s);
								}
							} catch (IOException e) {
								e.printStackTrace();
							}

							for (int i = 1; i < parts1.length - 3; i++)
								songs.add(parts1[i]);
							for (int i = 1; i < parts2.length - 3; i++)
								songs.add(parts2[i]);
							// we use it in playvideoatselection() to get random
							// id
							playListButton.setEnabled(true);
						}
					});
			Toast.makeText(PlayerViewDemoActivity.this, "Downloading id list...",
					Toast.LENGTH_SHORT).show();
			down1.execute();
		} else {
			Toast.makeText(PlayerViewDemoActivity.this, "Please turn wi-fi on",
					Toast.LENGTH_SHORT).show();
		}
	}

	public int getRawId(String tag) {
		if (tag.equals("happy")) {
			return R.raw.happy;
		}
		if (tag.equals("happy%20hardcore")) {
			return R.raw.happy_hardcore;
		}
		if (tag.equals("sad%20songs")) {
			return R.raw.sad_songs;
		}
		if (tag.equals("sad")) {
			return R.raw.sad;
		}
		if (tag.equals("songs%20that%20make%20me%20happy")) {
			return R.raw.songs_that_make_me_happy;

		}
		if (tag.equals("angry")) {
			return R.raw.angry;
		}
		if (tag.equals("calm-peaceful")) {
			return R.raw.calm_peaceful;
		}
		if (tag.equals("sad%20and%20slow")) {
			return R.raw.sad_and_slow;
		}
		return 0;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		faceDetection = false;
		root = Environment.getExternalStorageDirectory();

		moodFile = new File(root, "/moodplayer/mood.jpg");
		happyFile = new File(root, "/moodplayer/face_happy.jpg");
		sadFile = new File(root, "/moodplayer/face_sad.jpg");

		gauges[0] = getResources().getDrawable(R.drawable.gauge1);
		gauges[1] = getResources().getDrawable(R.drawable.gauge2);
		gauges[2] = getResources().getDrawable(R.drawable.gauge3);
		gauges[3] = getResources().getDrawable(R.drawable.gauge4);
		gauges[4] = getResources().getDrawable(R.drawable.gauge5);
		gauges[5] = getResources().getDrawable(R.drawable.gauge6);
		gauges[6] = getResources().getDrawable(R.drawable.gauge7);
		gauges[7] = getResources().getDrawable(R.drawable.gauge8);
		gauges[8] = getResources().getDrawable(R.drawable.gauge9);
		gauges[9] = getResources().getDrawable(R.drawable.gauge10);
		gauges[10] = getResources().getDrawable(R.drawable.gauge11);
		gauges[11] = getResources().getDrawable(R.drawable.gauge12);
		gauges[12] = getResources().getDrawable(R.drawable.gauge13);
		gauges[13] = getResources().getDrawable(R.drawable.gauge14);
		gauges[14] = getResources().getDrawable(R.drawable.gauge15);
		gauges[15] = getResources().getDrawable(R.drawable.gauge16);
		setContentView(R.layout.playerview_demo);

	}

	private int findFrontFacingCamera() {
		int cameraId = -1;
		// Search for the front facing camera
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numberOfCameras; i++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				cameraId = i;
				break;
			}
		}
		return cameraId;
	}

	public void onResume() {
		super.onResume();

		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG)
					.show();
		} else {
			cameraId = findFrontFacingCamera();
			if (cameraId < 0) {
				Toast
						.makeText(this, "No front facing camera found.", Toast.LENGTH_LONG)
						.show();
			} else {
				camera = Camera.open(cameraId);
			}
		}
		camera.startPreview();

		YouTubePlayerView youTubeView = (YouTubePlayerView) findViewById(R.id.youtube_view);
		youTubeView.initialize(DeveloperKey.DEVELOPER_KEY, this);

		playListButton = (Button) findViewById(R.id.button1);
		SeekBar seekBar1 = (SeekBar) findViewById(R.id.seekBar1);

		seekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				// seekBarValue.setText(String.valueOf(progress));
				progress1 = progress;
				ImageView gauge = (ImageView) findViewById(R.id.gaugeImage);

				if (progress <= 6)
					gauge.setImageDrawable(gauges[0]);
				else if (progress > 6 && progress <= 13)
					gauge.setImageDrawable(gauges[1]);
				else if (progress > 13 && progress <= 19)
					gauge.setImageDrawable(gauges[2]);
				else if (progress > 19 && progress <= 25)
					gauge.setImageDrawable(gauges[3]);
				else if (progress > 25 && progress <= 31)
					gauge.setImageDrawable(gauges[4]);
				else if (progress > 31 && progress <= 37)
					gauge.setImageDrawable(gauges[5]);
				else if (progress > 37 && progress <= 43)
					gauge.setImageDrawable(gauges[6]);
				else if (progress > 43 && progress <= 49)
					gauge.setImageDrawable(gauges[7]);
				else if (progress < 49 && progress <= 55)
					gauge.setImageDrawable(gauges[8]);
				else if (progress > 55 && progress <= 61)
					gauge.setImageDrawable(gauges[9]);
				else if (progress > 61 && progress <= 67)
					gauge.setImageDrawable(gauges[10]);
				else if (progress > 67 && progress <= 73)
					gauge.setImageDrawable(gauges[11]);
				else if (progress > 73 && progress <= 79)
					gauge.setImageDrawable(gauges[12]);
				else if (progress > 79 && progress <= 85)
					gauge.setImageDrawable(gauges[13]);
				else if (progress > 85 && progress <= 91)
					gauge.setImageDrawable(gauges[14]);
				else if (progress > 91)
					gauge.setImageDrawable(gauges[15]);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setIdArray();

			}
		});

		CheckBox checkFd = (CheckBox) findViewById(R.id.checkBox1);
		checkFd.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					faceDetection = true;
				else {
					tvTop.setText("Let`s play your mood!");
					faceDetection = false;
				}
			}
		});

		happyImage = (ImageView) findViewById(R.id.hapyFace);
		happyImage.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (faceDetection == false) {
					sadImage.setImageDrawable(getResources().getDrawable(
							R.drawable.sad_bw));
					happyImage.setImageDrawable(getResources().getDrawable(
							R.drawable.happy));
					happyImage.bringToFront();
					currentMood = new String("happy");
					setIdArray();
				}
			}
		});

		sadImage = (ImageView) findViewById(R.id.sadFace);
		sadImage.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (faceDetection == false) {
					sadImage.setImageDrawable(getResources().getDrawable(R.drawable.sad));
					happyImage.setImageDrawable(getResources().getDrawable(
							R.drawable.happy_bw));
					sadImage.bringToFront();
					currentMood = new String("sad");
					setIdArray();
				}
			}
		});

		timerAlert();

		mFacebookBtn = (CheckBox) findViewById(R.id.cb_facebook);

		mProgress = new ProgressDialog(this);
		mFacebook = new Facebook(APP_ID);

		SessionStore.restore(mFacebook, this);

		if (mFacebook.isSessionValid()) {
			mFacebookBtn.setChecked(true);

			String name = SessionStore.getName(this);
			name = (name.equals("")) ? "Unknown" : name;

			mFacebookBtn.setText("  Facebook (" + name + ")");
			mFacebookBtn.setTextColor(Color.WHITE);
		}

		mFacebookBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onFacebookClick();
			}
		});

	}

	public String getMood(Mat face, Mat mSad, Mat mHappy) {
		return (compareProp(face, mSad) > compareProp(face, mHappy) ? "happy"
				: "sad");
	}

	public double compareProp(Mat m1, Mat m2) {
		// daca au dimensiuni diferite, fa-o pe m2 de aceeasi dimensiune cu m1
		if (m1.rows() != m2.rows() || m1.cols() != m2.cols()) {
			Mat m3 = new Mat(m1.rows(), m1.cols(), CvType.CV_8UC1);
			Imgproc.resize(m2, m3, m3.size());
			return Core.norm(m1, m3);
		} else {
			return Core.norm(m1, m2);
		}
	}

	public void onCameraViewStarted(int width, int height) {
		mGray = new Mat();
		mRgba = new Mat();
	}

	public void onCameraViewStopped() {
		mGray.release();
		mRgba.release();
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();
		return mRgba;
	}

	@Override
	public void onInitializationSuccess(YouTubePlayer.Provider provider,
			YouTubePlayer player, boolean wasRestored) {

		setIdArray();
		this.player = player;
		if (!wasRestored) {
			playVideoAtSelection();
			player.setPlayerStateChangeListener(new PlayerStateChangeListener() {

				@Override
				public void onVideoStarted() {
					// TODO Auto-generated method stub

				}

				@Override
				public void onVideoEnded() {
					// TODO Auto-generated method stub

				}

				@Override
				public void onLoading() {
					// TODO Auto-generated method stub

				}

				@Override
				public void onLoaded(String arg0) {
					currentSong = "http://www.youtube.com/watch?v=" + arg0;

				}

				@Override
				public void onError(ErrorReason arg0) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onAdStarted() {
					// TODO Auto-generated method stub

				}
			});
		}
	}

	private void playVideoAtSelection() {
		playListButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (songs != null) {
					// play youtube video based on random id
					Playlist p = new Playlist(songs);
					player.loadVideos(p.playlist);
				} else {
					Toast.makeText(PlayerViewDemoActivity.this, "Please turn wi-fi on",
							Toast.LENGTH_SHORT).show();
				}
			}
		});

	}

	public String getTag() {

		if (currentMood.equals("happy")) {
			if (progress1 <= 25)
				return "calm-peaceful";
			if (progress1 > 25 && progress1 <= 50)
				return "happy";
			if (progress1 > 50 && progress1 <= 75)
				return "songs%20that%20make%20me%20happy";
			if (progress1 > 75 && progress1 <= 100)
				return "happy%20hardcore";

		}

		if (currentMood.equals("sad")) {
			if (progress1 <= 25)
				return "sad%20and%20slow";
			if (progress1 > 25 && progress1 <= 50)
				return "sad%20songs";
			if (progress1 > 50 && progress1 <= 75)
				return "sad";
			if (progress1 > 75 && progress1 <= 100)
				return "angry";

		}
		return currentMood;
	}

	@Override
	protected YouTubePlayer.Provider getYouTubePlayerProvider() {
		return (YouTubePlayerView) findViewById(R.id.youtube_view);
	}

	@Override
	protected void onPause() {
		if (camera != null) {
			camera.release();
			camera = null;
		}
		super.onPause();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		mDeleteTrainingSet = menu.add("Delete training photo set");
		mChooseThreshold = menu.add("Help / settings");
		mExit = menu.add("Quit application");
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {

		if (item == mDeleteTrainingSet) {
			File sadPhoto = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/moodplayer/face_sad.jpg");

			File happyPhoto = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/moodplayer/face_happy.jpg");

			sadPhoto.delete();
			happyPhoto.delete();

			final Intent faceDetect = new Intent(PlayerViewDemoActivity.this,
					FdActivity.class);
			startActivity(faceDetect);
			finish();
		}

		else if (item == mChooseThreshold) {
			Intent settings = new Intent(PlayerViewDemoActivity.this,
					HintActivity.class);
			startActivity(settings);
		}

		else if (item == mExit) {
			finish();
		}

		return true;
	}

	public void runDetection() {
		moodFile = new File(root, "/moodplayer/mood.jpg");

		if (moodFile.exists() == false) {
			// la prima rulare a aplicatiei crapa, cum nu are timp sa faca poza
			TakePhotoAsyncTask getPhoto = new TakePhotoAsyncTask(camera,
					PlayerViewDemoActivity.this);
			getPhoto.execute();
		}

		else if (camera != null) {

			TakePhotoAsyncTask getPhoto = new TakePhotoAsyncTask(camera,
					PlayerViewDemoActivity.this);
			getPhoto.execute();

			mHappy = Highgui.imread(happyFile.getAbsolutePath(),
					Highgui.CV_LOAD_IMAGE_GRAYSCALE);
			mSad = Highgui.imread(sadFile.getAbsolutePath(),
					Highgui.CV_LOAD_IMAGE_GRAYSCALE);
			// 10130 diferite
			// 2998 aproape la fel
			// 0 identice

			mMoodGray = Highgui.imread(moodFile.getAbsolutePath(),
					Highgui.CV_LOAD_IMAGE_GRAYSCALE);

			Mat mT;

			mT = mMoodGray.t();
			Core.flip(mT, mT, 0);

			Rect halfRect = new Rect(0, mT.rows() / 2, mT.cols(), mT.rows() / 2 - 10);
			mT = mT.submat(halfRect);

			tvTop = (TextView) findViewById(R.id.topText);

			Mat[] m = new Mat[3];
			m[0] = mT;
			m[1] = mHappy;
			m[2] = mSad;

			detect = new FaceDetectAsyncTask(m, PlayerViewDemoActivity.this,
					new FaceDetectAsyncTask.MyCallBack() {
						public void run(String mood) {
							currentMood = new String(mood);
							setIdArray();
							tvTop.setText("You seem " + mood);
							happyImage = (ImageView) findViewById(R.id.hapyFace);
							sadImage = (ImageView) findViewById(R.id.sadFace);
							if (mood.equals("sad")) {
								sadImage.setImageDrawable(getResources().getDrawable(
										R.drawable.sad));
								happyImage.setImageDrawable(getResources().getDrawable(
										R.drawable.happy_bw));
								sadImage.bringToFront();
							} else {
								sadImage.setImageDrawable(getResources().getDrawable(
										R.drawable.sad_bw));
								happyImage.setImageDrawable(getResources().getDrawable(
										R.drawable.happy));
								happyImage.bringToFront();
							}
						}
					});

			detect.execute();
		}

	}

	public void timerAlert() {

		final Handler handler = new Handler();
		handler.postDelayed(new Runnable() {
			public void run() {
				if (faceDetection == true)
					runDetection();
				handler.postDelayed(this, faceDetectThreshold);
			}
		}, 6000);

	}
	private void onFacebookClick() {
		if (mFacebook.isSessionValid()) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			// final AlertDialog alert = builder.create();

			// alert.show();
			Intent share = new Intent(PlayerViewDemoActivity.this,
					TestPost.class);
			share.putExtra("toShare", currentSong);
			startActivity(share);

		} else {
			mFacebookBtn.setChecked(false);
			mFacebook.authorize(this, PERMISSIONS, -1,
					new FbLoginDialogListener());
		}
	}

	private final class FbLoginDialogListener implements DialogListener {
		public void onComplete(Bundle values) {
			SessionStore.save(mFacebook, PlayerViewDemoActivity.this);

			mFacebookBtn.setText("  Facebook (No Name)");
			mFacebookBtn.setChecked(true);
			mFacebookBtn.setTextColor(Color.WHITE);

			getFbName();
		}

		public void onFacebookError(FacebookError error) {
			Toast.makeText(PlayerViewDemoActivity.this,
					"Facebook connection failed", Toast.LENGTH_SHORT).show();

			mFacebookBtn.setChecked(false);
		}

		public void onError(DialogError error) {
			Toast.makeText(PlayerViewDemoActivity.this,
					"Facebook connection failed", Toast.LENGTH_SHORT).show();

			mFacebookBtn.setChecked(false);
		}

		public void onCancel() {
			mFacebookBtn.setChecked(false);
		}
	}

	private void getFbName() {
		mProgress.setMessage("Finalizing ...");
		mProgress.show();

		new Thread() {
			@Override
			public void run() {
				String name = "";
				int what = 1;

				try {
					String me = mFacebook.request("me");

					JSONObject jsonObj = (JSONObject) new JSONTokener(me)
							.nextValue();
					name = jsonObj.getString("name");
					what = 0;
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				mFbHandler.sendMessage(mFbHandler.obtainMessage(what, name));
			}
		}.start();
	}

	private Handler mFbHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			mProgress.dismiss();

			if (msg.what == 0) {
				String username = (String) msg.obj;
				username = (username.equals("")) ? "No Name" : username;

				SessionStore.saveName(username, PlayerViewDemoActivity.this);

				mFacebookBtn.setText("  Facebook (" + username + ")");

				Toast.makeText(PlayerViewDemoActivity.this,
						"Connected to Facebook as " + username,
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(PlayerViewDemoActivity.this,
						"Connected to Facebook", Toast.LENGTH_SHORT).show();
			}
		}
	};

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			mProgress.dismiss();

			if (msg.what == 1) {
				Toast.makeText(PlayerViewDemoActivity.this,
						"Facebook logout failed", Toast.LENGTH_SHORT).show();
			} else {
				mFacebookBtn.setChecked(false);
				mFacebookBtn.setTextColor(Color.GRAY);

				Toast.makeText(PlayerViewDemoActivity.this,
						"Disconnected from Facebook", Toast.LENGTH_SHORT)
						.show();
			}
		}
	};

}
