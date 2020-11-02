package com.nuchwezi.dnaphistrion;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import android.telephony.SmsManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.builder.Builders;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

import androidx.annotation.NonNull;

/**
 * Created by NemesisFixx on 25-Jun-16.
 */
public class Utility {

    @NonNull
    public static String JSONObjectToHumaneString(JSONObject act, Set<String> filterOutThese) {
        StringBuilder builder = new StringBuilder();
        for(Iterator<String> iter = act.keys(); iter.hasNext();) {
            String key = iter.next();

            if(filterOutThese != null)
            if(filterOutThese.contains(key))
                continue;

            try {
                builder.append(String.format("%s: %s\n", key.toUpperCase(), String.valueOf(act.get(key))));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return builder.toString();
    }

    public static String convertStreamToString(InputStream is) throws IOException {
        // http://www.java2s.com/Code/Java/File-Input-Output/ConvertInputStreamtoString.htm
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        Boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if(firstLine){
                sb.append(line);
                firstLine = false;
            } else {
                sb.append("\n").append(line);
            }
        }
        reader.close();
        return sb.toString();
    }



    public static String readFileToString(String filePath)  {
        File fl = new File(filePath);
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(fl);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        String ret = null;
        try {
            ret = convertStreamToString(fin);
        } catch (IOException e) {
            return null;
        }
        //Make sure you close all streams.
        try {
            fin.close();
        } catch (IOException e) {
            return null;
        }
        return ret;
    }

    public static int getComplementaryColor(int colorToInvert) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(colorToInvert), Color.green(colorToInvert),
                Color.blue(colorToInvert), hsv);
        hsv[0] = (hsv[0] + 180) % 360;
        return Color.HSVToColor(hsv);
    }

    public static int getContrastVersionForColor(int color) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color),
                hsv);
        if (hsv[2] < 0.5) {
            hsv[2] = 0.99f;//0.7f;
        } else {
            hsv[2] = 0.3f;
        }
        hsv[1] = hsv[1] * 0.25f;//0.2f
        return Color.HSVToColor(hsv);
    }

    public static void sendSMS(final Context context, String phone_number, String message, final ParametricCallback successParametricCallback, final ParametricCallback errorParametricCallback) {
        String SENT = "SMS_SENT";
        String DELIVERED = "SMS_DELIVERED";

        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0,
                new Intent(SENT), 0);

        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0,
                new Intent(DELIVERED), 0);

        int partsCount;
        final int[] partsHandled = {0};

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        ArrayList<PendingIntent> sentPIntents = new ArrayList<>();
        ArrayList<PendingIntent> deliverPIntents = new ArrayList<>();
        for(String m: parts){
            sentPIntents.add(sentPI);
            deliverPIntents.add(deliveredPI);
        }
        partsCount = parts.size();

        //---when the SMS has been sent---
        final int finalPartsCount1 = partsCount;
        context.registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        if(++partsHandled[0] == finalPartsCount1)
                        successParametricCallback.call("SMS SENT");
                        else
                        showToast("SMS SENT", context);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        if(++partsHandled[0] == finalPartsCount1)
                        errorParametricCallback.call("Generic Failure");
                        else
                        showToast("Generic Failure", context);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        if(++partsHandled[0] == finalPartsCount1)
                        errorParametricCallback.call("No Service!");
                        else
                            showToast("No Service!", context);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        if(++partsHandled[0] == finalPartsCount1)
                        errorParametricCallback.call("NULL PDU!");
                        else
                            showToast("NULL PDU!", context);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        if(++partsHandled[0] == finalPartsCount1)
                        errorParametricCallback.call("RADIO Off!");
                        else
                            showToast("RADIO Off!", context);
                        break;
                }
            }
        }, new IntentFilter(SENT));

        //---when the SMS has been delivered---
        final int finalPartsCount = partsCount;
        context.registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        if(++partsHandled[0] == finalPartsCount)
                        successParametricCallback.call("SMS Delivered");
                        else
                            showToast("SMS Delivered", context);
                        break;
                    case Activity.RESULT_CANCELED:
                        if(++partsHandled[0] == finalPartsCount)
                        errorParametricCallback.call("SMS Not Delivered");
                        else
                            showToast("SMS Not Delivered", context);
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));


        ///smsManager.sendTextMessage(phone_number, null, message, sentPI, deliveredPI); // would have worked, but might fail for long sms messages...

        smsManager.sendMultipartTextMessage(phone_number, null, parts, sentPIntents, deliverPIntents);
    }

    public static JsonObject toJsonObject(JSONObject object) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(object.toString(),JsonObject.class);
        return jsonObject;
    }

    public static Set<String> arrayToSet(String[] strings) {
        return new HashSet<>(Arrays.asList(strings));
    }

    public static JSONObject removeFields(JSONObject jsonObject, String[] keys) {
        JSONObject sttrippedObject = new JSONObject();
        Set<String> keySet = arrayToSet(keys);

        for(Iterator<String> iter = jsonObject.keys(); iter.hasNext();) {
            String key = iter.next();
            if(!keySet.contains(key)) {
                try {
                    sttrippedObject.put(key, jsonObject.get(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return sttrippedObject;
    }

    public static JSONArray removeField(JSONArray jsonArray, int index) {

        JSONArray newArray = new JSONArray();

        for(int i = 0; i < jsonArray.length(); i++)
            if(i != index)
                try {
                    newArray.put(jsonArray.get(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

        return newArray;
    }

    public static boolean DeleteFile(String path) {
        File file = new File(path);
        return file.delete();
    }

    public static void saveBitmapToFile(Bitmap bitmap, String filename,
                                        int qualityPercent) {
        try {
            FileOutputStream out = new FileOutputStream(filename);
            bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent, out);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setThumbnailImage(final ImageView imgView, File imgFile) {

        		/* There isn't enough memory to open up more than a couple camera photos */
        /* So pre-scale the target bitmap into which the file is decoded */

		/* Get the size of the ImageView */
        int targetW = imgView.getWidth();
        int targetH = imgView.getHeight();

		/* Get the size of the image */
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imgFile.getAbsolutePath(), bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

		/* Figure out which way needs to be reduced less */
        int scaleFactor = 1;
        if ((targetW > 0) || (targetH > 0)) {
            scaleFactor = Math.min(photoW / targetW, photoH / targetH);
        }

		/* Set bitmap options to scale the image decode target */
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

		/* Decode the JPEG file into a Bitmap */
        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), bmOptions);

		/* Associate the Bitmap to the ImageView */
        imgView.setImageBitmap(bitmap);
        imgView.setVisibility(View.VISIBLE);
    }

    public static String imageToDataURI(Context context, String selectedImagePath, String mimeType) {

        Bitmap bm = null;
        try {
            bm = MediaStore.Images.Media.getBitmap(context.getContentResolver() , Uri.parse(selectedImagePath));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
        byte[] byteArrayImage = baos.toByteArray();

        String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);

        return String.format("data:%s;base64,%s", mimeType,encodedImage);

    }

    public static String fileDataURI(String filePath) {
        // source file
        File file = new File(filePath);

        // check content type of the file
        String contentType = Utility.probeFileDataType(filePath);

        // read data as byte[]
        byte[] data = new byte[0];
        try {
            data = org.apache.commons.io.FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // convert byte[] to base64(java7)
        String base64str = Base64.encodeToString(data,
                Base64.NO_WRAP);

        // convert byte[] to base64(java8)
        // String base64str = Base64.getEncoder().encodeToString(data);

        // cretate "data URI"
        StringBuilder sb = new StringBuilder();
        sb.append("data:");
        sb.append(contentType);
        sb.append(";base64,");
        sb.append(base64str);

        return sb.toString();
    }

    public static String probeFileDataType(String filePath) {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(filePath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if(is == null)
            return null;

        String mimeType = null;
        try {
            mimeType = URLConnection.guessContentTypeFromStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mimeType;
    }

    public static void postHTTPJsonArray(Context context, String url,
                                         HashMap<String, String> httpPOSTNameValuePairs, HashMap<String, String> files, JsonArray jsonBody, final ParametricCallback successParametricCallback, final ParametricCallback errorParametricCallback) {


        Log.d(Tag, String.format("HTTP POST, TARGET URI: %s", url));


        Builders.Any.B builder = Ion.with(context)
                .load(HTTP_METHODS.POST, url).setTimeout(60 * 60 * 1000);


        if (jsonBody != null) {
            Builders.Any.F l;

            l = builder.setJsonArrayBody(jsonBody);

            if (l != null) {
                l.asString()
                        .setCallback(new FutureCallback<String>() {
                            @Override
                            public void onCompleted(Exception e, String result) {

                                if ((e == null) || (result.trim().length() == 0))
                                    successParametricCallback.call(result);
                                else
                                    errorParametricCallback.call(String.format("Error: %s || Status: %s", String.valueOf(e), result));
                            }
                        });
            }

        } else {

            Builders.Any.M l = null;

            if (files != null) {
                for (String name : files.keySet()) {
                    try {
                        l = builder.setMultipartFile(name, new File(files.get(name)));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (httpPOSTNameValuePairs != null) {
                for (String name : httpPOSTNameValuePairs.keySet()) {
                    l = builder.setMultipartParameter(name, httpPOSTNameValuePairs.get(name));
                }
            }

            if (l != null) {
                l.asString()
                        .setCallback(new FutureCallback<String>() {
                            @Override
                            public void onCompleted(Exception e, String result) {

                                if ((e == null) || (result.trim().length() == 0))
                                    successParametricCallback.call(result);
                                else
                                    errorParametricCallback.call(String.format("Error: %s || Status: %s", String.valueOf(e), result));
                            }
                        });
            }
        }


    }

    public static JsonArray toJsonArray(JSONArray jArray) {
        Gson gson = new Gson();
        JsonArray jsonArray = gson.fromJson(jArray.toString(),JsonArray.class);
        return jsonArray;
    }

    public static String currentTimestampUTCISO(){
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        df.setTimeZone(tz);
        String nowAsISO = df.format(new Date());
        return nowAsISO;
    }

    public static boolean isURL(String uri) {
        final URL url;
        try {
            url = new URL(uri);
        } catch (Exception e1) {
            return false;
        }
        return url.getProtocol().startsWith("http");
    }

    public static ArrayList<String> JSONArrayToStrList(JSONArray jValList) {
        ArrayList<String> list = new ArrayList<>();
        if(jValList == null)
            return list;

        for(int i = 0; i < jValList.length(); i++) {
            try {
                list.add(jValList.getString(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return list;
    }

    public static boolean getSettingB(String KEY, boolean DEFAULT, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        try {
            Boolean val = sharedPrefs.getBoolean(KEY, DEFAULT);
            return val;
        }catch (Exception e){
            try {
                return Boolean.valueOf(sharedPrefs.getString(KEY, String.valueOf(DEFAULT)));
            }catch (Exception ee){
                return DEFAULT;
            }
        }
    }

    public static Bitmap bitmapFromDataUri(String cloneVal) {
        String[] parts = cloneVal.split("base64,");
        if(parts.length == 2) {
            byte[] decodedString = Base64.decode(parts[1], Base64.DEFAULT);
            Bitmap bitMap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            return bitMap;
        }
        return null;
    }

    public static boolean checkURLAccessible(String uri) {
        URL url;
        try {
            url = new URL(uri);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        int code = 0;
        try {
/*            StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(threadPolicy);*/
            code = connection.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if(code == 200) {
            return true;
        } else {
            return false;
        }
    }

    public static class DB_KEYS {
        public static final String UUID_LIST = "PERSONA_UUIDS";
        public static final String PERSONA_DICTIONARY = "PERSONA_DICTIONARY";
        public static final String ACTIVE_PERSONA_UUID = "ACTIVE_PERSONA_UUID";
        public static final String SAVED_ACTS = "SAVED_ACTS";
        public static final String AUTO_SAVE = "AUTO_SAVE";
    }

    public static void setSetting(String KEY, String VALUE, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(KEY, VALUE);
        editor.commit();
    }

    public static boolean getSetting(String KEY, boolean DEFAULT, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(KEY, DEFAULT);
    }

    public static String getSetting(String KEY, String DEFAULT, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        return sharedPrefs.getString(KEY, DEFAULT);
    }

    public static boolean hasSetting(String KEY, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        return sharedPrefs.contains(KEY);
    }

    public static String toTitleCase(String str) {

        if (str == null) {
            return null;
        }

        boolean space = true;
        StringBuilder builder = new StringBuilder(str);
        final int len = builder.length();

        for (int i = 0; i < len; ++i) {
            char c = builder.charAt(i);
            if (space) {
                if (!Character.isWhitespace(c)) {
                    // Convert to title case and switch out of whitespace mode.
                    builder.setCharAt(i, Character.toTitleCase(c));
                    space = false;
                }
            } else if (Character.isWhitespace(c)) {
                space = true;
            } else {
                builder.setCharAt(i, Character.toLowerCase(c));
            }
        }

        return builder.toString();
    }

    /*
	 * Will create directory on the External Storage Card with the given dirName
	 * name.
	 *
	 * Throws an exception is dirName is null, and returns the name of the
	 * created directory if successful
	 */
    public static String createSDCardDir(String dirName, File internalFilesDir) {

        Log.d(Tag, "Creating Dir on sdcard...");

        if (dirName == null) {
            Log.e(Tag, "No Directory Name Specified!");
            return null;
        }

        File exDir = Environment.getExternalStorageDirectory();

        if (exDir != null) {

            File folder = new File(exDir, dirName);

            boolean success = false;

            if (!folder.exists()) {
                success = folder.mkdirs();
                Log.d(Tag, "Created Dir on sdcard...");
            } else {
                success = true;
                Log.d(Tag, "Dir exists on sdcard...");
            }

            if (success) {
                return folder.getAbsolutePath();
            } else {
                Log.e(Tag, "Failed to create on sdcard...");
                return null;
            }
        } else {

            File folder = new File(internalFilesDir, dirName);

            boolean success = false;

            if (!folder.exists()) {
                success = folder.mkdirs();
                Log.d(Tag, "Created Dir on sdcard...");
            } else {
                success = true;
                Log.d(Tag, "Dir exists on sdcard...");
            }

            if (success) {
                return folder.getAbsolutePath();
            } else {
                Log.e(Tag, "Failed to create on sdcard...");
                return null;
            }
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        // if no network is available networkInfo will be null, otherwise check
        // if we are connected
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static class HTTP_METHODS {
        public static final String POST = "POST";
        public static final String GET = "GET";
    }

    public static void getHTTP(Context context, String url, final ParametricCallback successParametricCallback, final ParametricCallback errorParametricCallback ) {

        Log.d(Tag, String.format("HTTP GET, FETCH URI: %s", url));


        Ion.with(context)
                .load(HTTP_METHODS.GET, url)
                .asString()
                .setCallback(new FutureCallback<String>() {
                    @Override
                    public void onCompleted(Exception e, String result) {

                        if (e == null)
                            successParametricCallback.call(result);
                        else
                            errorParametricCallback.call(String.format("Exception: %s || Status: %s", String.valueOf(e), result));
                    }
                });

    }

    public static void postHTTP(Context context, String url,
                                HashMap<String, String> httpPOSTNameValuePairs, HashMap<String, String> files, JsonObject jsonBody, final ParametricCallback successParametricCallback, final ParametricCallback errorParametricCallback) {


        Log.d(Tag, String.format("HTTP POST, TARGET URI: %s", url));


        Builders.Any.B builder = Ion.with(context)
                .load(HTTP_METHODS.POST, url).setTimeout(60 * 60 * 1000);


        if (jsonBody != null) {
            Builders.Any.F l
                    = null;
            l = builder.setJsonObjectBody(jsonBody);


            if (l != null) {
                l.asString()
                        .setCallback(new FutureCallback<String>() {
                            @Override
                            public void onCompleted(Exception e, String result) {

                                if ((e == null) || (result.trim().length() == 0))
                                    successParametricCallback.call(result);
                                else
                                    errorParametricCallback.call(String.format("Error: %s || Status: %s", String.valueOf(e), result));
                            }
                        });
            }

        } else {

            Builders.Any.M l = null;

            if (files != null) {
                for (String name : files.keySet()) {
                    try {
                        l = builder.setMultipartFile(name, new File(files.get(name)));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (httpPOSTNameValuePairs != null) {
                for (String name : httpPOSTNameValuePairs.keySet()) {
                    l = builder.setMultipartParameter(name, httpPOSTNameValuePairs.get(name));
                }
            }

            if (l != null) {
                l.asString()
                        .setCallback(new FutureCallback<String>() {
                            @Override
                            public void onCompleted(Exception e, String result) {

                                if ((e == null) || (result.trim().length() == 0))
                                    successParametricCallback.call(result);
                                else
                                    errorParametricCallback.call(String.format("Error: %s || Status: %s", String.valueOf(e), result));
                            }
                        });
            }
        }


    }


    public static String Tag = HistrionMainActivity.TAG;

    /*
     * Display a toast with the default duration : Toast.LENGTH_SHORT
     */
    public static void showToast(String message, Context context) {
        showToast(message, context, Toast.LENGTH_SHORT);
    }

    /*
     * Display a toast with given Duration
     */
    public static void showToast(String message, Context context, int duration) {
        Toast.makeText(context, message, duration).show();
    }

    public static void showAlert(String title, String message, Context context) {
        showAlert(title, message, R.drawable.notify, context, null, null,null);
    }

    public static void showAlert(String title, String message, int iconId, Context context) {
        showAlert(title, message, iconId, context,  null, null,null);
    }

    public static void showAlert(String title, String message, Context context, Runnable yesCallback, Runnable noCallback, Runnable cancelCallback ) {
        showAlert(title, message, R.drawable.notify, context, yesCallback, noCallback,cancelCallback);
    }

    public static void showAlert(String title, String message, int iconId, Context context, Runnable yesCallback, Runnable noCallback, Runnable cancelCallback ) {
        showAlertFactory(title, message,iconId, context, yesCallback, noCallback,cancelCallback);
    }

    public static void showAlertFactory(String title, String message, int iconId,
                                        Context context, final Runnable yesCallback, final Runnable noCallback, final Runnable cancelCallback) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setIcon(iconId);
            builder.setTitle(title);
            builder.setMessage(message);

            if(yesCallback != null){
                builder.setPositiveButton( noCallback == null ? "OK"  : "YES", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        yesCallback.run();
                    }
                });
            }

            if(noCallback != null){
                builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        noCallback.run();
                    }
                });
            }

            if(cancelCallback != null){
                builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cancelCallback.run();
                    }
                });
            }

            AlertDialog alert = builder.create();
            alert.show();
        } catch (Exception e) {
            Log.e(Tag, "Alert Error : " + e.getMessage());
        }

    }

    public static void showAlertPrompt(String title, final boolean allowEmpty, boolean addMask, int iconId,
                                       final Context context, final ParametricCallback yesCallback, final Runnable cancelCallback) {
        try {
            LayoutInflater layoutInflaterAndroid = LayoutInflater.from(context);
            final View dialogView = layoutInflaterAndroid.inflate(R.layout.dnap_alert_prompt, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setView(dialogView);

            builder.setIcon(iconId);
            builder.setTitle(title);

            if(addMask){
                EditText editText = (EditText) dialogView.findViewById(R.id.eTxtPromptValue);
                editText.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setTransformationMethod(new PasswordTransformationMethod());
            }

            if(yesCallback != null){
                builder.setPositiveButton( "OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        EditText editText = (EditText) dialogView.findViewById(R.id.eTxtPromptValue);
                        String value = editText.getText().toString();
                        if(!allowEmpty){
                            if(value.trim().length() == 0){
                                Utility.showToast("Please set a value!", context);
                            }
                        }
                        yesCallback.call(value);
                    }
                });
            }

            if(cancelCallback != null){
                builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cancelCallback.run();
                    }
                });
            }

            AlertDialog alert = builder.create();
            alert.show();
        } catch (Exception e) {
            Log.e(Tag, "Alert Error : " + e.getMessage());
        }

    }

    public static int getVersionNumber(Context context) {
        PackageInfo pinfo = null;
        try {
            pinfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return pinfo != null ? pinfo.versionCode : 1;
    }

    public static String getVersionName(Context context) {
        PackageInfo pinfo = null;
        try {
            pinfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return pinfo != null ? pinfo.versionName : "DEFAULT";
    }

    public class PREFERENCES {
        public static final String PREF_KEY_SETTINGS_CACHE_ACTIVE_PERSONA = "PREF_KEY_SETTINGS_CACHE_ACTIVE_PERSONA";
        public static final String PREF_KEY_SETTINGS_ALLOW_DELETING_ACTIVE_PERSONA = "PREF_KEY_SETTINGS_ALLOW_DELETING_ACTIVE_PERSONA";
        public static final String PREF_KEY_SETTINGS_CHANNELS = "PREF_KEY_SETTINGS_CHANNELS";
        public static final String PREF_KEY_SETTINGS_AUTO_INSTALL_CHANNEL = "PREF_KEY_SETTINGS_AUTO_INSTALL_CHANNEL";
        public static final String PREF_KEY_SETTINGS_AUTO_INSTALL_FROM_CHANNEL = "PREF_KEY_SETTINGS_AUTO_INSTALL_FROM_CHANNEL";
        public static final String PREF_KEY_SETTINGS_AC_PHONE = "PREF_KEY_SETTINGS_AC_PHONE";
        public static final String PREF_KEY_SETTINGS_AC_NAME = "PREF_KEY_SETTINGS_AC_NAME";
        public static final String USER_INFO = "USER_PROFILE";
    }

}
