package com.nuchwezi.dnaphistrion;

import android.content.Context;
import android.graphics.Color;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by NemesisFixx on 20-Aug-16.
 */
public class Persona {
    public static final String META_FIELD_NAME = "*";
    private static final String DEFAULT_THEATRE_BASE_URL = "https://chwezi.tech/";
    public static final String PERSONA_BASEDIR = "DNAP_PERSONAS";

    public static boolean hasRestrictedAccess(JSONObject persona) {
        try {
            return persona.getString(KEYS.ACCESS).equalsIgnoreCase("LIMITED");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean hasSTICKY_ACTS_ON(JSONObject persona, Context context){
        return hasSTICKY_ACTS_ON(Persona.getAppUUID(persona), context);
    }

    public static boolean hasSTICKY_ACTS_ON(String personaUUID, Context context) {
        DBAdapter dbAdapter = new DBAdapter(context);
        dbAdapter.open();

        Gson gson = new Gson();

        if(dbAdapter.existsDictionaryKey(Persona.KEYS.STICKY_ACTS_ACTIVE_LIST)) {

            String stickyONSetJSON = dbAdapter.fetchDictionaryEntry(Persona.KEYS.STICKY_ACTS_ACTIVE_LIST);

            Type founderSetType = new TypeToken<HashSet<String>>() {
            }.getType();

            HashSet<String> stickyONSet = gson.fromJson(stickyONSetJSON, founderSetType);

            return
                    stickyONSet.contains(personaUUID);
        }

        return false;
    }

    public static JSONArray appFields(JSONObject persona) {
        try {
            JSONArray fields = persona.getJSONArray(KEYS.FIELDS);
            return fields;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getFieldId(JSONObject field) {
        String cid = null;
        try {
            cid = field.getString(KEYS.CID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return Integer.parseInt(cid.replace("c",""));
    }

    public static String getFieldCId(JSONObject field) {
        try {
            return String.format("%s-%s", field.getString(KEYS.CID), field.getString(KEYS.FIELD_TYPE));
        } catch (JSONException e) {
            return null;
        }
    }
    public static String getLeanFieldCId(JSONObject field) {
        try {
            return field.getString(KEYS.CID);
        } catch (JSONException e) {
            return null;
        }
    }

    public static String getAppName(JSONObject persona) {
        try {
            return persona.getJSONObject(KEYS.APP).getString(KEYS.NAME);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getAppUUID(JSONObject persona) {
        try {
            return persona.getJSONObject(KEYS.APP).getString(KEYS.UUID);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getAppDescription(JSONObject persona) {
        try {
            return persona.getJSONObject(KEYS.APP).getString(KEYS.DESCRIPTION);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getAppTransportMode(JSONObject persona) {
        try {
            return persona.getJSONObject(KEYS.APP).getString(KEYS.TRANSPORT_MODE);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getAppTheatreAddress(JSONObject persona) {
        try {
            String theatreURL =  persona.getJSONObject(KEYS.APP).getString(KEYS.THEATRE_ADDRESS);
            String modifiedTheatreAddress = theatreURL.replace("theatre.nuchwezi.com","chwezi.tech");
            return modifiedTheatreAddress;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getAppBrandURI(JSONObject persona) {
        try {
            return persona.getJSONObject(KEYS.APP).getString(KEYS.BRAND_IMAGE);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static int getAppThemeColor(JSONObject persona) {
        String parsedColor = null;
        try {
            parsedColor = persona.getJSONObject(KEYS.APP).getString(KEYS.COLOR);
        } catch (JSONException e) {

        }
        if(parsedColor!= null) {
            if (parsedColor.length() > 0) {
                try {
                    int colorV = Color.parseColor(parsedColor);
                    return  colorV;
                }catch (IllegalArgumentException e){
                    try {
                        int colorV = Color.parseColor(String.format("#%s", parsedColor)); // in case # is missing...
                        return colorV;
                    }catch (Exception ex){
                        return R.color.colorAccent;
                    }
                }
            }
        }

        return Color.WHITE;
    }

    public static JSONObject getFieldOptions(JSONObject field) {
        try {
            return field.getJSONObject(KEYS.FIELD_OPTIONS);
        } catch (JSONException e) {
            return null;
        }
    }

    public static boolean canTransportActs(JSONObject persona) {
        String theatre_address = getAppTheatreAddress(persona);
        String transport_mode = getAppTransportMode(persona);

        if(theatre_address == null) {
            return false;
        }

        if(theatre_address.trim().length() == 0)
            return false;

        switch (transport_mode){
            case Persona.TRANSPORT_MODES.POST:
            case Persona.TRANSPORT_MODES.GET:
            case Persona.TRANSPORT_MODES.SMS:
            case Persona.TRANSPORT_MODES.EMAIL:
                return true;
            default:
                return false;
        }
    }

    public static boolean isInputField(JSONObject field) {
        try {
            switch (field.getString(KEYS.FIELD_TYPE)) {
                case FieldTypes.TEXT:
                case FieldTypes.PARAGRAPH:
                case FieldTypes.NUMBER:
                case FieldTypes.EMAIL:
                case FieldTypes.WEBSITE:
                case FieldTypes.RADIO:
                case FieldTypes.FILE:
                case FieldTypes.DROPDOWN:
                case FieldTypes.TIME:
                case FieldTypes.DATE:
                case FieldTypes.CAMERA:
                case FieldTypes.DEVICE_ID:
                case FieldTypes.DEVICE_GPS:
                case FieldTypes.HIDDEN: {
                    // TODO: for now, we shall use this field type to intercept some
                    // meta functions such as forcing sticky-acts on a persona
                    // in that case, we should skip these when parsing acts
                    String fieldName = field.getString(Persona.KEYS.LABEL);
                    if(fieldName.equalsIgnoreCase(Persona.META_FIELD_NAME)) {
                        return false;
                    }
                }
                case FieldTypes.BARCODE:
                case FieldTypes.CHECK_BOXES:{
                    return true;
                }

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static JSONObject annotateAct(JSONObject act, JSONObject persona) {
        JSONObject annotatedAct = new JSONObject();

        for(Iterator<String> iter = act.keys(); iter.hasNext();) {
            String key = iter.next();
            Object value = null;
            try {
                value = act.get(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // check if the persona has a field with this name, then replace the key in the act with the field label...
            JSONObject field = getFieldWithCID(key, persona);
            if(field == null) {
                try {
                    annotatedAct.put(key, value);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                continue;
            }

            String label = Persona.getFieldLabel(field);

            if(label != null){

                try {
                    annotatedAct.put(label, value);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return annotatedAct;
    }

    private static String getFieldLabel(JSONObject field) {
        try {
            return field.getString(KEYS.LABEL);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static JSONObject getFieldWithCID(String cid, JSONObject persona) {
        JSONArray fields = Persona.appFields(persona);
        for(int f=0; f < fields.length(); f++) {
            JSONObject field = null;
            try {
                field = fields.getJSONObject(f);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(field != null)
                try {
                    if(field.getString(KEYS.CID).equalsIgnoreCase(cid))
                        return field;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
        }

        return null;
    }

    public static String getNewPersonaFilePath(JSONObject persona) {
        String SESSION_GUUID = java.util.UUID.randomUUID().toString();
        return String.format("%s-%s.%s", Persona.getAppName(persona), SESSION_GUUID,
                "persona");
    }

    public static String makeFieldID(String intID) {
        return String.format("c%s", intID);
    }

    public static String makeBatchSubmissionAddress(String theatre_address) {
        String theatreURL =  theatre_address;
        String modifiedTheatreAddress = theatreURL.replace("theatre.nuchwezi.com","chwezi.tech");
        return String.format("%s/batch/", modifiedTheatreAddress);
    }

    public static String getDivinerURL(JSONObject persona, String access_key) {
        String theatreAddress = getAppTheatreAddress(persona);
        try
        {
            URL url = new URL(theatreAddress);
            String baseUrl = url.getProtocol() + "://" + conditionalRewcriteHOST(url.getHost());
            String divinerURL = String.format("%s/persona/%s/diviner/mini/?show_title=0&compact=1", baseUrl, getAppUUID(persona));
            if(access_key != null){
                divinerURL += String.format("&key=%s", access_key);
            }
            return divinerURL;
        }
        catch (MalformedURLException e)
        {
        }

        return null;
    }

    private static String conditionalRewcriteHOST(String host) {
        if(host.equalsIgnoreCase("theatre.nuchwezi.com"))
            return "chwezi.tech";
        else
            return host;
    }

    public static String spaceURL(JSONObject persona) {
        String theatreAddress = getAppTheatreAddress(persona);
        try
        {
            URL url = new URL(theatreAddress);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String spaceURL = String.format("%s/channel/%s/space/", baseUrl, getAppUUID(persona));
            return spaceURL;
        }
        catch (MalformedURLException e)
        {
        }

        return null;
    }

    public static String getPersonaURL(JSONObject persona) {
        String theatreAddress = getAppTheatreAddress(persona);
        try
        {
            URL url = theatreAddress.trim().length() == 0 ? new URL(Persona.DEFAULT_THEATRE_BASE_URL) : new URL(theatreAddress);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String personaURL = String.format("%s/api/persona/%s/", baseUrl, getAppUUID(persona));
            return personaURL;
        }
        catch (MalformedURLException e)
        {
        }

        return null;
    }

    public static String getBriQURL(JSONObject persona, String access_key) {
        String theatreAddress = getAppTheatreAddress(persona);
        try
        {
            URL url = new URL(theatreAddress);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String divinerURL = String.format("%s/persona/%s/briq/?", baseUrl, getAppUUID(persona));
            if(access_key != null){
                divinerURL += String.format("&key=%s", access_key);
            }
            return divinerURL;
        }
        catch (MalformedURLException e)
        {
        }

        return null;
    }

    public static String getGeomatURL(JSONObject persona, String access_key) {
        String theatreAddress = getAppTheatreAddress(persona);
        try
        {
            URL url = new URL(theatreAddress);
            String baseUrl = url.getProtocol() + "://" + url.getHost();
            String divinerURL = String.format("%s/persona/%s/geomat/?", baseUrl, getAppUUID(persona));
            if(access_key != null){
                divinerURL += String.format("&key=%s", access_key);
            }
            return divinerURL;
        }
        catch (MalformedURLException e)
        {
        }

        return null;
    }

    public static String getAppDescription(JSONObject persona, boolean showVersion) {
        try {
            if (showVersion) {
                String appVersion = getAppVersion(persona);

                if (appVersion != null)
                    return String.format("%s\n\n--- VERSION: %s", getAppDescription(persona), getAppVersion(persona));
            }
            return getAppDescription(persona);
        }catch (Exception e){
            return "";
        }
    }

    private static boolean hasAppVersion(JSONObject persona) {
        JSONArray fields = Persona.appFields(persona);
        for (int f = 0; f < fields.length(); f++) {

            JSONObject field = null;
            try {
                field = fields.getJSONObject(f);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String fieldType = null;

            try {
                fieldType = field.getString(KEYS.FIELD_TYPE);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(fieldType.equalsIgnoreCase(FieldTypes.HIDDEN)) {
                if(Persona.getFieldLabel(field).equalsIgnoreCase("version")){
                    return true;
                }
            }

        }

        return false;
    }

    private static String getAppVersion(JSONObject persona) {
        JSONArray fields = Persona.appFields(persona);
        for (int f = 0; f < fields.length(); f++) {

            JSONObject field = null;
            try {
                field = fields.getJSONObject(f);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            String fieldType = null;

            try {
                fieldType = field.getString(KEYS.FIELD_TYPE);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if(fieldType.equalsIgnoreCase(FieldTypes.HIDDEN)) {
                if(Persona.getFieldLabel(field).equalsIgnoreCase("version")){
                    return Persona.getfieldDescription(field);
                }
            }

        }

        return null;
    }

    private static String getfieldDescription(JSONObject field) {
        try {
            return Persona.getFieldOptions(field).getString(KEYS.DESCRIPTION);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isPersonaURL(String url) {
        String personaURLPrefix = String.format("%sapi/persona/",DEFAULT_THEATRE_BASE_URL);
        if (url.startsWith(personaURLPrefix)) {
            String referenceString = url.substring(personaURLPrefix.length(),url.length());
            boolean isPersonaPath = referenceString.matches("[a-zA-Z0-9\\-]*\\/?\\s?$");
            return isPersonaPath;
        }
        else
            return false;
    }

    public static boolean fieldHasMeta(JSONObject field) {
        try {
            return field.has(KEYS.FIELD_META) && (field.getString(KEYS.FIELD_META).trim().length() > 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static String getFieldMeta(JSONObject field) {
        try {
            return field.getString(KEYS.FIELD_META);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String makeFieldDataCacheKEY(String appUUID) {
        return String.format("PERSONA_FIELD_DYNAMIC_DATA_CACHE:%s", appUUID);
    }

    public static String parsePersonaUUIDFromURL(String personaURL) {
        try {
            Pattern pattern = Pattern.compile("api\\/persona\\/([^\\/]+)\\/");
            Matcher matcher = pattern.matcher(personaURL);
            String val = "";
            if (matcher.find()) {
                val = matcher.group(1);  // get the uuid
            }
            return val;
        }catch (Exception e){
            return null;
        }
    }

    public static String getFieldName(JSONObject persona, String fieldCID) {
        try {
            JSONArray fields = persona.getJSONArray(KEYS.FIELDS);
            for(int f = 0; f < fields.length(); f++) {
                JSONObject field = fields.getJSONObject(f);
                if (Persona.getFieldCId(field).equalsIgnoreCase(fieldCID))
                    return Persona.getFieldLabel(field);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return fieldCID; // fallback to CID if we didn't find a match.
    }


    public class KEYS {
        public static final String APP = "app";
        public static final String THEATRE_ADDRESS = "theatre_address";
        public static final String TRANSPORT_MODE = "transport_mode";
        public static final String FIELDS = "fields";
        public static final String REQUIRED = "required";
        public static final String LABEL = "label";
        public static final String CID = "cid";
        public static final String FIELD_TYPE = "field_type";
        public static final String COLOR = "color";
        public static final String NAME = "name";
        public static final String OPTIONS = "options";
        public static final String FIELD_OPTIONS = "field_options";
        public static final String DESCRIPTION = "description";
        public static final String UUID = "uuid";
        public static final String BRAND_IMAGE = "brand_image";
        public static final String CACHE_THEATRE_ADDRESS = "CACHE_THEATRE_ADDRESS";
        public static final String CACHE_TRANSPORT_MODE = "CACHE_TRANSPORT_MODE";
        public static final String CACHE_APP_NAME = "CACHE_APP_NAME";
        public static final String CACHE_PERSONA = "PERSONA"; /* please, for historical reasons, keep this key as is, as the official Theatre implementation relies on it */
        public static final String CACHE_UUID = "UUID"; /* please, for historical reasons, keep this key as is, as the official Theatre implementation relies on it */
        public static final String CHECKED = "checked";
        public static final String CACHE_CAMERA = "CACHE_CAMERA";
        public static final String CACHE_FILES = "CACHE_FILES";
        public static final String CACHE_TIMESTAMP = "CACHE_TIMESTAMP";
        public static final String ACCESS = "access";
        public static final String PATTERN = "pattern";

        public static final String STICKY_ACTS_ACTIVE_LIST = "STICKY_ACTS_ACTIVE_LIST";

        public static final String FIELD_META = "meta";
        public static final String CACHE_ORIGIN_STORE_ID = "CACHE_ORIGIN_STORE_ID";
        public static final String CLONE_ACT = "CLONE_ACT";
    }

    public class FieldTypes {
        public static final String TEXT = "text";
        public static final String PARAGRAPH = "paragraph";
        public static final String EMAIL = "email";
        public static final String WEBSITE = "website";
        public static final String FILE = "file";
        public static final String RADIO = "radio";
        public static final String SHOW_IMAGE = "show_image";
        public static final String SHOW_INFO = "show_info";
        public static final String TIME = "time";
        public static final String DROPDOWN = "dropdown";
        public static final String CAMERA = "camera";
        public static final String CHECK_BOXES = "checkboxes";
        public static final String DATE = "date";
        public static final String NUMBER = "number";
        public static final String DEVICE_ID = "deviceid";
        public static final String HIDDEN = "hidden";
        public static final String DEVICE_GPS = "devicegps";
        public static final String BARCODE = "barcode";
        public static final String TRIGGER = "trigger";
        public static final String SHOW_VIDEO = "show_video";
        public static final String PLAY_AUDIO = "play_audio";
        public static final String SHOW_URL = "show_url";
        public static final String SHOW_WEBSITE = "show_website";
    }

    public class TRANSPORT_MODES {
        public static final String POST = "POST";
        public static final String GET = "GET";
        public static final String SMS = "SMS";
        public static final String EMAIL = "EMAIL";
    }

    public class PERSONA_REFERENCES {
        public static final String KEY_PERSONA_UUID__FEEDBACK_PERSONA = "bb765c31-6959-49d0-b192-6c83bdab5cb4";
        public static final String KEY_PERSONA_URL__FEEDBACK_PERSONA = "https://chwezi.tech/api/persona/bb765c31-6959-49d0-b192-6c83bdab5cb4/";
    }
}