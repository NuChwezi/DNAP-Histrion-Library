package com.nuchwezi.dnaphistrion;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import androidx.appcompat.app.AppCompatActivity;

import static com.nuchwezi.dnaphistrion.DBHelper.Tag;


public class ManagePersonaActivity extends AppCompatActivity {

    private DBAdapter adapter;
    private PersonaAdapter personaAdapter;
    JSONObject knownPersonas;
    private String autoInstallChannel; // default DNAP Channel

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dnap_activity_manage_persona_cache);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        adapter = new DBAdapter(this);
        adapter.open();

        Intent intent = getIntent();

        if(intent.hasExtra(HistrionMainActivity.PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL)){
            try {
                autoInstallChannel = intent.getStringExtra(HistrionMainActivity.PERSONA_REFERENCES.AUTO_INSTALL_CHANNEL);
                Utility.setSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_AUTO_INSTALL_CHANNEL, autoInstallChannel, this);
            }catch (Exception e){
                Utility.showAlert("Invalid Channel", "The Channel you tried to subscribe to seems be invalid! Contact app developers to rectify this.", this);
            }
        }

        loadCachedPersonas();
    }



    private void loadCachedPersonas() {

        if(adapter.existsDictionaryKey(Utility.DB_KEYS.PERSONA_DICTIONARY)) {
            // then, cache the person itself...
            knownPersonas = null;

            if(adapter.existsDictionaryKey(Utility.DB_KEYS.PERSONA_DICTIONARY)) {
                try {
                    knownPersonas = new JSONObject(adapter.fetchDictionaryEntry(Utility.DB_KEYS.PERSONA_DICTIONARY));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            if(knownPersonas != null){
                showPersonas(knownPersonas);
            }
        }
    }

    private void showPersonas(JSONObject knownPersonas) {
        Iterator<String> iter = knownPersonas.keys();
        ArrayList<JSONObject> personaList = new ArrayList<>();
        while (iter.hasNext()) {
            String key = iter.next();
            try {
                personaList.add(knownPersonas.getJSONObject(key));
            } catch (JSONException e) {
                // Something went wrong!
            }
        }

        showPersonas(personaList);
    }

    private void showPersonas(ArrayList<JSONObject> personaList) {
        personaAdapter = new PersonaAdapter(this, personaList,
                new PersonaUUIDRunnable(){
                    @Override
                    public void run(String uuid) {
                        setActivePersona(uuid);
                    } // set active persona handler

        }, new PersonaUUIDRunnable(){
            @Override
            public void run(String uuid) {
                deletePersona(uuid);
            } // delete persona handler
        });
        ExpandableListView personasList = (ExpandableListView) findViewById(R.id.personaListContainer);

        personasList.setAdapter(personaAdapter);
    }

    private void deletePersona(String uuid){
        if(uuid == null)
            return;

        if (!Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_ALLOW_DELETING_ACTIVE_PERSONA, false, this)) {
            // first, ensure this is not the active persona uuid... we shouldn't delete the active one.
            if (adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)) {
                String activeUUID = adapter.fetchDictionaryEntry(Utility.DB_KEYS.ACTIVE_PERSONA_UUID);
                if (activeUUID.equalsIgnoreCase(uuid)) {
                    Utility.showAlert("WARNING", "You have tried to delete the currently active persona. This is not allowed... Set the default to something else, then try again"
                          , this);
                    return;
                }
            }
        }

        if(knownPersonas != null){
            knownPersonas.remove(uuid);
            if(adapter.existsDictionaryKey(Utility.DB_KEYS.PERSONA_DICTIONARY)) {
                adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.PERSONA_DICTIONARY, knownPersonas.toString()));
                loadCachedPersonas();
                Utility.showToast("That persona has been deleted from the cache.", this);
            }

            if (adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)) {
                String activeUUID = adapter.fetchDictionaryEntry(Utility.DB_KEYS.ACTIVE_PERSONA_UUID);
                if (activeUUID.equalsIgnoreCase(uuid)) {
                   adapter.deleteDictionaryEntry(Utility.DB_KEYS.ACTIVE_PERSONA_UUID);
                    Utility.showToast("The active persona has likewise been unset...", this);
                }
            }
        }
    }

    private void setActivePersona(String uuid){
        if(uuid == null)
            return;

        if(adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)) {
            adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
            switchToActivePersona(); // after activating a persona, we should switch to it instead of reloading persona list
        }else {
            adapter.createDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
            switchToActivePersona(); // after activating a persona, we should switch to it instead of reloading persona list
        }

        try {
            Utility.showToast(String.format("%s has been set as the default persona", Persona.getAppName(knownPersonas.getJSONObject(uuid))), this);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void switchToActivePersona() {
        Intent _intent = new Intent(ManagePersonaActivity.this, HistrionMainActivity.class);
        startActivity(_intent);
    }

    public abstract class PersonaUUIDRunnable {

        public abstract void run(String uuid);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_manage_persona, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem v) {

        int id = v.getItemId();

        if (R.id.action_settings == id) {
            showSettings();
            return true;
        } else if (R.id.action_fetch_personas == id) {
            checkSubscriptionChannels();
            return true;
        } else
            return super.onOptionsItemSelected(v);
    }


    private void checkSubscriptionChannels() {
        String autoInstallChannelSpec = Utility.getSetting(Utility.PREFERENCES.PREF_KEY_SETTINGS_AUTO_INSTALL_CHANNEL, null, this);
        if((autoInstallChannelSpec == null))
            return;

        HashMap<String,Boolean> channelAuto = new HashMap<>();


        if(autoInstallChannelSpec != null) {
            ArrayList<String> channels = parseSubscriptionChannels(autoInstallChannelSpec);
            for(String chan: channels)
                channelAuto.put(chan,false);
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

        Utility.showToast(String.format("Wait as we check for any personas under the %s channel", channelQuery), this, Toast.LENGTH_LONG);

        Uri url = Uri.parse(channelQuery);
        Utility.getHTTP(this, url.toString(), new ParametricCallback() {
            @Override
            public void call(String personaListJSON) { // success
                try {
                    JSONArray personaList = new JSONArray(personaListJSON);
                    boolean canSetActivePersona = true;
                    for(int i = 0; i < personaList.length(); i++){
                        JSONObject persona = personaList.getJSONObject(i);
                        Log.d(Tag, String.format("Persona: %s", Persona.getAppName(persona)));
                        boolean setActivePersona = canSetActivePersona? autoInstall : false;
                        Utility.showToast(String.format("A new persona (%s) has been cached",  Persona.getAppName(persona)), ManagePersonaActivity.this);
                        cacheActivePersona(persona,setActivePersona);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                loadCachedPersonas();
            }
        }, new ParametricCallback() {
            @Override
            public void call(String status) { // error
                Utility.showToast("Failed to fetch persona list from the specified Channel Subscription Repository!", ManagePersonaActivity.this, Toast.LENGTH_LONG);
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


    private void showSettings() {
        Intent _intent = new Intent(ManagePersonaActivity.this, SettingsActivity.class);
        startActivity(_intent);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {


        switch (requestCode){

        }

        super.onActivityResult(requestCode, resultCode, intent);

    }

    private void loadNewPersonaFromPath(String pathToPersona) {
        if(pathToPersona == null){
            Utility.showAlert("Persona File Error","Sorry, but loading the persona from file has failed! Ensure the file can be read, and is legitimate!", R.drawable.notify,this);
            return;
        }

        String personaJSON = null;
        personaJSON = Utility.readFileToString(pathToPersona);

        if(personaJSON == null){
            Utility.showAlert("Persona File Error","Sorry, but loading the persona from file has failed! Ensure the file can be read, and is legitimate!", R.drawable.notify,this);
            return;
        }

        cacheActivePersona(parsePersona(personaJSON));
    }

    private JSONObject parsePersona(String appJSON) {
        try {
            return new JSONObject(appJSON);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
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
        String uuid = Persona.getAppUUID(persona);
        if(uuid == null) {
            // complain...
            Utility.showAlert("Persona Caching...", "Unfortunately, this persona lacks a UUID, and so it can't be cached for later reuse.", this);
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

        setActivePersonaUUID(uuid);
        loadCachedPersonas();
    }

    private void setActivePersonaUUID(String uuid) {
        // the set the active persona...
        if(adapter.existsDictionaryKey(Utility.DB_KEYS.ACTIVE_PERSONA_UUID)){
            adapter.updateDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
        }else {
            adapter.createDictionaryEntry(new DBAdapter.DictionaryKeyValue(Utility.DB_KEYS.ACTIVE_PERSONA_UUID, uuid));
        }
    }


}
