package com.nuchwezi.dnaphistrion;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

public class HistrionMainActivity extends AppCompatActivity {

    private String autoInstallChannel; // default DNAP Channel

    public class PERSONA_REFERENCES {
        public static final String DNAP_FEEDBACK_PERSONA_UUID = "bb765c31-6959-49d0-b192-6c83bdab5cb4";
        public static final String KEY_PERSONA_UUID = "PERSONA:UUID";
        public static final String AUTO_INSTALL_CHANNEL = "CHANNEL:NAME";
    }

    private boolean noPersonaLoadedYet = true;
    private HashMap<String, CheckBox[]> checkBoxMap = new HashMap<>();
    private boolean forceReload = false;
    private Set<String> gpsSubscribers = new HashSet<>();
    private String longitude;
    private String latitute;
    private HashMap<String, String[]> gpsCache = new HashMap<>();
    private LinearLayout controllerRootView;

    class DataFiles {
        public String Image;
    }

    public static final String TAG = "DNAP:Histrion";
    private Button btnSubmit;
    private boolean isPosting = false;
    private Button btnSave;
    private final String IMAGECACHE_BASEDIR = "DNAPLib_ImageCache";
    private static final String APKCACHE_BASEDIR = "DNAPLib_APKCache";
    private String dataPath;

    private static class INTENT_MODE {
        private static final int CHOOSE_FILE_REQUESTCODE = 1;
        private static final int SCAN_PERSONA_QRCODE = 2;
        public static final int CHOOSE_PERSONA_FILE_REQUESTCODE = 3;
        public static final int CHOOSE_CAMERA_REQUESTCODE = 4;
    }

    JSONObject activePersona;
    private HashMap<String, FileSelectionParams> fileSelectionMap = new HashMap<>();
    private FileSelectionParams currentFileSelectionParams;
    private HashMap<String, CameraSelectionParams> cameraSelectionMap = new HashMap<>();
    private CameraSelectionParams currentCameraSelectionParams;

