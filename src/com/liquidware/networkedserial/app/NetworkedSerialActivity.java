/*
 * Copyright 2011 Chris Ladden chris.ladden@liquidware.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package com.liquidware.networkedserial.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.widget.TextView.OnEditorActionListener;
import com.liquidware.networkedserial.app.R;

public class NetworkedSerialActivity extends SerialPortActivity {
	private static final String TAG = "NetworkedSerialActivity";

	private static final String mLocalDir = Environment.getExternalStorageDirectory().toString() + "/out";
	
	private static final int CMD_SUCCESS  = -1;
	private static final int CMD_TIMEOUT  = -2;

	SendingThread mSendingThread;
	volatile byte[] mBuffer;
	static TextView mReception;
	EditText mPingIP;
	ScrollView mScroller;
	ProgressBar mProgressBar;
	ImageButton mImage1;
	ImageButton mImage2;
	public static Button mButtonInit;
	public static Button mButtonIP;
	public static Button mButtonPing;
	public static Button mButtonBack;
	public static ViewFlipper mFlipper;
	volatile String mReceptionBuffer;
	volatile StringBuffer mStringBuffer;
	volatile String mExpectedResult;
	volatile boolean mIsExpectedResult;
	volatile boolean mShowSerialInput;
	volatile int mTimeout;

	public void setUIDisabled() {
		mButtonInit.setEnabled(false);
		mButtonPing.setEnabled(false);
		mButtonIP.setEnabled(false);
		mProgressBar.setVisibility(ProgressBar.VISIBLE);
		mProgressBar.setProgress(0);
	}

	public void setUIEnabled() {
		mButtonInit.setEnabled(true);
		mButtonPing.setEnabled(true);
		mButtonIP.setEnabled(true);
		mProgressBar.setVisibility(ProgressBar.INVISIBLE);
		mProgressBar.setProgress(0);
		try {
			Bitmap bitmap1 = BitmapFactory.decodeFile(mLocalDir + "/image1.jpg");
			Bitmap bitmap2 = BitmapFactory.decodeFile(mLocalDir + "/image2.jpg");
			if (bitmap1 != null)
				mImage1.setImageBitmap(bitmap1);
			if (bitmap2 != null)
				mImage2.setImageBitmap(bitmap2);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

        mFlipper.setInAnimation(Util.inFromRightAnimation());
        mFlipper.setOutAnimation(Util.outToLeftAnimation());
        mFlipper.showNext();  
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.networkedserial);
		mBuffer = new byte[1024];
		mStringBuffer = new StringBuffer(500000);
		mShowSerialInput = true;
		mReception = (TextView) findViewById(R.id.TextViewReception);
		mScroller = (ScrollView) findViewById(R.id.scroller);
		mProgressBar = (ProgressBar) findViewById(R.id.ProgressBar1);
		mPingIP = (EditText) findViewById(R.id.EditTextPingIP);
		mImage1 = (ImageButton) findViewById(R.id.imageButton1);
		mImage2 = (ImageButton) findViewById(R.id.imageButton2);
		mFlipper = (ViewFlipper) findViewById(R.id.flipper);
		
		mButtonInit = (Button)findViewById(R.id.ButtonInit);
		mButtonInit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing Send!", 1).show();

				if (mSerialPort != null) {
					new ExecuteCommandTask().execute("init");
				} else {
					Toast.makeText(getApplicationContext(), "Error: Serial not ready", 1).show();
				}
			}
		});

		mButtonIP = (Button)findViewById(R.id.button1);
		mButtonIP.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing IP", 1).show();

				mReception.append("Getting local IP address...\n");
				mReception.append(Util.getLocalIpAddress());
				mReception.append("Done.\n");
			}
		});

		mButtonPing = (Button)findViewById(R.id.button2); 
		mButtonPing.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing Ping", 1).show();

				mReception.append("Pinging server...\n");
				String url = mPingIP.getText().toString();
				mReception.append(Util.pingUrl(url));
				mReception.append(Util.pingUrl(url));
				mReception.append(Util.pingUrl(url));
				mReception.append(Util.pingUrl(url));
				mReception.append("Done.\n");
			}
		});
		mButtonBack = (Button)findViewById(R.id.ButtonBack);
		mButtonBack.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
		         mFlipper.setInAnimation(Util.inFromLeftAnimation());
		         mFlipper.setOutAnimation(Util.outToRightAnimation());
		         mFlipper.showPrevious();     
			}
		});

		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN); 
	}

	private class ExecuteCommandTask extends AsyncTask<String, Integer, Boolean> {

		protected void onPreExecute() {
			setUIDisabled();
		}

		protected void send(String cmd) {
			/* Prepare the command */	
			mReceptionBuffer = "";
			mStringBuffer.delete(0, mStringBuffer.length());
			mIsExpectedResult = false;
			mBuffer = cmd.getBytes();

			Log.d(TAG, "Sending '" + cmd + "'");
			mSendingThread = new SendingThread();
			mSendingThread.start();
		}

		protected void setTimeout(int ms) {
			mTimeout = ms;
		}

		protected int getTimeout() {
			return mTimeout;
		}

		protected boolean expect(String expected) {
			int ms_count = 0;
			mExpectedResult = expected;

			setTimeout(3000);

			/* Wait for the response */
			while (!mIsExpectedResult) {

				Log.d(TAG, "Scaning '" + mExpectedResult + "' " + ms_count);
				publishProgress(ms_count);

				mReceptionBuffer = mStringBuffer.toString();
				if (mReceptionBuffer.indexOf(mExpectedResult) > 0) {
					mIsExpectedResult = true;
					Log.d(TAG, "Expect found!");
					publishProgress(CMD_SUCCESS);
				}

				SystemClock.sleep(100);
				ms_count = ms_count + 100;
				if (ms_count > getTimeout()) {
					Log.d(TAG, "Expect Timeout!");
					publishProgress(CMD_TIMEOUT);
					break;
				}
			}

			return mIsExpectedResult;
		}

		protected boolean send_cmd(String cmd, String expect) {
			boolean r;

			send(cmd + "\n");
			r = expect(expect);
			return r;
		}
		
		protected Boolean get_remote_file(String remoteFile, String localFile) {
			boolean storeTempFile = false;
			File tf = null;
			FileWriter w = null;
			StringBuffer buff = new StringBuffer();
			boolean keepWriting = true;
			int count = 0;
			
			/* Disable input visuals during file transfer */
			mShowSerialInput = false;
			
			/* Send the command to transfer the file */
			send_cmd("./b64-armv7 -e " + remoteFile + ";\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n", "root@beagleboard");
			
			/* Count input lines */
			int lines = Util.getLineCount(mReceptionBuffer);
			
			try {
				BufferedReader br = new BufferedReader(new StringReader(mReceptionBuffer));//.replaceAll("\\r\\z", "")));
				while (keepWriting) {
					String t = br.readLine();
					if ( (t == null) || t.contains("root@beagleboard"))
						break;
					if ((count > 0) && (count < (lines))) {
						buff.append(t);
					}
					count++;
				}
				
				if (storeTempFile) {
					tf = new File(localFile + ".tmp");
					w = new FileWriter(tf);
					
					w.write(buff.toString());
					w.flush();
					w.close();
				}
				
				
				/* Read the file, decode, and finally store */
				byte[] b64_bytes = buff.toString().getBytes(); //getBytesFromFile(tf);
				
				try {
					byte[] b_dec = Base64.decode(b64_bytes,Base64.DEFAULT);
					File f = new File(localFile);
					FileOutputStream os = new FileOutputStream(f);
					os.write(b_dec);
					os.flush();
					os.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				/* Delete the temp file */
				//tf.delete();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			mShowSerialInput = true;
			
			return true;
		}
		
		public boolean prepareLocalDirectory(String path) {
			File dir = new File(path);
			Util.deleteEntireDirectory(dir);
			dir.mkdirs();
			
			return true;
		}

		protected Boolean doInBackground(String... cmd) {
			boolean r;

			r = (prepareLocalDirectory(mLocalDir) &&
					send_cmd("root", "root@beagleboard") &&
					get_remote_file("/home/root/chris.jpg", mLocalDir + "/image1.jpg") &&
					get_remote_file("/home/root/chris.jpg", mLocalDir + "/image2.jpg"));
			return r;
		}

		protected void onProgressUpdate(Integer... progress) {
			mProgressBar.setMax(getTimeout());
			mProgressBar.setProgress(progress[0]);
			if (mShowSerialInput)
				mReception.setText(mReceptionBuffer);
			mScroller.smoothScrollTo(0, mReception.getBottom());
			if (progress[0] == CMD_SUCCESS) {
				Toast.makeText(getApplicationContext(), "Success!", 1).show();
			} else if (progress[0] == CMD_TIMEOUT){
				Toast.makeText(getApplicationContext(), "Error: timeout running command.", 1).show();
			} else {
				//just update the UI with some progress.
			}
		}

		protected void onPostExecute(Boolean result) {
			setUIEnabled();
		}
	}

	private class SendingThread extends Thread {
		@Override
		public void run() {
			if (mOutputStream == null)
				return;

			try {
				mOutputStream.write(mBuffer);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	protected void onDataReceived(final byte[] buffer, final int size) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (mStringBuffer == null)
					return;
				mStringBuffer.append(new String(buffer, 0, size));
			}
		});
	}
}
