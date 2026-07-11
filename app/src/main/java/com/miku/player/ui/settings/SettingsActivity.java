package com.miku.player.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.miku.player.databinding.ActivitySettingsBinding;
import com.miku.player.ui.backup.BackupActivity;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.cardBackup.setOnClickListener(v ->
                startActivity(new Intent(this, BackupActivity.class)));
    }

    @Override
    protected void onDestroy() { super.onDestroy(); binding = null; }
}