    private DBAdapter adapter;
    private Handler handler;

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 299;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dnap_activity_histrion);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        handler = new Handler();

        adapter = new DBAdapter(this);
        adapter.open();

        if(!getOrRequestWriteStoragePermission()){
            Utility.showToast("Please grant this app the permission to write external storage. It's required for images and updates to work well.", this);
        }

        try {
            dataPath = Utility.createSDCardDir(IMAGECACHE_BASEDIR, getFilesDir());
        } catch (Exception e) {
            Log.e(TAG, "Image Path Error : " + e.getMessage());
            Utility.showToast(e.getMessage(), getApplicationContext(),
                    Toast.LENGTH_LONG);
        }

        Intent intent = getIntent();
        if(intent.hasExtra(PERSONA_REFERENCES.KEY_PERSONA_UUID)){
            try {
                String newActiveUUID = intent.getStringExtra(PERSONA_REFERENCES.KEY_PERSONA_UUID);
                setActivePersonaUUID(newActiveUUID);
                activePersona = getActivePersona();
            }catch (Exception e){
                Utility.showAlert("Invalid Persona", "The Persona you tried to launch seems to not exist! Please visit 'Manage Tools' and refresh. Then Try Again.", this);
            }
        }
        if(intent.hasExtra(PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL)){
            try {
                autoInstallChannel = intent.getStringExtra(PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL);
            }catch (Exception e){
                Utility.showAlert("Invalid Channel", "The Channel you tried to subscribe to seems be invalid! Contact app developers to rectify this.", this);
            }
        }

        bootstrapHistorion();

        checkSubscriptionChannels();
    }

    private boolean getOrRequestWriteStoragePermission() {
        if(hasPermissionWriteStorage()){
            return true;
        }else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }

        return false;
    }

    private boolean hasPermissionWriteStorage() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_location_permission)
                        .setMessage(R.string.text_location_permission)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(HistrionMainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Request location updates:
                        initGPS();
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }


    private void checkSubscriptionChannels() {
        String subscriptionChannelSpec = Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_CHANNELS, autoInstallChannel, this);
        String autoInstallChannelSpec = Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_AUTO_INSTALL_CHANNEL, autoInstallChannel, this);
        boolean allowAutoInstall = Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_CHECK_AUTO_INSTALL_FROM_CHANNEL, true, this);
        if((subscriptionChannelSpec == null) && (autoInstallChannelSpec == null))
            return;

        HashMap<String,Boolean> channelAuto = new HashMap<>();

        if(subscriptionChannelSpec != null) {
            ArrayList<String> channels = parseSubscriptionChannels(subscriptionChannelSpec);
            for(String chan: channels)
                channelAuto.put(chan,false);
        }

        if(autoInstallChannelSpec != null) {
            ArrayList<String> channels = parseSubscriptionChannels(autoInstallChannelSpec);
            for(String chan: channels)
                channelAuto.put(chan,allowAutoInstall? true: false);
        }

        if(channelAuto.size() > 0){
            for(String chan: channelAuto.keySet()) {
                loadPersonaFromChannel(chan, channelAuto.get(chan));
            }
        }
    }

    private void loadPersonaFromChannel(String channelQuery, final Boolean autoInstall) {
        if(channelQuery == null) {
            Utility.showToast("Channel is Null!", this);
            return;
        }

        if (!Utility.isNetworkAvailable(this)) {
            Utility.showToast("There's no data connection to allow contacting of the specified Channel Subscription Repository...", this);
            return;
        }

        Uri url = Uri.parse(channelQuery);
        Utility.getHTTP(this, url.toString(), new ParametricCallback() {
            @Override
            public void call(String personaListJSON) { // success
                try {
                    JSONArray personaList = new JSONArray(personaListJSON);
                    boolean canSetActivePersona = true;
                    for(int i = 0; i < personaList.length(); i++){
                        JSONObject persona = personaList.getJSONObject(i);
                        boolean setActivePersona = canSetActivePersona? autoInstall : false;
                        cacheActivePersona(persona,setActivePersona);
                        if(setActivePersona){
                            if(noPersonaLoadedYet)
                            loadNewPersona(persona);
                            else
                                Utility.showToast("A new active persona has been set, but you will have to manually activate it or just restart the app to start using it, since you are already working with another persona right now.", HistrionMainActivity.this);

                        }
                        canSetActivePersona = false;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new ParametricCallback() {
            @Override
            public void call(String status) { // error
                Utility.showToast("Failed to fetch persona list from the specified Channel Subscription Repository!", HistrionMainActivity.this);
            }
        });
    }

    private ArrayList<String> parseSubscriptionChannels(String subscriptionChannelSpec) {
        ArrayList<String> chans = new ArrayList<>();

        String[] chunks = subscriptionChannelSpec.split(Pattern.quote("|"));
        for(String chunk: chunks){
            if(!chunk.startsWith("http")){//it's a default repo query...
                chans.add(makeDefaultChannelRepositoryQuery(chunk));
            }else
                chans.add(chunk);
        }

        return chans;
    }

    private String makeDefaultChannelRepositoryQuery(String s) {
        String template = getString(R.string.DEFAULT_CHANNEL_REPO_QUERY_PREFIX);
        long time = (new Date()).getTime();
        return String.format(template ,s, time);
    }

    private void bootstrapHistorion() {
        setContentView(R.layout.dnap_activity_histrion);


        cameraSelectionMap.clear();
        fileSelectionMap.clear();

        isPosting = false;
        btnSubmit = (Button) findViewById(R.id.btnSubmit);
        /*btnSave = (Button) findViewById(R.id.btnSave);*/

        if(adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)) {
            loadNewPersona(getActivePersona());
        }else {
            btnSubmit.setVisibility(View.INVISIBLE); // hide it, since we won't need it...
            /* btnSave.setVisibility(View.INVISIBLE); // hide it, since we won't need it...*/
            //android.app.ActionBar actionBar = getActionBar();
            //actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));
            android.app.ActionBar actionBar = getActionBar();
            if(actionBar == null) {
                ActionBar supportActionBar = getSupportActionBar();
                supportActionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));
            }else {
                actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));
            }
            setTitle(R.string.app_name);
            // get the container view in which the persona is to be rendered
            LinearLayout linearLayoutControls = (LinearLayout) findViewById(R.id.linLayoutForControls);
            linearLayoutControls.removeAllViews();
            TextView label = new TextView(this);
            label.setText(getString(R.string.getting_started_historion_message));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.setMargins(0, 10, 0, 10); // llp.setMargins(left, top, right, bottom);
            label.setTextColor(Color.BLACK);
            label.setLayoutParams(llp);
            linearLayoutControls.addView(label);
            //Utility.showAlert("Getting Started", , this);

            // and then, we just go to personas and let the users activate one of them
            switchToManagePersonas();

            return;
        }


        if((activePersona == null) || (!Persona.canTransportActs(activePersona))){

            btnSubmit.setVisibility(View.INVISIBLE); // hide it, since we won't need it...
            /* btnSave.setVisibility(View.INVISIBLE); // hide it, since we won't need it...*/

        }else {
            btnSubmit.setVisibility(View.VISIBLE); // might have hidden it previously...
            /*btnSave.setVisibility(View.INVISIBLE); // hide it, since we won't need it...*/

            btnSubmit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(isPosting)
                        return;

                    isPosting = true;

                    JSONObject act = parseAct();
                    if (act != null) {
                        submitActToTheatre(act, Persona.getAppTheatreAddress(activePersona), Persona.getAppTransportMode(activePersona));
                    }else
                        isPosting = false;
                }
            });
            /*btnSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(isPosting)
                        return;

                    isPosting = true;

                    JSONObject act = parseAct();
                    if (act != null) {
                        saveAct(act, Persona.getAppTheatreAddress(activePersona), Persona.getAppTransportMode(activePersona), Persona.getAppName(activePersona));
                    }else
                        isPosting = false;
                }
            });*/
        }

    }

    private void saveAct(JSONObject act, String appTheatreAddress, String appTransportMode, String appName) {
        act = includeUUID(act);
        try {
            act.put(Persona.KEYS.CACHE_THEATRE_ADDRESS,appTheatreAddress);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            act.put(Persona.KEYS.CACHE_TRANSPORT_MODE,appTransportMode);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            act.put(Persona.KEYS.CACHE_APP_NAME,appName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(cameraSelectionMap.size() > 0) {
            JSONObject cameraMap = new JSONObject();
            for(String key: cameraSelectionMap.keySet()){
                try {
                    cameraMap.put(key,cameraSelectionMap.get(key).imagePath);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                act.put(Persona.KEYS.CACHE_CAMERA, cameraMap.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(fileSelectionMap.size() > 0) {
            JSONObject fileMap = new JSONObject();
            for(String key: fileSelectionMap.keySet()){
                try {
                    fileMap.put(key,fileSelectionMap.get(key).labelField.getText().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                act.put(Persona.KEYS.CACHE_FILES, fileMap.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        JSONObject savedActs = null;
        if(adapter.existsDictionaryKey(Utility.DB_KEYS.SAVED_ACTS)){
            try {
                savedActs = new JSONObject(adapter.fetchDictionaryEntry(Utility.DB_KEYS.SAVED_ACTS));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(savedActs == null)
            savedActs = new JSONObject();

        // savedActs is { UUID1 : [Act1, Act2,... ], UUID2: [...], ... }
        String personaUUID = null;
        try {
            personaUUID = act.getString(Persona.KEYS.CACHE_UUID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(personaUUID == null){
            Utility.showAlert(getString(R.string.title_cant_save_act), getString(R.string.invalid_persona_uuid), this);
        }else {

            if (savedActs.has(personaUUID)) {
                JSONArray actsForUUID = null;
                try {
                    actsForUUID = savedActs.getJSONArray(personaUUID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                actsForUUID.put(act);
                try {
                    savedActs.put(personaUUID, actsForUUID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                JSONArray actsForUUID = new JSONArray();
                actsForUUID.put(act);
                try {
                    savedActs.put(personaUUID, actsForUUID);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if (adapter.existsDictionaryKey(Utility.DB_KEYS.SAVED_ACTS)) {
                adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.SAVED_ACTS, savedActs.toString()));
            } else {
                adapter.createDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.SAVED_ACTS, savedActs.toString()));
            }


            Utility.showToast("Submission has been successfully saved. You may continue to capture a new one...", this, Toast.LENGTH_LONG);
            bootstrapHistorion();
        }
    }

    private void submitActToTheatre(JSONObject act, String theatre_address, String transport_mode) {
        Utility.showToast("Wait as the data is being submitted...", HistrionMainActivity.this, Toast.LENGTH_LONG);

        switch (transport_mode){
            case Persona.TRANSPORT_MODES.POST: {
                if (!Utility.isNetworkAvailable(this)) {
                    Utility.showAlert(
                            "No Data Connection!",
                            "Sorry but there's not active data connection to proceed...",
                            R.drawable.ic_no_network, this);
                    return;
                }
                act = includeUUID(act);

                for(Iterator<String> iter = act.keys(); iter.hasNext();) {
                    String fieldID = iter.next();
                    try {
                        if (cameraSelectionMap.containsKey(fieldID)) {
                            act.put(fieldID, Utility.fileDataURI(cameraSelectionMap.get(fieldID).imagePath));
                        }

                        if (fileSelectionMap.containsKey(fieldID)) {
                            act.put(fieldID, Utility.fileDataURI(fileSelectionMap.get(fieldID).labelField.getText().toString()));
                        }
                    }catch (Exception e){}
                }

               {

                    Utility.postHTTP(this, theatre_address, null, null, Utility.toJsonObject(act), new ParametricCallback() {
                        @Override
                        public void call(String status) { // success
                            Log.i(TAG, status);
                            isPosting = false;
                            Utility.showToast("ACT SUBMITTED, via HTTP POST.", HistrionMainActivity.this, Toast.LENGTH_LONG);
                            bootstrapHistorion();
                        }
                    }, new ParametricCallback() {
                        @Override
                        public void call(String status) { // error
                            Log.i(TAG, status);
                            isPosting = false;
                            Utility.showToast(String.format("DATA NOT SENT. HTTP POST ERROR: %s", status), HistrionMainActivity.this, Toast.LENGTH_LONG);
                        }
                    });
                }
                break;
            }
        }

    }

    private JSONObject includeUUID(JSONObject act) {
        // remove the persona field in the act, replace it with uuid instead...
        JSONObject persona = null;
        try {
            persona = new JSONObject(act.getString(Persona.KEYS.CACHE_PERSONA));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(persona != null) {
            String uuid = Persona.getAppUUID(persona);
            try {
                act.put(Persona.KEYS.CACHE_UUID, uuid);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return act;
    }

    private JSONObject replacePersonaWithUUID(JSONObject act) {
        // remove the persona field in the act, replace it with uuid instead...
        JSONObject persona = null;
        try {
            persona = new JSONObject(act.getString(Persona.KEYS.CACHE_PERSONA));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(persona != null) {
            String uuid = Persona.getAppUUID(persona);
            try {
                act.put(Persona.KEYS.CACHE_UUID, uuid);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        act.remove(Persona.KEYS.CACHE_PERSONA);

        return act;
    }

    private JSONObject parseAct() {

        JSONObject act = new JSONObject();
        JSONArray fields = Persona.appFields(activePersona);
        for(int f=0; f < fields.length(); f++){
            JSONObject field = null;
            try {
                field = fields.getJSONObject(f);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(!Persona.isInputField(field))
                continue;

            Object value = readFieldValue(field);
            try {
                if((value == null) && field.getBoolean(Persona.KEYS.REQUIRED)){
                    Utility.showAlert("Validation Error!",String.format("%s is required!", field.getString(Persona.KEYS.LABEL)),this);
                    return null;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                String pattern = field.getString(Persona.KEYS.PATTERN);
                if(pattern.length() > 0){
                    if(!Pattern.compile(pattern, Pattern.MULTILINE).matcher(String.valueOf(value)).find()){
                        Utility.showAlert("Validation Error!",String.format("%s is expected to match the pattern:\n\n%s\n\nPleaase correct this value.",
                                field.getString(Persona.KEYS.LABEL),
                                pattern),this);
                        return null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String cid = field.getString(Persona.KEYS.CID);
                if(act.has(cid)){
                    Utility.showAlert("Conflicting Fields", String.format("The field\n\n%s\n\nWith CID : %s, has a conflict with another field of the same CID!", field.getString(Persona.KEYS.LABEL), cid), this);
                    return null;
                }else {
                    act.put(cid, value);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // we want to include the persona, so the act can later be validated against it...
        try {
            act.put(Persona.KEYS.CACHE_PERSONA, activePersona.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }


        // to keep with the practice in the web historion, we include a current timestamp to have a means to know globally, when this act was created.
        try {
            String val  = Utility.currentTimestampUTCISO();
            act.put(Persona.KEYS.CACHE_TIMESTAMP, val );
        } catch (JSONException e) {
            e.printStackTrace();
        }


        // Make this mandatory...
        try {
            act.put(Persona.KEYS.CACHE_RID,String.format("%s-%s", getDeviceID(), act.get(Persona.KEYS.CACHE_TIMESTAMP)));
        } catch (JSONException e) {
            e.printStackTrace();
        }


        return act;
    }


    /*private Object readFieldValue(JSONObject field) {

        try {
            switch (field.getString(Persona.KEYS.FIELD_TYPE)){
                case Persona.FieldTypes.NUMBER:
                case Persona.FieldTypes.TEXT:
                case Persona.FieldTypes.PARAGRAPH:
                case Persona.FieldTypes.EMAIL:
                case Persona.FieldTypes.WEBSITE:{
                    try {
                        EditText editText = (EditText) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        String val = editText.getText().toString().trim();
                        try {
                            if (field.getBoolean(Persona.KEYS.REQUIRED)) {
                                if (val.length() == 0)
                                    return null;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        return val;
                    }catch (ClassCastException e){
                        Log.e(TAG, e.getMessage());
                    }
                    return null;
                }
                case Persona.FieldTypes.RADIO:{
                    Log.d(TAG, String.format("FIELD ID: %s", Persona.getFieldId(field)));
                    try{
                        RadioButton rb = (RadioButton) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        Log.d(TAG, String.format("Radio B ID: %s | RB Text: %s| Checked: %s", rb.getId(), rb.getText(), rb.isChecked()? "YES": "NO"));
                        if(rb.isChecked())
                            return rb.getText();
                    }catch (Exception e){}

                    try {
                        RadioGroup rg = (RadioGroup) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        for (int r = 0; r < rg.getChildCount(); r++) {
                            RadioButton btn = (RadioButton) rg.getChildAt(r);
                            if (btn.isChecked())
                                return btn.getText();
                        }
                    }catch (ClassCastException e){
                        try {
                        RadioButton btn = (RadioButton) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        if (btn.isChecked())
                            return btn.getText();
                        }catch (ClassCastException ee){
                            return null;
                        }
                    }

                    return null;
                }
                case Persona.FieldTypes.CHECK_BOXES:{
                    CheckBox[] checkBoxes = checkBoxMap.get(Persona.getFieldCId(field));
                    ArrayList<String> vals = new ArrayList<>();
                    for(CheckBox checkBox: checkBoxes){
                        if(checkBox.isChecked())
                            vals.add(checkBox.getText().toString());
                    }

                    if(field.getBoolean(Persona.KEYS.REQUIRED)){
                        if(vals.size() == 0)
                            return null;
                    }

                    return TextUtils.join(",", vals);
                }
                case  Persona.FieldTypes.DROPDOWN:{
                    Spinner spinner = (Spinner) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));

                    return spinner.getSelectedItem();
                }
                case Persona.FieldTypes.TIME:{
                    TimePicker timePicker = (TimePicker) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                    return String.format("%s:%s", timePicker.getCurrentHour(), timePicker.getCurrentMinute());
                }
                case Persona.FieldTypes.DATE:{
                    DatePicker datePicker = (DatePicker) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));;
                    return String.format("%s-%s-%s", datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
                }
                case Persona.FieldTypes.FILE:{
                    FileSelectionParams params = fileSelectionMap.get(Persona.getFieldCId(field));
                    String val = params.labelField.getText().toString(); // contains the file selection...
                    try {
                        if(field.getBoolean(Persona.KEYS.REQUIRED)){
                            if(val.length() == 0)
                                return null;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return val;
                }
                case Persona.FieldTypes.CAMERA:{
                    CameraSelectionParams params = cameraSelectionMap.get(Persona.getFieldCId(field));
                    if((params != null) && (params.imagePath != null)) {
                        String val = params.imagePath; // contains the file selection...
                        try {
                            if (field.getBoolean(Persona.KEYS.REQUIRED)) {
                                if (val.length() == 0)
                                    return null;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return val;
                    }else
                        return null;
                }
                case Persona.FieldTypes.DEVICE_ID:{
                    String deviceID = getDeviceID();
                    return deviceID;
                }
                case Persona.FieldTypes.DEVICE_GPS:{
                    String field_id = Persona.getFieldCId(field);
                    if(gpsCache.containsKey(field_id)){
                        String[] latlng = gpsCache.get(field_id);
                        return TextUtils.join(",", latlng);
                    }else{
                        return null;
                    }
                }
                case Persona.FieldTypes.HIDDEN: {
                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    return fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }*/

    private Object readFieldValue(JSONObject field) {

        try {
            switch (field.getString(Persona.KEYS.FIELD_TYPE)){
                case Persona.FieldTypes.NUMBER:
                case Persona.FieldTypes.TEXT:
                case Persona.FieldTypes.PARAGRAPH:
                case Persona.FieldTypes.EMAIL:
                case Persona.FieldTypes.WEBSITE:{
                    try {
                        EditText editText = (EditText) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        String val = editText.getText().toString().trim();
                        try {
                            if (field.getBoolean(Persona.KEYS.REQUIRED)) {
                                if (val.length() == 0)
                                    return null;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        return val;
                    }catch (ClassCastException e){
                        Log.e(TAG, e.getMessage());
                    }
                    return null;
                }
                case Persona.FieldTypes.RADIO:{
                    Log.d(TAG, String.format("FIELD ID: %s", Persona.getFieldId(field)));
                    try{
                        RadioButton rb = (RadioButton) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        Log.d(TAG, String.format("Radio B ID: %s | RB Text: %s| Checked: %s", rb.getId(), rb.getText(), rb.isChecked()? "YES": "NO"));
                        if(rb.isChecked())
                            return rb.getText();
                    }catch (Exception e){}

                    try {
                        RadioGroup rg = (RadioGroup) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));;
                        for (int r = 0; r < rg.getChildCount(); r++) {
                            RadioButton btn = (RadioButton) rg.getChildAt(r);
                            if (btn.isChecked())
                                return btn.getText();
                        }
                    }catch (ClassCastException e){
                        RadioButton btn = (RadioButton) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));;
                        if (btn.isChecked())
                            return btn.getText();
                    }

                    return null;
                }
                case Persona.FieldTypes.CHECK_BOXES:{
                    CheckBox[] checkBoxes = checkBoxMap.get(Persona.getFieldCId(field));
                    ArrayList<String> vals = new ArrayList<>();
                    for(CheckBox checkBox: checkBoxes){
                        if(checkBox.isChecked())
                            vals.add(checkBox.getText().toString());
                    }

                    if(field.getBoolean(Persona.KEYS.REQUIRED)){
                        if(vals.size() == 0)
                            return null;
                    }

                    return TextUtils.join(",", vals);
                }
                case  Persona.FieldTypes.DROPDOWN:{
                    try {
                        View vv = ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                        Spinner spinner = (Spinner) vv;

                        return spinner.getSelectedItem();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
                }
                case Persona.FieldTypes.TIME:{
                    TimePicker timePicker = (TimePicker) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                    return String.format("%s:%s", timePicker.getCurrentHour(), timePicker.getCurrentMinute());
                }
                case Persona.FieldTypes.DATE:{
                    DatePicker datePicker = (DatePicker) ViewGroupUtils.getFirstViewByTag(controllerRootView, Persona.getFieldCId(field));
                    return String.format("%s-%s-%s", datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth());
                }
                case Persona.FieldTypes.FILE:{
                    FileSelectionParams params = fileSelectionMap.get(Persona.getFieldCId(field));
                    String val = params.labelField.getText().toString(); // contains the file selection...
                    try {
                        if(field.getBoolean(Persona.KEYS.REQUIRED)){
                            if(val.length() == 0)
                                return null;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return val;
                }
                case Persona.FieldTypes.CAMERA:{
                    CameraSelectionParams params = cameraSelectionMap.get(Persona.getFieldCId(field));
                    if((params != null) && (params.imagePath != null)) {
                        String val = params.imagePath; // contains the file selection...
                        try {
                            if (field.getBoolean(Persona.KEYS.REQUIRED)) {
                                if (val.length() == 0)
                                    return null;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return val;
                    }else
                        return null;
                }
                case Persona.FieldTypes.DEVICE_ID:{
                    String deviceID = getDeviceID();
                    return deviceID;
                }
                case Persona.FieldTypes.DEVICE_GPS:{
                    String field_id = Persona.getFieldCId(field);
                    if(gpsCache.containsKey(field_id)){
                        String[] latlng = gpsCache.get(field_id);
                        return TextUtils.join(",", latlng);
                    }else{
                        return null;
                    }
                }
                case Persona.FieldTypes.HIDDEN: {
                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    return fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getDeviceID() {
        DeviceUuidFactory deviceUuidFactory = new DeviceUuidFactory(this);
        return deviceUuidFactory.getDeviceUuid().toString();
    }


    private void loadNewPersona(JSONObject persona) {
        if(persona == null)
            return;

        // get the persona to use...
        activePersona = persona;
        RelativeLayout appContainer = (RelativeLayout) findViewById(R.id.appContainer);

        int appThemeColor = Persona.getAppThemeColor(persona);
        int complimentaryColor = Utility.getContrastVersionForColor(appThemeColor);
        int contrastingColor = Utility.getContrastVersionForColor(complimentaryColor);

        PersonaTheme personaTheme = new PersonaTheme(appThemeColor, complimentaryColor, contrastingColor);

        // add some background chrome...
        Bitmap bmp = BitmapFactory.decodeResource(getResources(),
                R.drawable.curls);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(),bmp);
        bitmapDrawable.setTileModeXY(Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            appContainer.setBackground(bitmapDrawable);
        }else {
            appContainer.setBackgroundDrawable(bitmapDrawable);
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(appThemeColor));

        // get the container view in which the persona is to be rendered
        LinearLayout linearLayoutControls = (LinearLayout) findViewById(R.id.linLayoutForControls);
        // bootstrap our app from the persona
        renderPersona(this.activePersona, linearLayoutControls, personaTheme);
        Utility.showToast("Persona successfully loaded.", this);

        noPersonaLoadedYet = false;

        // cache this persona if it doesn't already exist...
        if (Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_CACHE_ACTIVE_PERSONA, true, this))
            cacheActivePersona(persona);
    }

    private void cacheActivePersona(JSONObject persona, boolean setActive) {
        String uuid = Persona.getAppUUID(persona);
        if(uuid == null) {
            // complain...
            Utility.showAlert("Survey Caching...", "Unfortunately, this persona lacks a UUID, and so it can't be cached for later reuse.", this);
            return;
        }

        // then, cache the person itself...
        JSONObject knownPersonas = null;

        if(adapter.existsDictionaryKey(Utility.DB_KEYS.PERSONA_DICTIONARY)) {
            try {
                knownPersonas = new JSONObject(adapter.fetchDictionaryEntry(Utility.DB_KEYS.PERSONA_DICTIONARY));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if(knownPersonas == null){
            knownPersonas = new JSONObject();
            try {
                knownPersonas.put(uuid, persona);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            adapter.createDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.PERSONA_DICTIONARY, knownPersonas.toString()));
        }
        else {
            try {
                knownPersonas.put(uuid, persona); // if there was already a persona with this uuid, we override it...
            } catch (JSONException e) {
                e.printStackTrace();
            }
            adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.PERSONA_DICTIONARY, knownPersonas.toString()));
        }


        if(setActive) {

            setActivePersonaUUID(uuid);
        }

    }

    private void cacheActivePersona(JSONObject persona) {
        cacheActivePersona(persona, true);
    }

    @Override
    public void onResume(){
        super.onResume();
        if(forceReload) {
            bootstrapHistorion();
            forceReload = false;
        }
    }

    private void setActivePersonaUUID(String uuid) {
        // the set the active persona...
        if(adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)){
            adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
        }else {
            adapter.createDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
        }
    }

    private void renderPersona(JSONObject persona, LinearLayout container, PersonaTheme personaTheme) {
        this.setTitle(Persona.getAppName(persona));

        container.removeAllViews();
        controllerRootView = container;

        JSONArray fields = Persona.appFields(activePersona);
        for(int f=0; f < fields.length(); f++){
            JSONObject field = null;
            try {
                field = fields.getJSONObject(f);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            renderField(field,container,personaTheme);
        }

    }

    private void renderField(final JSONObject field, LinearLayout container, PersonaTheme personaTheme) {
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 10, 0, 10); // llp.setMargins(left, top, right, bottom);

        LayoutInflater inflater = getLayoutInflater();
        LinearLayout fieldOuterContainer = (LinearLayout) inflater.inflate(R.layout.dnap_field_container, container, false);
        LinearLayout fieldInnerContainer = (LinearLayout) fieldOuterContainer.findViewById(R.id.fieldContainer);

        try {
            switch (field.getString(Persona.KEYS.FIELD_TYPE)){
                case Persona.FieldTypes.TEXT: {

                    TextView label = new TextView(this);
                    EditText editText = new EditText(this);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    // instead of setId as done up until this point, using tags seems to be the more robust approach for our dynamic views
                    editText.setTag(Persona.getFieldCId(field));

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setTypeface(null, Typeface.BOLD);
                    label.setLayoutParams(llp);

                    editText.setTextColor(personaTheme.ContrastingColor);
                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(editText);

                    try {
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if (desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case Persona.FieldTypes.NUMBER: {
                    TextView label = new TextView(this);
                    EditText editText = new EditText(this);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setTag(Persona.getFieldCId(field));
                    editText.setTextColor(personaTheme.ContrastingColor);


                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);


                    editText.setTextColor(personaTheme.ContrastingColor);
                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(editText);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.PARAGRAPH: {
                    TextView label = new TextView(this);
                    EditText editText = new EditText(this);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    editText.setTag(Persona.getFieldCId(field));


                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    editText.setTextColor(personaTheme.ContrastingColor);
                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(editText);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.EMAIL: {
                    TextView label = new TextView(this);
                    EditText editText = new EditText(this);
                    editText.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    editText.setTag(Persona.getFieldCId(field));
                    editText.setTextColor(Utility.getContrastVersionForColor(personaTheme.ContrastingColor));

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    editText.setTextColor(personaTheme.ContrastingColor);
                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(editText);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.WEBSITE:{
                    TextView label = new TextView(this);
                    EditText editText = new EditText(this);
                    editText.setTag(Persona.getFieldCId(field));
                    editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
                    editText.setTextColor(Utility.getContrastVersionForColor(personaTheme.ContrastingColor));

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    editText.setTextColor(personaTheme.ContrastingColor);
                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(editText);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.TIME:{
                    TextView label = new TextView(this);
                    TimePicker timePicker = new TimePicker(this);
                    timePicker.setTag(Persona.getFieldCId(field));

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);


                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(timePicker);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.DATE:{
                    TextView label = new TextView(this);
                    DatePicker datePicker = new DatePicker(this);
                    datePicker.setCalendarViewShown(false);
                    datePicker.setTag(Persona.getFieldCId(field));

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    container.addView(fieldOuterContainer);

                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(datePicker);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}


                    break;
                }
                case  Persona.FieldTypes.RADIO:{
                    TextView label = new TextView(this);

                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    JSONArray options = fieldOptions.getJSONArray(Persona.KEYS.OPTIONS);

                    final RadioButton[] rb = new RadioButton[options.length()];
                    RadioGroup rg = new RadioGroup(this); //create the RadioGroup
                    rg.setTag(Persona.getFieldCId(field));
                    rg.setOrientation(RadioGroup.VERTICAL);//or RadioGroup.VERTICAL

                    for(int i=0; i<options.length(); i++){
                        JSONObject fieldOption = options.getJSONObject(i);
                        rb[i]  = new RadioButton(this);
                        rg.addView(rb[i]); //the RadioButtons are added to the radioGroup instead of the layout
                        rb[i].setText(fieldOption.getString(Persona.KEYS.LABEL));
                        rb[i].setTextColor(personaTheme.ContrastingColor);
                        //rb[i].setTag(Integer.parseInt(String.format("%s%s",Persona.getFieldId(field),i)));
                        if(fieldOption.getBoolean(Persona.KEYS.CHECKED)){
                            rb[i].setChecked(true);
                        }
                    }

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);


                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(rg);

                    try{
                        // description
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.CHECK_BOXES:{
                    TextView label = new TextView(this);

                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    JSONArray options = fieldOptions.getJSONArray(Persona.KEYS.OPTIONS);

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);


                    try{
                        // description
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    final CheckBox[] checkBoxes = new CheckBox[options.length()];
                    checkBoxMap.put(Persona.getFieldCId(field),checkBoxes); // to help track which checkboxes belong to which field...

                    for(int i=0; i<options.length(); i++){
                        JSONObject fieldOption = options.getJSONObject(i);
                        checkBoxes[i]  = new CheckBox(this);
                        fieldInnerContainer.addView(checkBoxes[i]);
                        checkBoxes[i].setText(fieldOption.getString(Persona.KEYS.LABEL));
                        checkBoxes[i].setTextColor(personaTheme.ContrastingColor);
                        //checkBoxes[i].setTag(Integer.parseInt(String.format("%s%s",Persona.getFieldId(field),i)));
                        if(fieldOption.getBoolean(Persona.KEYS.CHECKED)){
                            checkBoxes[i].setChecked(true);
                        }
                    }

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.DROPDOWN:{
                    TextView label = new TextView(this);

                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    JSONArray options = fieldOptions.getJSONArray(Persona.KEYS.OPTIONS);

                    final ArrayList<String> values = new ArrayList<>();
                    Spinner spinner = new Spinner(this); //create the RadioGroup
                    spinner.setTag(Persona.getFieldCId(field));

                    Integer defaultPosition = null;
                    for(int i=0; i<options.length(); i++){
                        JSONObject fieldOption = options.getJSONObject(i);
                        values.add(fieldOption.getString(Persona.KEYS.LABEL));
                        if(fieldOption.getBoolean(Persona.KEYS.CHECKED)){
                            defaultPosition = i;
                        }
                    }

                    ArrayAdapter<String> stringArrayAdapter = new ArrayAdapter<String>(this,
                            android.R.layout.simple_spinner_item, values);
                    spinner.setAdapter(stringArrayAdapter);

                    if(defaultPosition != null)
                        spinner.setSelection(defaultPosition);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        spinner.setLayoutMode(Spinner.MODE_DROPDOWN);
                    }
                    GradientDrawable shape =  new GradientDrawable();
                    shape.setCornerRadius( 6 );
                    shape.setColor(personaTheme.ContrastingColor);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        spinner.setBackground(shape);
                    }else {
                        spinner.setBackgroundDrawable(shape);
                    }
                    spinner.setPadding(5,5,5,5);

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(spinner);


                    try {
                        // description
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if (desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.FILE:{
                    TextView label = new TextView(this);
                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    LinearLayout btnContainer = new LinearLayout(this);
                    btnContainer.setOrientation(LinearLayout.HORIZONTAL);

                    Button btnSelectFile = new Button(this);
                    btnSelectFile.setText("Set File");
                    final TextView labelSelectedFile = new TextView(this);

                    btnContainer.addView(btnSelectFile);
                    btnContainer.addView(labelSelectedFile);

                    btnSelectFile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                setFilePickerTrigger(field.getString(Persona.KEYS.LABEL), Persona.getFieldCId(field),labelSelectedFile, "*/*");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });


                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(btnContainer);

                    try {
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case  Persona.FieldTypes.CAMERA:{
                    TextView label = new TextView(this);
                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    LinearLayout btnContainer = new LinearLayout(this);
                    btnContainer.setOrientation(LinearLayout.HORIZONTAL);

                    final ImageButton btnSelectFile = new ImageButton(this);
                    btnSelectFile.setImageResource(R.drawable.ic_camera);
                    final TextView labelSelectedFile = new TextView(this);

                    btnContainer.addView(btnSelectFile);
                    btnContainer.addView(labelSelectedFile);

                    btnSelectFile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                setCameraTrigger(field.getString(Persona.KEYS.LABEL), Persona.getFieldCId(field),btnSelectFile, "*/*");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    fieldOuterContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(btnContainer);

                    try{
                        // description
                        JSONObject fieldOptions = Persona.getFieldOptions(field);
                        String desc = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                        if(desc.trim().length() > 0) {
                            TextView infoView = new TextView(this);
                            infoView.setText(desc);
                            infoView.setTextColor(personaTheme.ContrastingColor);
                            infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                            infoView.setTypeface(null, Typeface.ITALIC);
                            LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            llpIndent.setMargins(30, 5, 0, 10); // llp.setMargins(left, top, right, bottom);
                            infoView.setLayoutParams(llpIndent);
                            fieldOuterContainer.addView(infoView);
                        }
                        //end description
                    }catch (Exception e){}

                    container.addView(fieldOuterContainer);
                    break;
                }
                case Persona.FieldTypes.SHOW_IMAGE:{
                    TextView label = new TextView(this);
                    ImageView imageView = new ImageView(this);
                    imageView.setTag(Persona.getFieldCId(field));
                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setLayoutParams(llp);

                    LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);;
                    llpIndent.setMargins(30, 10, 0, 10); // llp.setMargins(left, top, right, bottom);
                    imageView.setLayoutParams(llpIndent);

                    fieldOuterContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(imageView);

                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    String uri = fieldOptions.getString(Persona.KEYS.DESCRIPTION);
                    Picasso.with(this)
                            .load(uri)
                            .placeholder(R.drawable.loading)
                            .error(R.drawable.notify)
                            .into(imageView);



                    container.addView(fieldOuterContainer);
                    break;
                }
                case Persona.FieldTypes.SHOW_INFO:{
                    TextView label = new TextView(this);
                    TextView infoView = new TextView(this);

                    label.setText(field.getString(Persona.KEYS.LABEL));
                    label.setTextColor(personaTheme.ContrastingColor);
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    label.setLayoutParams(llp);
                    label.setTypeface(null, Typeface.BOLD);

                    JSONObject fieldOptions = Persona.getFieldOptions(field);
                    infoView.setText(fieldOptions.getString(Persona.KEYS.DESCRIPTION));
                    infoView.setTextColor(personaTheme.ContrastingColor);
                    infoView.setTypeface(null, Typeface.ITALIC);

                    LinearLayout.LayoutParams llpIndent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);;
                    llpIndent.setMargins(30, 10, 0, 10); // llp.setMargins(left, top, right, bottom);
                    infoView.setLayoutParams(llpIndent);

                    fieldOuterContainer.setBackgroundColor(personaTheme.ComplimentaryColor);
                    fieldInnerContainer.setBackgroundColor(personaTheme.ThemeColor);
                    fieldInnerContainer.addView(label);
                    fieldInnerContainer.addView(infoView);

                    container.addView(fieldOuterContainer);
                    break;
                }
                case Persona.FieldTypes.DEVICE_GPS:{

                    String field_id = Persona.getFieldCId(field);
                    registerForGPS(field_id);

                    break;
                }
                /*case Persona.FieldTypes.BARCODE: {
                    //TODO: implement barcode scanning...

                }*/

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void registerForGPS(String field_name) {
        gpsSubscribers.add(field_name);
        //initGPS();
        if(checkLocationPermission()){
            initGPS();
        }else {
            Utility.showToast("After allowing the app to access location services, please try again.", this);
        }
    }

    private void initGPS() {
        try {
            trackGPSWithGPSProvider();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            trackGPSWithNetworkProvider();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void trackGPSWithGPSProvider() {
        LocationManager mnetworkLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // mlocManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
        // 60000,
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mnetworkLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                60000, 0, new LocationListener() {

                    public void onLocationChanged(Location location) {

                        longitude = String.format("%s",
                                location.getLongitude());
                        latitute = String.format("%s",
                                location.getLatitude());

                        updateGPSSubscribers();

                        Utility.showToast("GPS Location updated.", HistrionMainActivity.this);

                    }

                    public void onProviderDisabled(String provider) {

                    }

                    public void onProviderEnabled(String provider) {
                        // pass...
                    }

                    public void onStatusChanged(String provider, int status,
                                                Bundle extras) {
                        // pass...
                    }
                });
    }

    private void updateGPSSubscribers() {
        for(String subscriber: gpsSubscribers){
            gpsCache.put(subscriber, new String[]{latitute,longitude});
        }
    }

    private void trackGPSWithNetworkProvider() {
        LocationManager mnetworkLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // mlocManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
        // 60000,
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mnetworkLocManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 60000, 0,
                new LocationListener() {

                    public void onLocationChanged(Location location) {

                        longitude = String.format("%s",
                                location.getLongitude());
                        latitute = String.format("%s",
                                location.getLatitude());

                        updateGPSSubscribers();

                        Utility.showToast("GPS Location updated.", HistrionMainActivity.this);

                    }

                    public void onProviderDisabled(String provider) {

                    }

                    public void onProviderEnabled(String provider) {
                        // pass...

                    }

                    public void onStatusChanged(String provider, int status,
                                                Bundle extras) {
                        // pass...
                    }
                });
    }


    private void setCameraTrigger(String label, String fieldCID, ImageButton imageButton, String mimeType) {

        if(cameraSelectionMap.containsKey(fieldCID)){
            Utility.DeleteFile(cameraSelectionMap.get(fieldCID).imagePath);
        }

        String path = getNewImagePath(String.valueOf(fieldCID));

        Log.i(TAG, "Creating Image : "
                + path);

        CameraSelectionParams cameraSelectionParams = new CameraSelectionParams(path, fieldCID, imageButton);
        currentCameraSelectionParams = cameraSelectionParams;
        cameraSelectionMap.put(fieldCID,cameraSelectionParams);

        try {
            startCameraActivity(path);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Camera Error : " + e.getMessage());
            Utility.showAlert("Photo Capture Error", e.getMessage(),
                    HistrionMainActivity.this);
        }

    }

    private String getNewImagePath(String kind) {
        String SESSION_GUUID = java.util.UUID.randomUUID().toString();
        return String.format("%s/%s-%s.%s", dataPath, kind, SESSION_GUUID,
                "jpg");
    }

    protected void startCameraActivity(String path) {
        File file = new File(path);
        Uri outputFileUri = Uri.fromFile(file);

        Intent intent = new Intent(
                MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, INTENT_MODE.CHOOSE_CAMERA_REQUESTCODE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem v) {
        int id = v.getItemId();

        if (R.id.action_settings == id) {
            showSettings();
            forceReload = true;
            return true;
        } else if (R.id.action_manage_personas == id) {
            switchToManagePersonas();
            forceReload = true;
            return true;
        } else
            return super.onOptionsItemSelected(v);
    }

    private void switchToManagePersonas() {

        Intent _intent = new Intent(HistrionMainActivity.this, ManagePersonaActivity.class);
        if(autoInstallChannel != null){
            _intent.putExtra("CHANNEL:NAME", autoInstallChannel);
        }
        startActivity(_intent);
    }

    private void showSettings() {
        Intent _intent = new Intent(HistrionMainActivity.this, SettingsActivity.class);
        startActivity(_intent);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {


        switch (requestCode){
            case INTENT_MODE.CHOOSE_FILE_REQUESTCODE: {
                if(intent == null) {
                    Utility.showToast("Failed to perform action", this);
                    break;
                }
                String selectedPath = intent.getDataString();
                currentFileSelectionParams.labelField.setText(selectedPath);
                break;
            }
            case INTENT_MODE.CHOOSE_CAMERA_REQUESTCODE:{
                Log.d(TAG, "Returned From Photo capture...");

                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "Photo Capture OK");
                    onPhotoTaken(currentCameraSelectionParams.imagePath,
                            currentCameraSelectionParams.imageView,
                            R.drawable.ic_camera);
                } else {
                    Log.e(TAG, "Photo Capture FAILED!");
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);

    }

    protected void onPhotoTaken(String imgPath, ImageButton imageView,
                                int defaultResourceImage) {
        try {

            Log.d(TAG, "Loading image now...");

            File imgFile = new File(imgPath);
            if (imgFile.exists()) {

                Uri url = Uri.fromFile(imgFile);
                Bitmap img = handleSamplingAndRotationBitmap(this, url);
                Utility.saveBitmapToFile(img, imgPath, 25);

                Utility.setThumbnailImage(imageView,
                        imgFile);
            } else
                (imageView).setImageResource(defaultResourceImage);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Image Error : " + e.getMessage());
        }

    }


    /**
     * This method is responsible for solving the rotation issue if exist. Also scale the images to
     * 1024x1024 resolution
     *
     * @param context       The current context
     * @param selectedImage The Image URI
     * @return Bitmap image results
     * @throws IOException
     */
    public static Bitmap handleSamplingAndRotationBitmap(Context context, Uri selectedImage)
            throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = context.getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = context.getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);

        img = rotateImageIfRequired(img, selectedImage);
        return img;
    }

    /* results in a larger bitmap which isn't as useful for caching purposes.
            *
            * @param options   An options object with out* params already populated (run through a decode*
            *                  method with inJustDecodeBounds==true
            * @param reqWidth  The requested width of the resulting bitmap
            * @param reqHeight The requested height of the resulting bitmap
            * @return The value to be used for inSampleSize
            */
    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    /**
     * Rotate an image if required.
     *
     * @param img           The image bitmap
     * @param selectedImage Image URI
     * @return The resulted Bitmap after manipulation
     */
    private static Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {

        ExifInterface ei = new ExifInterface(selectedImage.getPath());
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private void setFilePickerTrigger(String label, String fieldCID, TextView labelSelectedFile, String mimeType) {


        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // special intent for Samsung file manager
        Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA");
        // if you want any file type, you can skip next line
        sIntent.putExtra("CONTENT_TYPE", mimeType);
        sIntent.addCategory(Intent.CATEGORY_DEFAULT);

        Intent chooserIntent;
        if (getPackageManager().resolveActivity(sIntent, 0) != null) {
            // it is device with samsung file manager
            chooserIntent = Intent.createChooser(sIntent, label);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{intent});
        } else {
            chooserIntent = Intent.createChooser(intent,label);
        }

        try {
            startActivityForResult(chooserIntent, INTENT_MODE.CHOOSE_FILE_REQUESTCODE);

            FileSelectionParams fileSelectionParams = new FileSelectionParams(fieldCID,labelSelectedFile);
            currentFileSelectionParams = fileSelectionParams;
            fileSelectionMap.put(fieldCID,fileSelectionParams);

        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "No suitable File Manager was found.", Toast.LENGTH_SHORT).show();
        }

    }

    private JSONObject getActivePersona() {

        String appJSON = null;

        if(adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)){
            String uuid = adapter.fetchDictionaryEntry(Utility.DB_KEYS.ACTIVE_PERSONA_UUID);
            if(adapter.existsDictionaryKey(Utility.DB_KEYS.PERSONA_DICTIONARY)){
                try {
                    JSONObject knownPersonas =  new JSONObject(adapter.fetchDictionaryEntry(Utility.DB_KEYS.PERSONA_DICTIONARY));
                    appJSON = knownPersonas.getString(uuid);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        if(appJSON != null)
        {
            JSONObject persona = parsePersona(appJSON);
            return  persona;
        }else {
            Utility.showToast("There is currently no cached, active persona to initialize with.", this);
        }

        return  null;
    }

    private JSONObject parsePersona(String appJSON) {
        try {
            return new JSONObject(appJSON);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class FileSelectionParams {
        private final String fieldCID;
        private final TextView labelField;

        public FileSelectionParams(String fieldCID, TextView labelSelectedFile) {
            this.fieldCID = fieldCID;
            this.labelField = labelSelectedFile;
        }
    }

    private class CameraSelectionParams {
        private final String fieldCID;
        private final String imagePath;
        private final ImageButton imageView;

        public CameraSelectionParams(String imagePath, String fieldCID, ImageButton imageButton) {
            this.imagePath = imagePath;
            this.fieldCID = fieldCID;
            this.imageView = imageButton;
        }
    }
}
