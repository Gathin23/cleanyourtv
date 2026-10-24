package io.github.jemychao.restart;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

/** The tile and the card: asks whether to restart the TV, with Cancel focused. Back cancels. */
public class RestartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.title)
                .setMessage(R.string.body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restart, (d, which) -> {
                    if (!RestartService.restart()) Toast.makeText(this, R.string.no_service, Toast.LENGTH_LONG).show();
                })
                .create();
        dialog.setOnDismissListener(d -> finish());
        dialog.show();
        Button cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (cancel != null) cancel.requestFocus();
    }
}
