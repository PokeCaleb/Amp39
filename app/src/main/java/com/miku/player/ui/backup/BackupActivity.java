package com.miku.player.ui.backup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.miku.player.data.repository.AppDatabase;
import com.miku.player.databinding.ActivityBackupBinding;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupActivity extends AppCompatActivity {

    private ActivityBackupBinding binding;
    private BackupManager backupManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Android 9- needs WRITE_EXTERNAL_STORAGE for export
    private final ActivityResultLauncher<String> writePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) performExport();
                        else Snackbar.make(binding.getRoot(),
                                "Se necesita permiso de almacenamiento para crear el respaldo.",
                                Snackbar.LENGTH_LONG).show();
                    });

    // Picker for full restore (listas + carátulas)
    private final ActivityResultLauncher<String[]> importFullPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    takePersistable(uri);
                    confirmAndImportFull(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBackupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        backupManager = new BackupManager(this);
        refreshLastBackupLabel();

        binding.cardExport.setOnClickListener(v -> requestExport());
        binding.cardImport.setOnClickListener(v ->
                importFullPicker.launch(new String[]{"application/zip", "*/*"}));
    }

    // ── Last backup label ─────────────────────────────────────────────────────

    private void refreshLastBackupLabel() {
        String last = backupManager.getLastBackupTime();
        binding.tvLastBackup.setText(last != null
                ? "Último respaldo: " + last
                : "Sin respaldo previo");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private void requestExport() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        performExport();
    }

    private void performExport() {
        showProgress("Creando respaldo…");
        executor.execute(() -> {
            try {
                String fileName = backupManager.export();
                runOnUiThread(() -> {
                    hideProgress();
                    refreshLastBackupLabel();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("✓ Respaldo creado")
                            .setMessage(
                                "Archivo guardado en:\n\n"
                                + "Descargas/" + BackupManager.BACKUP_FOLDER + "/" + fileName
                                + "\n\nPuedes copiar ese archivo a tu computadora o a la nube "
                                + "antes de desinstalar la app.")
                            .setPositiveButton("Entendido", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    showError("Error al crear respaldo: " + e.getMessage());
                });
            }
        });
    }

    // ── Import full (listas + carátulas del respaldo) ─────────────────────────

    private void confirmAndImportFull(Uri zipUri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restaurar respaldo completo")
                .setMessage(
                    "Se restaurarán tus listas de reproducción, favoritos "
                    + "y las carátulas guardadas en el respaldo.\n\n¿Continuar?")
                .setPositiveButton("Restaurar", (d, w) -> performImportFull(zipUri))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void performImportFull(Uri zipUri) {
        showProgress("Restaurando datos…");
        executor.execute(() -> {
            try {
                int covers = backupManager.importFromUri(zipUri);
                AppDatabase.resetInstance();
                runOnUiThread(() -> {
                    hideProgress();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("✓ Restauración completada")
                            .setMessage(
                                "Listas y carátulas restauradas (" + covers + " imágenes).\n\n"
                                + "La app se cerrará ahora para aplicar los cambios.")
                            .setPositiveButton("Cerrar app", (d, w) ->
                                    android.os.Process.killProcess(android.os.Process.myPid()))
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideProgress();
                    showError("Error al restaurar: " + e.getMessage());
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void takePersistable(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    private void showProgress(String msg) {
        binding.tvProgress.setText(msg);
        binding.progressOverlay.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        binding.progressOverlay.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
        executor.shutdown();
    }
}
