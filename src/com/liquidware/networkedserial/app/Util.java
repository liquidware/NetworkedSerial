package com.liquidware.networkedserial.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

public class Util {
	private static final String TAG = "Util";
	
	/**
	 * Count number of '\r' terminated lines in a string.
	 * @param inputString The input string to count lines.
	 * @return The number of lines in the string.
	 */
	public static int getLineCount(String inputString) {
		Matcher m = Pattern.compile("\r").matcher(inputString);
		int lines = 1;
		while (m.find())
		    lines ++;
		
		return lines;
	}
	
	/**
	 *  Recursively deletes the files inside the directory and the directory itself.
	 *  
	 * @param dir The directory to be deleted
	 */
	public static void deleteEntireDirectory(File dir) {
		if (dir.isDirectory()) {
	        String[] children = dir.list();
	        for (int i = 0; i < children.length; i++) {
	            new File(dir, children[i]).delete();
	        }
	        dir.delete();
		}
	}
	
	/**
	 *  Read an entire file into a byte array.
	 *  
	 * @param file The file to read.
	 * @return Returns the contents of the file in a byte array
	 * @throws IOException
	 */
	public static byte[] getBytesFromFile(File file) throws IOException {
	    InputStream is = new FileInputStream(file);

	    // Get the size of the file
	    long length = file.length();

	    // You cannot create an array using a long type.
	    // It needs to be an int type.
	    // Before converting to an int type, check
	    // to ensure that file is not larger than Integer.MAX_VALUE.
	    if (length > Integer.MAX_VALUE) {
	        // File is too large
	    }

	    // Create the byte array to hold the data
	    byte[] bytes = new byte[(int)length];

	    // Read in the bytes
	    int offset = 0;
	    int numRead = 0;
	    while (offset < bytes.length
	           && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
	        offset += numRead;
	    }

	    // Ensure all the bytes have been read in
	    if (offset < bytes.length) {
	        throw new IOException("Could not completely read file "+file.getName());
	    }

	    // Close the input stream and return bytes
	    is.close();
	    return bytes;
	}
	
	/**
	 *  Performs a ping on the specified HTTP URL.
	 * @param address The address to perform the ping
	 * @return The response from the server. Contains 'Error' on fail.
	 */
	public static String pingHttpUrl(String address) {
		String response = "";
		
		try {
			URL url = new URL(address);
			Log.d(TAG, "Pinging host '" + url.getHost() + "'");
			
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			
			urlConn.setConnectTimeout(1000 * 10); // mTimeout is in seconds
			long startTime = System.currentTimeMillis();
			urlConn.connect();
			long endTime = System.currentTimeMillis();
			if (urlConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				response = "Reply from " + url + ": Time=" + (endTime - startTime) + " ms\n";
			} else {
				response = "No response from server\n";
			}
		} catch (final MalformedURLException e1) {
		    e1.printStackTrace();
			Log.d(TAG, "MalformedURLException " + e1.getStackTrace().toString());
			response = "Error\n";
		} catch (final Exception e) {
		    e.printStackTrace();
			Log.d(TAG, "Exception " + e.getStackTrace().toString());
			response = "Error\n";
		}
		return response;
	}
	
	/**
	 * Gets the local host IP address of all adapters. Excludes the loopback address.
	 * @return The host IP address of all adapters.
	 */
	public static String getLocalIpAddress() {
		String response = "";
		Log.d(TAG, "getLocalIpAddress"); 
		
		try {
			Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 	
			
			while(en.hasMoreElements()) {
			    Log.d(TAG, "Next network interface");
				NetworkInterface intf = en.nextElement();
				Log.d(TAG, "Name=" + intf.getDisplayName());
				Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
				while(enumIpAddr.hasMoreElements()) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						response += (intf.getName() + ":" + 
								    inetAddress.getHostAddress().toString());
					}
				}
			}
		} catch (SocketException ex) {
		    ex.printStackTrace();
			//Log.e(TAG, ex.getMessage().toString());
			response = "Error\n";
		}
		return response;
	}
	
	public static Animation inFromRightAnimation() {

		Animation inFromRight = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT,  +1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
		);
		inFromRight.setDuration(500);
		inFromRight.setInterpolator(new AccelerateInterpolator());
		return inFromRight;
	}
	
	public static Animation outToLeftAnimation() {
		Animation outtoLeft = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,  -1.0f,
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
		);
		outtoLeft.setDuration(500);
		outtoLeft.setInterpolator(new AccelerateInterpolator());
		return outtoLeft;
	}

	public static Animation inFromLeftAnimation() {
		Animation inFromLeft = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT,  -1.0f, Animation.RELATIVE_TO_PARENT,  0.0f,
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
		);
		inFromLeft.setDuration(500);
		inFromLeft.setInterpolator(new AccelerateInterpolator());
		return inFromLeft;
	}
	
	public static Animation outToRightAnimation() {
		Animation outtoRight = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,  +1.0f,
				Animation.RELATIVE_TO_PARENT,  0.0f, Animation.RELATIVE_TO_PARENT,   0.0f
		);
		outtoRight.setDuration(500);
		outtoRight.setInterpolator(new AccelerateInterpolator());
		return outtoRight;
	}
}
