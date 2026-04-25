package com.keytron.clamguard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class QuarantineManager {
    public static class QuarantineItem {
        public String id;
        public String originalPath;
        public String quarantinePath;
        public String threatName;
        public long timestamp;
    }

    private static File getDbFile(Context context) {
        return new File(context.getFilesDir(), "quarantine_db.json");
    }

    public static List<QuarantineItem> getItems(Context context) {
        List<QuarantineItem> list = new ArrayList<QuarantineItem>();
        File file = getDbFile(context);
        if (!file.exists()) return list;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                QuarantineItem item = new QuarantineItem();
                item.id = obj.getString("id");
                item.originalPath = obj.getString("originalPath");
                item.quarantinePath = obj.getString("quarantinePath");
                item.threatName = obj.optString("threatName", "Unknown");
                item.timestamp = obj.optLong("timestamp", 0);
                list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveItems(Context context, List<QuarantineItem> items) {
        try {
            JSONArray array = new JSONArray();
            for (QuarantineItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("originalPath", item.originalPath);
                obj.put("quarantinePath", item.quarantinePath);
                obj.put("threatName", item.threatName);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(getDbFile(context)));
            writer.write(array.toString(2));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addItem(Context context, String originalPath, String quarantinePath, String threatName) {
        List<QuarantineItem> items = getItems(context);
        QuarantineItem item = new QuarantineItem();
        item.id = String.valueOf(System.currentTimeMillis()) + "_" + (int)(Math.random() * 1000);
        item.originalPath = originalPath;
        item.quarantinePath = quarantinePath;
        item.threatName = threatName;
        item.timestamp = System.currentTimeMillis();
        items.add(item);
        saveItems(context, items);
    }

    public static void removeItem(Context context, String id) {
        List<QuarantineItem> items = getItems(context);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.remove(i);
                break;
            }
        }
        saveItems(context, items);
    }
}