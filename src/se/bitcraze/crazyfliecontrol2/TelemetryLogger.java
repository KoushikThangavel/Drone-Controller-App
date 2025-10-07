package se.bitcraze.crazyfliecontrol2;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TelemetryLogger {
    private static final String LOG_TAG = "TelemetryLogger";
    private File logFile;

    public TelemetryLogger(Context context) {
        logFile = new File(context.getCacheDir(), "telemetry_log.txt");

        Log.d(LOG_TAG, "Log file path: " + logFile.getAbsolutePath());
    }

    public void log(String message) {
        try {
            FileWriter writer = new FileWriter(logFile, true); // append mode
            writer.append(System.currentTimeMillis() + ": " + message).append("\n");
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write telemetry", e);
        }
    }

    public String getLogFilePath() {
        return logFile.getAbsolutePath();
    }
}
