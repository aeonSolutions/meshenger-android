package d.d.meshenger;

import android.util.JsonReader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


class Database {
    private static final String TAG = "Database";
    static String version = "3.1.0"; // current version
    private Settings settings;
    private Contacts contacts;
    private Events events;

    Database() {
        this.contacts = new Contacts();
        this.settings = new Settings();
        this.events = new Events();
    }

    public Settings getSettings() {
        return settings;
    }

    public Contacts getContacts() {
        return contacts;
    }

    public Events getEvents() {
        return events;
    }

    public void onDestroy() {
        // zero keys from memory
        if (settings.getSecretKey() != null) {
            Arrays.fill(settings.getSecretKey(), (byte) 0);
        }

        if (settings.getPublicKey() != null) {
            Arrays.fill(settings.getPublicKey(), (byte) 0);
        }

        for (Contact contact : contacts.getContactList()) {
            if (contact.getPublicKey() != null) {
                Arrays.fill(contact.getPublicKey(), (byte) 0);
            }
        }
    }

    public static Database load(String path, String password) throws IOException, JSONException {
        // read database file
        byte[] data = Utils.readExternalFile(path);

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.decryptDatabase(data, password.getBytes());

            if (data == null) {
                throw new IOException("Wrong database password.");
            }
        }

        JSONObject obj = new JSONObject(
            new String(data, Charset.forName("UTF-8"))
        );

        boolean upgraded = upgradeDatabase(obj.getString("version"), Database.version, obj);
        Database db = Database.fromJSON(obj);

        if (upgraded) {
            Log.d(TAG, "Store updated database.");
            Database.store(path, db, password);
        }

        return db;
    }

    public static void store(String path, Database db, String password) throws IOException, JSONException {
        JSONObject obj = Database.toJSON(db);
        byte[] data = obj.toString().getBytes();

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.encryptDatabase(data, password.getBytes());
        }

        // write database file
        Utils.writeExternalFile(path, data);
    }

    private static void alignSettings(JSONObject settings) throws JSONException {
        JSONObject defaults = Settings.exportJSON(new Settings());

        // default keys
        Iterator<String> defaults_iter = defaults.keys();
        List<String> defaults_keys = new ArrayList<>();
        while (defaults_iter.hasNext()) {
            defaults_keys.add(defaults_iter.next());
        }

        // current keys
        Iterator<String> settings_iter = settings.keys();
        List<String> settings_keys = new ArrayList<>();
        while (settings_iter.hasNext()) {
            settings_keys.add(settings_iter.next());
        }

        // add missing keys
        for (String key : defaults_keys) {
            if (!settings.has(key)) {
                settings.put(key, defaults.get(key));
            }
        }

        // remove extra keys
        for (String key : settings_keys) {
            if (!defaults.has(key)) {
                settings.remove(key);
            }
        }
    }

    private static boolean upgradeDatabase(String from, String to, JSONObject db) throws JSONException {
        if (from.equals(to)) {
            return false;
        }

        Log.d(TAG, "Upgrade database from " + from + " to " + to);

        JSONObject settings = db.getJSONObject("settings");

        // 2.0.0 => 2.1.0
        if (from.equals("2.0.0")) {
            // add blocked field (added in 2.1.0)
            JSONArray contacts = db.getJSONArray("contacts");
            for (int i = 0; i < contacts.length(); i += 1) {
                contacts.getJSONObject(i).put("blocked", false);
            }
            from = "2.1.0";
        }

        // 2.1.0 => 3.0.0
        if (from.equals("2.1.0")) {
            // add new fields
            settings.put("ice_servers", new JSONArray());
            settings.put("development_mode", false);
            from = "3.0.0";
        }

        // 3.0.0 => 3.0.1
        if (from.equals("3.0.0")) {
            // nothing to do
            from = "3.0.1";
        }

        // 3.0.1 => 3.0.2
        if (from.equals("3.0.1")) {
            // fix typo in setting name
            settings.put("night_mode", settings.getBoolean("might_mode"));
            from = "3.0.2";
        }

        // 3.0.2 => 3.0.3
        if (from.equals("3.0.2")) {
            // nothing to do
            from = "3.0.3";
        }

        // 3.0.3 => 3.1.0
        if (from.equals("3.0.3")) {
            from = "3.1.0";
        }

        // add missing keys with defaults and remove unexpected keys
        alignSettings(settings);

        db.put("version", from);

        return true;
    }

    public static JSONObject toJSON(Database db) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", db.version);
        obj.put("settings", Settings.exportJSON(db.settings));

        JSONArray contacts = new JSONArray();
        for (Contact contact : db.getContacts().getContactList()) {
            contacts.put(Contact.exportJSON(contact, true));
        }
        obj.put("contacts", contacts);

        return obj;
    }

    public static Database fromJSON(JSONObject obj) throws JSONException {
        Database db = new Database();

        // import version
        db.version = obj.getString("version");

        // import contacts
        JSONArray array = obj.getJSONArray("contacts");
        for (int i = 0; i < array.length(); i += 1) {
            db.getContacts().addContact(
                Contact.importJSON(array.getJSONObject(i), true)
            );
        }

        // import settings
        JSONObject settings = obj.getJSONObject("settings");
        db.settings = Settings.importJSON(settings);

        return db;
    }
}
