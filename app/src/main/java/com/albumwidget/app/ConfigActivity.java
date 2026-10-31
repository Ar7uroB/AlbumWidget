package com.albumwidget.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de configuración del álbum: carpetas, tiempo por imagen y opciones.
 * Se abre al colocar el widget, al mantenerlo presionado > Reconfigurar,
 * o desde el icono de la app.
 */
public class ConfigActivity extends Activity {
    private static final int REQ_FOLDER = 1;
    private static final String[] UNITS = {"segundos", "minutos", "horas"};
    private static final long[] UNIT_SEC = {1, 60, 3600};

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<String> folders = new ArrayList<>();

    private LinearLayout folderList;
    private EditText intervalInput;
    private Spinner unitSpinner;
    private Switch shuffleSwitch;
    private Switch fitSwitch;
    private Switch roundedSwitch;
    private Button exactButton;
    private TextView exactNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Abierta desde el icono de la app: se edita el primer widget existente.
            int[] ids = AppWidgetManager.getInstance(this)
                    .getAppWidgetIds(new ComponentName(this, AlbumWidgetProvider.class));
            if (ids.length > 0) widgetId = ids[0];
        }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showHelp();
            return;
        }

        buildUi();
        loadSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateExactAlarmUi();
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private void showHelp() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.addView(label("Álbum Widget", 24, true));
        TextView t = label("\nTodavía no has añadido el widget.\n\n"
                + "Mantén presionado un espacio vacío de la pantalla de inicio, "
                + "elige «Widgets», busca «Álbum Widget» y arrástralo. "
                + "Al colocarlo podrás elegir tus carpetas y el tiempo por imagen.", 16, false);
        t.setGravity(Gravity.CENTER);
        root.addView(t);
        setContentView(root);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(root);

        root.addView(label("Carpetas del álbum", 18, true));
        folderList = new LinearLayout(this);
        folderList.setOrientation(LinearLayout.VERTICAL);
        root.addView(folderList);

        Button add = new Button(this);
        add.setText("+ Agregar carpeta");
        add.setOnClickListener(v -> pickFolder());
        root.addView(add);

        View gap1 = new View(this);
        root.addView(gap1, new LinearLayout.LayoutParams(1, dp(16)));

        root.addView(label("Tiempo por imagen", 18, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        intervalInput = new EditText(this);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setEms(5);
        row.addView(intervalInput);
        unitSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, UNITS);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(ad);
        row.addView(unitSpinner);
        root.addView(row);
        root.addView(label("Mínimo " + Prefs.MIN_INTERVAL_SEC + " segundos.", 12, false));

        View gap2 = new View(this);
        root.addView(gap2, new LinearLayout.LayoutParams(1, dp(16)));

        shuffleSwitch = new Switch(this);
        shuffleSwitch.setText("Orden aleatorio");
        shuffleSwitch.setPadding(0, dp(8), 0, dp(8));
        root.addView(shuffleSwitch);

        fitSwitch = new Switch(this);
        fitSwitch.setText("Mostrar imagen completa (sin recortar)");
        fitSwitch.setPadding(0, dp(8), 0, dp(8));
        root.addView(fitSwitch);

        roundedSwitch = new Switch(this);
        roundedSwitch.setText("Esquinas redondeadas");
        roundedSwitch.setPadding(0, dp(8), 0, dp(8));
        root.addView(roundedSwitch);

        exactNote = label("Para que los cambios sean puntuales, permite «Alarmas y recordatorios» "
                + "a esta app. Sin eso Android puede retrasar los cambios.", 12, false);
        exactNote.setPadding(0, dp(12), 0, 0);
        root.addView(exactNote);
        exactButton = new Button(this);
        exactButton.setText("Permitir cambios puntuales");
        exactButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        root.addView(exactButton);

        TextView tip = label("Toca el widget para pasar a la siguiente imagen. "
                + "Mantenlo presionado y elige «Reconfigurar» para volver aquí.", 12, false);
        tip.setPadding(0, dp(12), 0, dp(12));
        root.addView(tip);

        Button save = new Button(this);
        save.setText("Guardar");
        save.setOnClickListener(v -> save());
        root.addView(save);

        setContentView(scroll);
    }

    private void updateExactAlarmUi() {
        if (exactButton == null) return;
        boolean needed = false;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            needed = am != null && !am.canScheduleExactAlarms();
        }
        int vis = needed ? View.VISIBLE : View.GONE;
        exactButton.setVisibility(vis);
        exactNote.setVisibility(vis);
    }

    // ---------------------------------------------------------------- datos

    private void loadSettings() {
        folders.clear();
        folders.addAll(Prefs.getFolders(this, widgetId));
        refreshFolderList();

        long sec = Prefs.getIntervalSec(this, widgetId);
        int unit = 0;
        if (sec % 3600 == 0) unit = 2;
        else if (sec % 60 == 0) unit = 1;
        intervalInput.setText(String.valueOf(sec / UNIT_SEC[unit]));
        unitSpinner.setSelection(unit);

        shuffleSwitch.setChecked(Prefs.getShuffle(this, widgetId));
        fitSwitch.setChecked(Prefs.getFit(this, widgetId));
        roundedSwitch.setChecked(Prefs.getRounded(this, widgetId));
    }

    private void refreshFolderList() {
        folderList.removeAllViews();
        if (folders.isEmpty()) {
            TextView t = label("Aún no hay carpetas.", 14, false);
            t.setPadding(0, dp(8), 0, dp(8));
            folderList.addView(t);
            return;
        }
        for (final String f : new ArrayList<>(folders)) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label("📁  " + folderName(f), 16, false);
            r.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button del = new Button(this);
            del.setText("Quitar");
            del.setOnClickListener(v -> {
                folders.remove(f);
                refreshFolderList();
            });
            r.addView(del);
            folderList.addView(r);
        }
    }

    private String folderName(String uriStr) {
        try {
            Uri tree = Uri.parse(uriStr);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            try (Cursor c = getContentResolver().query(doc,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
            }
        } catch (Exception ignored) {
            // se muestra el URI tal cual
        }
        return uriStr;
    }

    private void pickFolder() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(it, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el permiso de esa carpeta.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String s = uri.toString();
        if (!folders.contains(s)) folders.add(s);
        refreshFolderList();
    }

    private void save() {
        if (folders.isEmpty()) {
            Toast.makeText(this, "Agrega al menos una carpeta.", Toast.LENGTH_SHORT).show();
            return;
        }
        long value;
        try {
            value = Long.parseLong(intervalInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Escribe un número para el tiempo.", Toast.LENGTH_SHORT).show();
            return;
        }
        long sec = value * UNIT_SEC[unitSpinner.getSelectedItemPosition()];
        if (sec < Prefs.MIN_INTERVAL_SEC) {
            Toast.makeText(this, "El mínimo es " + Prefs.MIN_INTERVAL_SEC + " segundos.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (sec > Prefs.MAX_INTERVAL_SEC) {
            Toast.makeText(this, "El máximo es 7 días.", Toast.LENGTH_SHORT).show();
            return;
        }

        Prefs.setFolders(this, widgetId, folders);
        Prefs.setIntervalSec(this, widgetId, sec);
        Prefs.setShuffle(this, widgetId, shuffleSwitch.isChecked());
        Prefs.setFit(this, widgetId, fitSwitch.isChecked());
        Prefs.setRounded(this, widgetId, roundedSwitch.isChecked());
        Prefs.setIndex(this, widgetId, 0);
        ImageSource.invalidate(widgetId);

        AlbumWidgetProvider.refreshInBackground(this, widgetId);

        setResult(RESULT_OK, new Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
}
