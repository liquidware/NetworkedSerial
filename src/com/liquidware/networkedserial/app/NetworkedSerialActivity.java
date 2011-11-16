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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;
import com.liquidware.networkedserial.app.R;

public class NetworkedSerialActivity extends SerialPortActivity {
	private static final String TAG = "NetworkedSerialActivity";

	private static final int CMD_SUCCESS  = -1;
	private static final int CMD_TIMEOUT  = -2;

	SendingThread mSendingThread;
	volatile byte[] mBuffer;
	static TextView mReception;
	EditText mPingIP;
	ScrollView mScroller;
	ProgressBar mProgressBar;
	public static Button mButtonInit;
	public static Button mButtonIP;
	public static Button mButtonPing;
	volatile String mReceptionBuffer;
	volatile StringBuffer mStringBuffer;
	volatile String mExpectedResult;
	volatile boolean mIsExpectedResult;
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
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.networkedserial);
		mBuffer = new byte[1024];
		mStringBuffer = new StringBuffer(500000);
		mReception = (TextView) findViewById(R.id.TextViewReception);
		mScroller = (ScrollView) findViewById(R.id.scroller);
		mProgressBar = (ProgressBar) findViewById(R.id.ProgressBar1);
		mPingIP = (EditText) findViewById(R.id.EditTextPingIP);

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
				displayLocalIpAddress();
				mReception.append("Done.\n");
			}
		});

		mButtonPing = (Button)findViewById(R.id.button2); 
		mButtonPing.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Toast.makeText(getApplicationContext(), "Performing Ping", 1).show();

				mReception.append("Pinging server...\n");
				String url = mPingIP.getText().toString();
				pingUrl(url);
				pingUrl(url);
				pingUrl(url); 
				pingUrl(url);
				mReception.append("Done.\n");
			}
		});
	}

	public void displayLocalIpAddress() {
		try {
			Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 
			Log.d(TAG, "Getting network interfaces");
			while(en.hasMoreElements()) {
				Log.d(TAG, "en has more");
				NetworkInterface intf = en.nextElement();
				Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
				while(enumIpAddr.hasMoreElements()) {
					Log.d(TAG, "enumIpAddr has more");
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						Log.d(TAG, "Getting IP");
						mReception.append(intf.getName() + ":" + 
								inetAddress.getHostAddress().toString() + "\n");
					}
				}

			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
	}

	public static boolean pingUrl(final String address) {
		try {
			final URL url = new URL("http://" + address);
			final HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setConnectTimeout(1000 * 10); // mTimeout is in seconds
			final long startTime = System.currentTimeMillis();
			urlConn.connect();
			final long endTime = System.currentTimeMillis();
			if (urlConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				String out;
				out = "Reply from " + url + ": Time=" + (endTime - startTime) + " ms\n";
				mReception.append(out);
				return true;
			} else {
				mReception.append("No response from server\n");
			}
		} catch (final MalformedURLException e1) {
			e1.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return false;
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

			setTimeout(240000);

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

			send(cmd);
			r = expect(expect);
			return r;
		}

		protected Boolean doInBackground(String... cmd) {

			boolean r;

			r = (send_cmd("cd /home; sleep 3\n", "root@beagleboard") &&
					send_cmd("ls -l; sleep 3\n", "root@beagleboard") &&
					send_cmd("cd /home/root; sleep 3\n", "root@beagleboard") &&
					send_cmd("cat flower.jpg.b64\n", "root@beagleboard") &&
					send_cmd("ls -l; sleep 3\n", "root@beagleboard"));

			try {
				File f = new File(Environment.getExternalStorageDirectory(), "output.txt");
				FileWriter w = new FileWriter(f);

				Log.d(TAG, Environment.getExternalStorageState());
				//Write Disabled for now
				//w.write(mReceptionBuffer.replaceAll("\\n", "").replaceAll("\\r", ""));
				/* ensure that everything is
				 * really written out and close */
				w.flush();
				w.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return r;
		}

		protected void onProgressUpdate(Integer... progress) {
			mProgressBar.setMax(getTimeout());
			mProgressBar.setProgress(progress[0]);

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
