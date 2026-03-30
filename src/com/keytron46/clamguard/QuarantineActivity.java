package com.keytron46.clamguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuarantineActivity extends Activity {
    private ListView listView;
    private TextView emptyView;
    private QuarantineAdapter adapter;
    private List<QuarantineManager.QuarantineItem> items;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiConfig.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiConfig.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quarantine);

        listView = (ListView) findViewById(R.id.quarantine_list);
        emptyView = (TextView) findViewById(R.id.quarantine_empty);
        
        Button backButton = (Button) findViewById(R.id.quarantine_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        loadItems();
    }

    private void loadItems() {
        items = QuarantineManager.getItems(this);
        if (items.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter = new QuarantineAdapter(this, items);
            listView.setAdapter(adapter);
        }
    }

    private void runAction(final String command, final String successMsg, final Runnable onSuccess) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process process;
                    if (ShellUtils.hasKnownSuBinary()) {
                        process = ShellUtils.newRootProcess(command).start();
                    } else {
                        process = new ProcessBuilder("/system/bin/sh", "-c", command).start();
                    }
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QuarantineActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                                if (onSuccess != null) onSuccess.run();
                            }
                        });
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(QuarantineActivity.this, "Error (Code: " + exitCode + ")", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(QuarantineActivity.this, "Execution failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private class QuarantineAdapter extends ArrayAdapter<QuarantineManager.QuarantineItem> {
        private LayoutInflater inflater;
        private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        public QuarantineAdapter(Context context, List<QuarantineManager.QuarantineItem> items) {
            super(context, 0, items);
            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_quarantine, parent, false);
            }

            final QuarantineManager.QuarantineItem item = getItem(position);
            TextView nameView = (TextView) convertView.findViewById(R.id.item_threat_name);
            TextView pathView = (TextView) convertView.findViewById(R.id.item_original_path);
            TextView dateView = (TextView) convertView.findViewById(R.id.item_date);
            Button restoreBtn = (Button) convertView.findViewById(R.id.btn_restore);
            Button deleteBtn = (Button) convertView.findViewById(R.id.btn_delete);

            nameView.setText(item.threatName);
            pathView.setText(item.originalPath);
            dateView.setText(sdf.format(new Date(item.timestamp)));

            restoreBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(QuarantineActivity.this)
                            .setTitle("Restore file?")
                            .setMessage("Are you sure you want to restore " + item.originalPath + "?")
                            .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String cmd = "mv -f '" + item.quarantinePath + "' '" + item.originalPath + "'";
                                    runAction(cmd, "File restored", new Runnable() {
                                        @Override
                                        public void run() {
                                            QuarantineManager.removeItem(QuarantineActivity.this, item.id);
                                            loadItems();
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

            deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(QuarantineActivity.this)
                            .setTitle("Delete file?")
                            .setMessage("Are you sure you want to permanently delete this file?")
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String cmd = "rm -f '" + item.quarantinePath + "'";
                                    runAction(cmd, "File deleted", new Runnable() {
                                        @Override
                                        public void run() {
                                            QuarantineManager.removeItem(QuarantineActivity.this, item.id);
                                            loadItems();
                                        }
                                    });
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

            return convertView;
        }
    }
}