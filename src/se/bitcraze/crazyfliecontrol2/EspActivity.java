
package se.bitcraze.crazyfliecontrol2;

import static se.bitcraze.crazyfliecontrol2.EspUdpDriver.DEVICE_ADDRESS;
import static se.bitcraze.crazyfliecontrol2.EspUdpDriver.DEVICE_PORT;
import static se.bitcraze.crazyfliecontrol2.EspUdpDriver.mIs_land;
import static se.bitcraze.crazyfliecontrol2.EspUdpDriver.mIsdis_Armed;
//import static se.bitcraze.crazyfliecontrol2.EspUdpDriver.ack_arm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;

import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;
import com.espressif.espdrone.android.R;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Locale;
import android.speech.tts.TextToSpeech;
import android.widget.ToggleButton;


import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


public abstract class EspActivity extends AppCompatActivity {
    private MutableLiveData<String> mBroadcastData;
    private EspUdpDriver udpDriver;  // ✅ Use a single instance of EspUdpDriver

    public static TextToSpeech textToSpeech;

    private Handler handler = new Handler();
    private Runnable checkConnectionRunnable;
    protected ImageButton toggleTakeOffLand;
    private ToggleButton toggleAlthold;
    private static boolean mIs_take_off;
    //private static boolean mIs_land;

    private Runnable checkTakeoffAckRunnable;
    private boolean hasSwitchedToLand = false;

    // New state guards to prevent ACK re-trigger
    private boolean lastCommandWasTakeoff = false;
    private boolean lastCommandWasLand = false;

    private SwitchCompat armDisarmSwitch;

    private ImageButton joystickHlock;

    private ToggleButton.OnCheckedChangeListener altholdListener;

    private boolean wasPreviouslyConnected = false;

    private Runnable checkTakeoffLandAckRunnable;
    private JoystickView mJoystickViewLeft;

    private boolean yawLockEnabled = true;
    private boolean first = true;
    private ImageView mBatteryIcon;
    private ImageView wifiIcon;
    private boolean isDroneConnected = false;

    private float lastBatteryVoltage = -1f;
    RecordingManager recorder;
    private boolean isRecording = false;
    private volatile EspUdpDriver.VideoReceiveThread mVideoReceiveThread;
    private TextView recordingTimer;
    private Handler timerHandler = new Handler();
    private long startTime = 0;


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                return;
            }
            mBroadcastData.setValue(action);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mBatteryIcon = findViewById(R.id.battery_icon);
        wifiIcon = findViewById(R.id.linkQuality_icon);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.ENGLISH);
            }
        });

        //recordingTimer = findViewById(R.id.recording_timer);

        // Initialize LiveData
        mBroadcastData = new MutableLiveData<>();

        // Register Wi-Fi State Broadcast Receiver
        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);

        // ✅ Initialize the UDP driver
        udpDriver = new EspUdpDriver(this);


    }
    public boolean isRecorderActive() {
        return recorder != null;
    }

    private int framesRecorded = 0;

    public void pushFrameToRecorder(Bitmap bmp) {
        if (!isRecording || recorder == null || recorder.getInputSurface() == null) return;

        Surface surface = recorder.getInputSurface();

        int w = bmp.getWidth() & ~1;
        int h = bmp.getHeight() & ~1;

        if (bmp.getWidth() != w || bmp.getHeight() != h) {
            bmp = Bitmap.createScaledBitmap(bmp, w, h, false);
        }

        Bitmap finalBmp = bmp;

        runOnUiThread(() -> {
            try {
                Canvas c = surface.lockCanvas(null);
                if (c != null) {
                    c.drawBitmap(finalBmp, 0f, 0f, null);
                    surface.unlockCanvasAndPost(c);
                    framesRecorded++;

                    if (framesRecorded == 1) {
                        forceKeyFrame();
                    }
                }
            } catch (IllegalStateException e) {
                Log.e("RECs", "❌ Surface already released: " + e.getMessage());
            }
        });
    }


    public void onRecordButton(View view) {
        ImageButton recordButton = (ImageButton) view;

        if (!isRecording) {
            int w = 640, h = 480, fps = 15;
            try {
                recorder = new RecordingManager(this, w, h, fps);
                Surface s = recorder.start();

                if (mVideoReceiveThread != null) {
                    mVideoReceiveThread.setRecorderSurface(s);
                }

                isRecording = true;
              //  startRecordingTimer();

                // ✅ Show timer
                recordingTimer.setVisibility(View.VISIBLE);

                // ✅ Change icon to stop
                recordButton.setImageResource(R.drawable.record_stop_dark);
                recordButton.setTag("stop");

                Toast.makeText(this, "Recording…", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("RECsumma", "startRecording: ", e);
            }
        } else {
            isRecording = false;
           // stopRecordingTimer();

            new Thread(() -> {
                if (mVideoReceiveThread != null) mVideoReceiveThread.clearRecorderSurface();
                if (recorder != null) {
                    recorder.stop();
                    recorder = null;
                }
                framesRecorded = 0;

                runOnUiThread(() -> {
                    // ✅ Hide timer
                    recordingTimer.setVisibility(View.GONE);
                    recordingTimer.setText("00:00");

                    // ✅ Change icon back to start
                    recordButton.setImageResource(R.drawable.record_start_dark);
                    recordButton.setTag("start");

                    Toast.makeText(this, "Saved to Movies/ESP", Toast.LENGTH_SHORT).show();
                });
            }).start();
        }
    }







    // Add this method exactly:


    // Add helper to force keyframe:
    private void forceKeyFrame() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle sync = new Bundle();
            sync.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            recorder.getCodec().setParameters(sync);
            Log.i("RECs", "Forced IDR (keyframe)");
        }
    }


    public static void speak(Context context, String message) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isVoiceEnabled = prefs.getBoolean("enable_voice", true);

        if (isVoiceEnabled && textToSpeech != null) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    ////******************************************** END  ****************************************************///
    boolean isFlying = false;

   // private ImageButton toggleTakeOffLand;

    @Override
    protected void onStart() {
        super.onStart();
        //boolean isConnected = EspUdpDriver.isDroneConnected();
        //ImageButton recordButton = findViewById(R.id.btn_record_toggle);
        toggleTakeOffLand = findViewById(R.id.toggle_takeoff_land);

        toggleAlthold = findViewById(R.id.toggle_althold);


        joystickHlock = findViewById(R.id.joystick_left_hlock);

        armDisarmSwitch = findViewById(R.id.customSwitch);
        mJoystickViewLeft = findViewById(R.id.joystick_left);


        if (mJoystickViewLeft != null) {
            mJoystickViewLeft.setHorizontalLocked(true);  // ✅ Lock yaw axis!
        }

        if (joystickHlock != null) {
            joystickHlock.setSelected(true);               // ✅ Show visually as locked (brown)
            joystickHlock.setBackgroundResource(R.drawable.button_yawlock_selector); // ✅ Style applied
        }

     //   toggleAlthold = findViewById(R.id.toggle_althold);



        altholdListener = (buttonView, isChecked) -> {
            althold_1(buttonView); // Will send AltHold/Sports command
        };
        toggleAlthold.setOnCheckedChangeListener(altholdListener);


        checkConnectionRunnable = new Runnable() {
            @Override
            public void run() {
                boolean isConnected = EspUdpDriver.isDroneConnected();

                if (isConnected) {
                    // Re-enable controls
                    boolean isSportsMode = toggleAlthold != null && toggleAlthold.isChecked();
                    boolean isArmed = arm_state;

                    if (isArmed && !isSportsMode) {
                        toggleTakeOffLand.setEnabled(true);
                        toggleTakeOffLand.setAlpha(1f);
                    } else {
                        toggleTakeOffLand.setEnabled(false);
                        toggleTakeOffLand.setAlpha(0.5f);
                    }


                    joystickHlock.setEnabled(true);
                    joystickHlock.setAlpha(1f);

                    if (EspUdpDriver.isReadyToFly) {
                        armDisarmSwitch.setEnabled(true);
                        armDisarmSwitch.setAlpha(1f);
                    } else {
                        armDisarmSwitch.setEnabled(false);
                        armDisarmSwitch.setAlpha(0.5f);
                    }

//                    if (recordButton != null) {
//                        recordButton.setEnabled(true);
//                      //  recordButton.setAlpha(1f);
//                    }
                    toggleAlthold.setEnabled(true);
                    toggleAlthold.setAlpha(1f);

                    wasPreviouslyConnected = true; // Update flag
                } else {
                    toggleTakeOffLand.setEnabled(false);
                    toggleTakeOffLand.setAlpha(0.5f);

                    joystickHlock.setEnabled(false);
                    joystickHlock.setAlpha(0.5f);

                    armDisarmSwitch.setEnabled(false);
                    armDisarmSwitch.setAlpha(0.5f);

                    toggleAlthold.setEnabled(false);
                    toggleAlthold.setAlpha(0.5f);
//                    if (recordButton != null) {
//                        recordButton.setEnabled(false);
//                       // recordButton.setAlpha(0.5f);
//                    }

                    // Reset logic only runs once after disconnect
                    if (wasPreviouslyConnected) {
                        resetAllControlsToDefault();
                        wasPreviouslyConnected = false;
                    }
                }
                handler.postDelayed(this, 1000);
            }
        };


        handler.postDelayed(checkConnectionRunnable, 1000);

        checkTakeoffLandAckRunnable = new Runnable() {
            @Override
            public void run() {
//                if(mIsdis_Armed){
//                    toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
//                    toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
//                }
                if (mIs_land) {
                    runOnUiThread(() -> {
//                        toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
//                        toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
                        // ... existing code ...
                        // Only send DISARM if you are still armed!
                        if (arm_state) {
                            new Thread(() -> {
                                try {
                                    initializeUdpSocket();
                                    byte[] disarmCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x00};
                                    InetAddress droneAddress = InetAddress.getByName(DEVICE_ADDRESS);
                                    DatagramPacket packet_2 = new DatagramPacket(disarmCommand, disarmCommand.length, droneAddress, DEVICE_PORT);
                                    udpSocket.send(packet_2);
                                    Log.d("DISARM", "✅ Disarm command sent after landing");

                                    runOnUiThread(() -> {

                                        speak(getApplicationContext(), "Disarmed");
                                        showAssistantMessage("Disarm completed after landing");
                                        if (armDisarmSwitch != null) {
                                            armDisarmSwitch.setChecked(false);
                                            armDisarmSwitch.setEnabled(false);
                                        }
                                        if (toggleTakeOffLand != null) {
                                            toggleTakeOffLand.setEnabled(false);
                                            toggleTakeOffLand.setAlpha(0.5f);
                                        }
                                        arm_state = false;
                                        isFlying = false;
                                    });

                                } catch (Exception e) {
                                    Log.e("DISARM", "❌ Failed to send disarm: " + e.getMessage());
                                }
                            }).start();
                        } else {
                            // Already disarmed, just update UI
                            speak(getApplicationContext(), "Already disarmed");
                            showAssistantMessage("Drone is already disarmed.");
                            if (armDisarmSwitch != null) {
                                armDisarmSwitch.setChecked(false);
                                armDisarmSwitch.setEnabled(false);
                            }
                            if (toggleTakeOffLand != null) {
                                toggleTakeOffLand.setEnabled(false);
                                toggleTakeOffLand.setAlpha(0.5f);
                            }
                            isFlying = false;
                        }
                    });
                    mIs_land = false;
                }


                if (EspUdpDriver.isDroneConnected()) {

                    if (EspUdpDriver.ack_take_off()) {
                        runOnUiThread(() -> {
                            toggleTakeOffLand.setImageResource(R.drawable.ic_land);
                            toggleTakeOffLand.setTag(R.drawable.ic_land);
                            speak(getApplicationContext(), "Takeoff detected");
                            showAssistantMessage("Drone has taken off - switched to LAND");
                        });
                    }
                }
//
//                    if (EspUdpDriver.ack_land()) {
//                        runOnUiThread(() -> {
//                            toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
//                            toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
//                            Log.d("land_test_2", "✅ LAND UDP Sent");
//                            speak(getApplicationContext(), "Landing detected");
//                            showAssistantMessage("Drone has landed - switched to TAKEOFF");
//
//                        });
//                        new Thread(() -> {
//                            try {
//                                initializeUdpSocket(); // Make sure it's ready
//                                byte[] disarmCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x00};
//                                byte[] messageToSend;
//                                messageToSend = disarmCommand;
//
//                                InetAddress droneAddress = InetAddress.getByName(DEVICE_ADDRESS);
//                                DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, droneAddress, DEVICE_PORT);
//
//
//                                udpSocket.send(packet);
//                                Log.d("DISARM", "✅ Disarm command sent after landing");
//
//                                // Wait for disarm ACK
//                                //boolean ackReceived = waitForArmAck(false, 3000); // false = waiting for DISARM ack
//
//                                runOnUiThread(() -> {
//                                    speak(getApplicationContext(), "Disarmed");
//                                    showAssistantMessage("Disarm completed after landing");
//
//                                    // Update arm switch UI
//                                    if (armDisarmSwitch != null) {
//                                        armDisarmSwitch.setChecked(false);
//                                        armDisarmSwitch.setEnabled(false);
//                                       // armDisarmSwitch.setAlpha(0.5f);
//                                    }
//
//                                    // Disable takeoff button after disarm
//                                    if (toggleTakeOffLand != null) {
//                                        toggleTakeOffLand.setEnabled(false);
//                                        toggleTakeOffLand.setAlpha(0.5f);
//                                    }
//
//                                    // Reset internal state
//                                    arm_state = false;
//                                    isFlying = false;
//                                });
//                               // Log.d("DISARM", "DISARM ACK: CD CC AC 42 87");
//                            } catch (Exception e) {
//                                Log.e("DISARM", "❌ Failed to send disarm: " + e.getMessage());
//                            }
//                        }).start();
//
//                    }
//
//
//                }

                handler.postDelayed(this, 500);
            }
        };


//        handler.postDelayed(checkTakeoffAckRunnable, 500);

        toggleTakeOffLand.setOnClickListener(v -> {
            ImageButton button = (ImageButton) v;
            int currentDrawableId = (Integer) button.getTag();

            button.setEnabled(false); // Always disable to prevent spamming

            if (currentDrawableId == R.drawable.ic_takeoff) {
                takeoff(button); // Will only change to land if ACK is received
            } else {
                land(button); // Will only change to takeoff if ACK is received
            }
        });
        handler.postDelayed(checkTakeoffLandAckRunnable, 500);

        // Set initial tag
        toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
    }
//    private final Runnable timerRunnable = new Runnable() {
//        @Override
//        public void run() {
//            long elapsed = System.currentTimeMillis() - startTime;
//            int totalSeconds = (int) (elapsed / 1000);
//            int minutes = totalSeconds / 60;
//            int seconds = totalSeconds % 60;
//
//            String formatted = String.format("%02d:%02d", minutes, seconds);
//
//            Log.d("RECORD_TIMER", "Tick: " + formatted);
//
//            runOnUiThread(() -> {
//                TextView timerView = findViewById(R.id.recording_timer);  // force refresh each tick
//                if (timerView != null) {
//                    timerView.setText(formatted);
//                    timerView.setVisibility(View.VISIBLE);
//                    Log.d("RECORD_TIMER", "✅ UI updated: " + formatted);
//                } else {
//                    Log.e("RECORD_TIMER", "❌ TextView is NULL in UI thread");
//                }
//            });
//
//            timerHandler.postDelayed(this, 1000); // Schedule next tick
//        }
//    };



//    private void startRecordingTimer() {
//        Log.d("RECORD_TIMER", "⏱ Timer started");
//        startTime = System.currentTimeMillis();
//        timerHandler.postDelayed(timerRunnable, 1000); // 🔁 Delay added
//    }
//
//
//
//    private void stopRecordingTimer() {
//        Log.d("RECORD_TIMER", "⏹ Timer stopped");
//        TextView timerView = findViewById(R.id.recording_timer);
//        timerView.setVisibility(View.GONE);
//        timerHandler.removeCallbacks(timerRunnable);
//        runOnUiThread(() -> recordingTimer.setText("00:00")); // Always reset safely
//    }


    ////*********************** Land function ***********************************************///

    public void land(ImageButton button) {
        speak(getApplicationContext(), "Landing Initiated");
        Log.d("BUTTON-CLICK", "Land Button Pressed");
        hasSwitchedToLand = false; // ✅ ready to detect next flight

        lastCommandWasLand = true;
        lastCommandWasTakeoff = false;

        EspUdpDriver.resetLandAck();
        byte[] landCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x11, (byte) 0x00};

        new Thread(() -> {
            try {
                initializeUdpSocket();
                DatagramPacket packet = new DatagramPacket(landCommand, landCommand.length,
                        InetAddress.getByName(DEVICE_ADDRESS), DEVICE_PORT);

                udpSocket.send(packet);
                Log.d("UDP-SEND", "✅ LAND UDP Sent");

                boolean ackReceived = waitForlandAck(30000);

                runOnUiThread(() -> {
                    if (ackReceived) {
                        button.setImageResource(R.drawable.ic_takeoff);
                        button.setTag(R.drawable.ic_takeoff);
                        Log.d("BUTTON-CLICK", "Land Ack recieved");
                        speak(getApplicationContext(), "Landing Completed");
                        showAssistantMessage("Landing complete");
                        new Thread(() -> {
                            try {
                                initializeUdpSocket(); // Make sure it's ready
                                byte[] disarmCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x00};
                                byte[] messageToSend;
                                messageToSend = disarmCommand;

                                InetAddress droneAddress = InetAddress.getByName(DEVICE_ADDRESS);
                                DatagramPacket packet_2 = new DatagramPacket(messageToSend, messageToSend.length, droneAddress, DEVICE_PORT);


                                udpSocket.send(packet_2);
                                Log.d("DISARM", "✅ Disarm command sent after landing");

                                // Wait for disarm ACK
                                //boolean ackReceived = waitForArmAck(false, 3000); // false = waiting for DISARM ack

                                runOnUiThread(() -> {
                                    speak(getApplicationContext(), "Disarmed");

                                    showAssistantMessage("Disarm completed after landing");

                                    // Update arm switch UI
                                    if (armDisarmSwitch != null) {
                                        armDisarmSwitch.setChecked(false);
                                        armDisarmSwitch.setEnabled(false);
                                        // armDisarmSwitch.setAlpha(0.5f);
                                    }

                                    // Disable takeoff button after disarm
                                    if (toggleTakeOffLand != null) {

                                        toggleTakeOffLand.setEnabled(false);
                                        toggleTakeOffLand.setAlpha(0.5f);

                                    }

                                    // Reset internal state
                                    arm_state = false;
                                    isFlying = false;
                                });
                                // Log.d("DISARM", "DISARM ACK: CD CC AC 42 87");
                            } catch (Exception e) {
                                Log.e("DISARM", "❌ Failed to send disarm: " + e.getMessage());
                            }
                        }).start();




                    } else {
                        button.setImageResource(R.drawable.ic_takeoff); // fail-safe
                        button.setTag(R.drawable.ic_takeoff);
                        Log.d("BUTTON-CLICK", "Land Ack not received");
                        speak(getApplicationContext(), "Landing failed");
                        showAssistantMessage("Land ACK not received");
                    }
                    button.setEnabled(true);
                });

            } catch (Exception e) {
                Log.e("LAND-ACK", "❌ Land Error: " + e.getMessage());
            }
        }).start();
    }




    private boolean waitForlandAck(int timeoutMillis) {
        int elapsed = 0;
        int interval = 100;
        while (elapsed < timeoutMillis) {
            if (EspUdpDriver.ack_land()) {  // ✅ Correct
                return true;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            elapsed += interval;
        }
        return false;
    }


    ////******************************************** END  ****************************************************///



    ////*********************** Take off/land ***********************************************///
    ////*********************** This is my Actual Code without Ganesh's Takeoff logic ***********************************************///
//    public void takeoff(ImageButton button) {
//        Log.d("BUTTON-CLICK", "Takeoff Button Pressed");
//        speak(getApplicationContext(), "Takeoff Initiated");
//
//        hasSwitchedToLand = true; // ✅ prevent redundant switch
//
//        lastCommandWasTakeoff = true;
//        lastCommandWasLand = false;
//
//        EspUdpDriver.resetTakeoffAck();
//        byte[] takeoffCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x11, (byte) 0x01};
//
//        new Thread(() -> {
//            try {
//                initializeUdpSocket();
//                DatagramPacket packet = new DatagramPacket(takeoffCommand, takeoffCommand.length,
//                        InetAddress.getByName(DEVICE_ADDRESS), DEVICE_PORT);
//
//                udpSocket.send(packet);
//                Log.d("UDP-SEND", "✅ TAKEOFF UDP Sent");
//
//                boolean ackReceived = waitForTakeoffAck(30000);
//
//                runOnUiThread(() -> {
//                    if (ackReceived) {
//                        button.setImageResource(R.drawable.ic_land);
//                        button.setTag(R.drawable.ic_land);
//                        speak(getApplicationContext(), "Takeoff Completed");
//                        showAssistantMessage("Takeoff Ack received successful");
//                    } else {
//                        button.setImageResource(R.drawable.ic_takeoff);
//                        button.setTag(R.drawable.ic_takeoff);
//                        speak(getApplicationContext(), "Takeoff failed");
//                        showAssistantMessage("Takeoff ACK not received");
//                    }
//                    button.setEnabled(true);
//                });
//
//            } catch (Exception e) {
//                Log.e("UDP-SEND", "❌ Takeoff Error: " + e.getMessage());
//            }
//        }).start();
//    }
//
//
//    // ✅ Robust polling method for TAKEOFF ACK
//    private boolean waitForTakeoffAck(int timeoutMillis) {
//        int elapsed = 0;
//        int interval = 100; // check every 100ms
//        while (elapsed < timeoutMillis) {
//            if (EspUdpDriver.ack_take_off()) {
//                return true; // Ack received!
//            }
//            try {
//                Thread.sleep(interval);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                return false;
//            }
//            elapsed += interval;
//        }
//        return false; // Timeout reached without ack
//    }

    private volatile int takeoffRequestId = 0;  // Tracks the latest click
    public void takeoff(ImageButton button) {
        Log.d("BUTTON-CLICK", "Takeoff Button Pressed");
        speak(getApplicationContext(), "Takeoff Initiated");

        hasSwitchedToLand = true;
        lastCommandWasTakeoff = true;
        lastCommandWasLand = false;

        // Generate a new request ID
        final int currentRequestId = ++takeoffRequestId;

        EspUdpDriver.resetTakeoffAck();
        byte[] takeoffCommand = {(byte) 0x71, (byte) 0x13, (byte) 0x11, (byte) 0x01};

        new Thread(() -> {
            try {
                // If this thread is already outdated, skip it
                if (currentRequestId != takeoffRequestId) {
                    Log.d("button_cmd", "🛑 Skipping old takeoff thread: " + currentRequestId);
                    return;
                }

                initializeUdpSocket();
                DatagramPacket packet = new DatagramPacket(takeoffCommand, takeoffCommand.length,
                        InetAddress.getByName(DEVICE_ADDRESS), DEVICE_PORT);

                udpSocket.send(packet);
                Log.d("button_cmd", "✅ TAKEOFF UDP Sent");

                boolean ackReceived = waitForTakeoffAck(15000, currentRequestId);

                // Again, skip if outdated
                if (currentRequestId != takeoffRequestId) {
                    Log.d("button_cmd", "🛑 Thread expired after ACK wait: " + currentRequestId);
                    return;
                }

                runOnUiThread(() -> {
                    if (currentRequestId != takeoffRequestId) {
                        Log.d("button_cmd", "🛑 UI update skipped for outdated thread");
                        return; // Avoid old threads modifying UI
                    }

                    if (ackReceived) {
                        button.setImageResource(R.drawable.ic_land);
                        button.setTag(R.drawable.ic_land);
                        Log.d("BUTTON-CLICK", "Takeoff Ack recieved");
                        speak(getApplicationContext(), "Takeoff Completed");
                        showAssistantMessage("Takeoff Ack received successful");
                    } else {
                        button.setImageResource(R.drawable.ic_takeoff);
                        button.setTag(R.drawable.ic_takeoff);
                        Log.d("BUTTON-CLICK", "Takeoff Ack not recieved");
                        speak(getApplicationContext(), "Takeoff failed");
                        showAssistantMessage("Takeoff ACK not received");
                    }
                });


            } catch (Exception e) {
                Log.e("take_off", "❌ Takeoff Error: " + e.getMessage());
            }
        }).start();
    }


    private boolean waitForTakeoffAck(int timeoutMillis, int requestId) {
        int elapsed = 0;
        int interval = 100;
        while (elapsed < timeoutMillis) {
            if (requestId != takeoffRequestId) {
                Log.d("button_cmd", "🛑 Exiting ACK wait early due to newer request");
                return false;
            }

            if (EspUdpDriver.ack_take_off()) {
                return true;
            }

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            elapsed += interval;
        }
        return false;
    }




    ////******************************************** END  ****************************************************///


    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(checkConnectionRunnable);
        handler.removeCallbacks(checkTakeoffLandAckRunnable);
    }


    ////*********************** Althold function ***********************************************///

    private boolean althold = true; // ✅ Start with AltHold mode as default
    //private boolean first = true;
    public void althold_1(View view) {
        Log.d("BUTTON-CLICK", "AltHold Toggle Pressed");
//        if(first)
//        {
//            speak(getApplicationContext(), "Sports Mode");
//            showAssistantMessage("Sports Mode ACK received");
//            first = false;
//        }
        ToggleButton toggle = (ToggleButton) view;
        boolean isSportsMode = toggle.isChecked(); // true when brown (Sports), false when white (AltHold)
        Log.d("UDP-SEND_SPORTS_2", "Sports packet sent");
        byte[] altholdOn = new byte[]{(byte) 0x71, (byte) 0x13, (byte) 0x22, (byte) 0x01};
        byte[] altholdOff = new byte[]{(byte) 0x71, (byte) 0x13, (byte) 0x22, (byte) 0x00};

        byte[] messageToSend = isSportsMode ? altholdOff : altholdOn;
        boolean willBeAltHold = !isSportsMode;

       // EspUdpDriver.resetAltHoldAck(); // add this line

        String statusMessage = willBeAltHold ? "📡 Enabling Altitude Hold Mode..." : "📡 Switching to Sports Mode...";
        althold = willBeAltHold;

        StringBuilder hexString = new StringBuilder();
        for (byte b : messageToSend) {
            hexString.append(String.format("%02X ", b));
        }
        // drone connection state

        Log.d("UDP-SEND_SPORTS", statusMessage + " Data (HEX): " + hexString.toString());

        new Thread(() -> {
            try {
                String DRONE_IP = DEVICE_ADDRESS;
                int DRONE_PORT = DEVICE_PORT;

                initializeUdpSocket();

                InetAddress droneAddress = InetAddress.getByName(DRONE_IP);
                DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, droneAddress, DRONE_PORT);

                udpSocket.send(packet);

                boolean ackReceived = false;

                if (first) {
                    Log.d("ACK-OVERRIDE", "⚡ First-time Sports Mode override – skipping wait");
                } else {
                    ackReceived = waitForAltHoldAck(willBeAltHold, 3000); // Wait for 3 seconds
                }

                if (first || ackReceived ) {
                    if (!ackReceived && first) {
                        Log.d("ACK-OVERRIDE", "✅ First-time Sports Mode override – faking ACK.");
                    } else {
                        Log.d("UDP-SEND_SPORTS", "✅ ACK received for " + (willBeAltHold ? "AltHold" : "Sports"));
                    }

                    runOnUiThread(() -> {
                        Log.d("Sports_ack", "SPORTS ACK RECEIVED or FORCED");
                        speak(getApplicationContext(), willBeAltHold ? "Altitude Hold Mode" : "Sports Mode");
                        showAssistantMessage(willBeAltHold ? "Altitude Hold ACK received" : "Sports Mode ACK received");
                    });

                    first = false; // ✅ Disable override for next time
                } else {

                    Log.d("Sports_ack", "SPORTS ACK NOT Test RECEIVED");
                    Log.d("UDP-SEND_SPORTS", "❌ No ACK received for " + (willBeAltHold ? "AltHold" : "Sports"));

                    runOnUiThread(() -> {
                        speak(getApplicationContext(), willBeAltHold ? "Althold ack not recived" : "Sports Ack not recieved");
                        showAssistantMessage(willBeAltHold ? "AltHold ACK not received" : "Sports Mode ACK not received");
                    });
                }

            } catch (Exception e) {
                Log.e("UDP-SEND", "❌ Error Sending UDP Data: " + e.getMessage());
            }
        }).start();
    }


    private boolean waitForAltHoldAck(boolean expectedAltHold, int timeoutMillis) {
        int waited = 0;
        int interval = 100;
        while (waited < timeoutMillis) {
            if (expectedAltHold && EspUdpDriver.ack_althold()) return true;
            if (!expectedAltHold && EspUdpDriver.ack_sports()) return true;

            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return false;
            }
            waited += interval;
        }
        return false;
    }

    ////******************************************** END  ****************************************************///

    private DatagramSocket udpSocket;

    private DatagramSocket getDriverSocket() {
        return udpDriver.getSocket();
    }

    private void initializeUdpSocket() {
        try {
            if (udpSocket == null || udpSocket.isClosed()) {
                // ❗ Instead of creating a new socket
                //udpSocket = new DatagramSocket();

                // ✅ USE the existing socket from EspUdpDriver
                udpSocket = getDriverSocket();
            }
        } catch (Exception e) {
            Log.e("UDP", "Socket Initialization Error: " + e.getMessage());
        }
    }


    ////*********************** ARM/Disarm ***********************************************///


    private boolean arm_state = false;

    public void arm(View view) {
        Log.d("BUTTON-CLICK", "ARM Button Pressed");

        byte[] arm = {(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x01};   //ARM PACKET
        byte[] disarm = {(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x00}; //DISARM PACKET

        byte[] messageToSend;
        boolean willArm;


        // drone connection state
        if (!EspUdpDriver.isDroneConnected()) {
            messageToSend = arm;
            willArm = true;
            Log.d("ARM_ACK", "📡 Drone disconnected. Forcing ARM ON...");
        } else {
            willArm = !arm_state;
            messageToSend = willArm ? arm : disarm;
            arm_state = willArm;
        }

        new Thread(() -> {
            try {
                String DRONE_IP = DEVICE_ADDRESS;
                int DRONE_PORT = DEVICE_PORT;

                initializeUdpSocket();

                InetAddress droneAddress = InetAddress.getByName(DRONE_IP);
                DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, droneAddress, DRONE_PORT);

                udpSocket.send(packet);
                //Log.d("ARM_ACK", "✅ UDP Data Sent Successfully");


                EspActivity.this.runOnUiThread(() -> {
                    speak(getApplicationContext(),willArm ? "Armed" : "Disarmed");
                });

                // ⏱️ Start waiting for ACK dynamically
                boolean ackReceived = waitForArmAck(willArm, 3000); // 3 seconds timeout
                // Wait max 3 seconds

                if (willArm) {
                    if (ackReceived) {
                        Log.d("BUTTON-CLICKd", "Arm Ack recieved");
                        // speak(getApplicationContext(),"ARM initiated");
                        showAssistantMessage("ARM ACK received successfully");
                        //showAssistantMessage("ARM Completed");
                    } else {
                        Log.d("BUTTON-CLICK", "❌ ARM ACK not received");
                        //speak(getApplicationContext(),"ARM ACK not received");
                        showAssistantMessage("ARM ACK not received");
                    }
                } else {
                    if (ackReceived) {
                        Log.d("BUTTON-CLICK", "DisArm Ack recieved");
                        //  speak(getApplicationContext(),"DisARM completed");
                        toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
                        toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
                        showAssistantMessage("DisARM ACK received successfully");

                        //showAssistantMessage("DisARM Completed");
                    } else {
                        Log.d("BUTTON-CLICK", "DisArm Ack not recieved");
                        showAssistantMessage("DisARM ACK not received");
                    }
                }

            } catch (Exception e) {
                Log.e("ARM_ACK", "❌ Error Sending UDP Data: " + e.getMessage());
            }
        }).start();
    }


    // ✅ New robust polling method
    private boolean waitForArmAck(boolean armRequested, int timeoutMillis) {
        int elapsed = 0;
        int interval = 100; // check every 100ms
        while (elapsed < timeoutMillis) {
            boolean ack = armRequested ? EspUdpDriver.ack_isArmed() : EspUdpDriver.ack_dis_Armed();
            if (ack) {
                return true; // Ack received!
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            elapsed += interval;
        }
        return false; // Timeout reached without ack
    }

    public void setDroneReady() {
        armDisarmSwitch.setEnabled(true);
        armDisarmSwitch.setAlpha(1f);
        speak(this, "Ready to fly");
        showAssistantMessage("Ready to fly");
    }

//    private boolean arm_state = false;
//
//    public void arm(View view) {
//
//        //EspUdpDriver.ack_isArmed();
//        Log.d("ARM_ACK", "ack" + EspUdpDriver.ack_isArmed());
//        Log.d("BUTTON-CLICK", "ARM Button Pressed");
//
//
//        //showAssistantMessage("ARM Ganesh " );
//
//
//        byte[] arm = new byte[]{(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x01};
//        byte[] disarm = new byte[]{(byte) 0x71, (byte) 0x13, (byte) 0x33, (byte) 0x00};
//
//        byte[] messageToSend;
//        String statusMessage;
//        boolean willArm;
//
//        if (!EspUdpDriver.isDroneConnected()) {
//            //Log.d("ARM_ACK", "❌ Drone is DISCONNECTED — forcing ARM ON");
//            messageToSend = arm;
//            statusMessage = "📡 Drone disconnected. Forcing ARM ON...";
//            willArm = true;
//        } else {
//            //Log.d("ARM_ACK", "🚀 Drone is CONNECTED");
//
//            willArm = !arm_state;
//            messageToSend = willArm ? arm : disarm;
//            //statusMessage = willArm ? "📡 Sending Arm command..." : "📡 Sending Disarm command...";
//            arm_state = willArm;
//        }
//
//        StringBuilder hexString = new StringBuilder();
//        for (byte b : messageToSend) {
//            hexString.append(String.format("%02X ", b));
//        }
//
//        //Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show();
//        //Log.d("ARM_ACK", statusMessage + " Data (HEX): " + hexString.toString());
//
//        new Thread(() -> {
//            try {
//                String DRONE_IP = DEVICE_ADDRESS;
//                int DRONE_PORT = DEVICE_PORT;
//
//                initializeUdpSocket();
//
//                InetAddress droneAddress = InetAddress.getByName(DRONE_IP);
//                DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, droneAddress, DRONE_PORT);
//
//                udpSocket.send(packet);
//                Log.d("ARM_ACK", "✅ UDP Data Sent Successfully (HEX): " + hexString.toString());
//
//                // 🔊 Speak based on actual command sent
//                speak(willArm ? "Armed" : "Disarmed");
//
//                if (willArm) {
//                    if (EspUdpDriver.ack_isArmed()) {
//
//                        Log.d("ARM_ACK", "✅ ARM initiated");
//
//                        showAssistantMessage("ARM Ganesh " );
//                        speak("ARM initiated");
//                    }
//                    else {
//                        showAssistantMessage("ARM ACK not received Ganesh");
//                    }
//                } else {
//                    if (EspUdpDriver.ack_dis_Armed()) {
//                        Log.d("ARM_ACK", "✅ DisARM completed");
//                        speak("DisARM completed");
//                    } else {
//                        Log.d("ARM_ACK", "❌ DisARM not completed");
//                        speak("DisARM ");
//                        showAssistantMessage("Disarm ganesh");
//                    }
//                }
//
//            } catch (Exception e) {
//                Log.e("ARM_ACK", "❌ Error Sending UDP Data: " + e.getMessage());
//            }
//        }).start();
//
    ////******************************************** END  ****************************************************///


    ////******************************************** Connect Start  ****************************************************///

//    public void connectpck(View view) {
//        Log.d("Connect-CLICK", "CONNECT Button Pressed");
//
//        byte[] connect_pck = {(byte) 0x71, (byte) 0x13, (byte) 0x55, (byte) 0x00};  // Connect packet
//
//        new Thread(() -> {
//            try {
//                String DRONE_IP = DEVICE_ADDRESS;
//                int DRONE_PORT = DEVICE_PORT;
//
//                initializeUdpSocket(); // Ensure socket is ready
//
//                InetAddress droneAddress = InetAddress.getByName(DRONE_IP);
//                DatagramPacket packet = new DatagramPacket(connect_pck, connect_pck.length, droneAddress, DRONE_PORT);
//
//                udpSocket.send(packet);
//
//                Log.d("CONNECT_ACK", "✅ Connect packet sent successfully");
//
//                runOnUiThread(() -> {
//                    speak(getApplicationContext(), "Connect packet sent");
//                    showAssistantMessage("Connect packet sent to drone.");
//                });
//
//            } catch (Exception e) {
//                Log.e("CONNECT_ACK", "❌ Error sending connect packet: " + e.getMessage());
//                runOnUiThread(() -> {
//                    speak(getApplicationContext(), "Failed to connect");
//                    showAssistantMessage("Failed to send connect packet.");
//                });
//            }
//        }).start();
//    }

    ////******************************************** Connect End  ****************************************************///


//    public void showAssistantMessage(final String message) {
//        runOnUiThread(() -> {
//            // Show assistant image
//            ImageView assistantImage = findViewById(R.id.assistantImage);
//            if (assistantImage != null) {
//                assistantImage.setVisibility(View.VISIBLE);
//            }
//
//            // Inflate popup layout
//            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//            View popupView = inflater.inflate(R.layout.custom_popup, null);
//
//            // Set popup message
//            TextView popupMessage = popupView.findViewById(R.id.popupMessage);
//            popupMessage.setText(message);
//
//            // ✅ Create popup window
//            final PopupWindow popupWindow = new PopupWindow(
//                    popupView,
//                    ViewGroup.LayoutParams.WRAP_CONTENT,
//                    ViewGroup.LayoutParams.WRAP_CONTENT,
//                    true
//            );
//
//            // ✅ Prevent layout shift & background dimming
//            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//            popupWindow.setOutsideTouchable(true);
//            popupWindow.setClippingEnabled(false); // prevents the popup from resizing parent or cutting off edges
//
//            // Show popup relative to assistant image
//            if (assistantImage != null) {
//                popupWindow.showAsDropDown(assistantImage, -150, -250);
//            } else {
//                popupWindow.showAtLocation(findViewById(android.R.id.content), Gravity.BOTTOM | Gravity.END, 16, 16);
//            }
//
//            // Auto-dismiss after 3 seconds
//            new Handler().postDelayed(() -> {
//                if (popupWindow.isShowing()) {
//                    popupWindow.dismiss();
//                }
//            }, 3000);
//        });
//    }


//SD-card-storage
    public void sendCameraUdpCommand(String command) {
        Log.d("CAMERA_UDP", "Sending command: " + command);

        new Thread(() -> {
            try {
                String CAM_IP = "192.168.43.42";    // ESP32-CAM IP
                int CAM_PORT = 4210;                // ESP32-CAM UDP port

                byte[] messageToSend = command.getBytes();

                // You should have a DatagramSocket field, or you can create it here
                DatagramSocket udpSocket = new DatagramSocket();
                InetAddress camAddress = InetAddress.getByName(CAM_IP);

                DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, camAddress, CAM_PORT);

                udpSocket.send(packet);

                Log.d("CAMERA_UDP", "✅ UDP command sent successfully: " + command);

                udpSocket.close();
            } catch (Exception e) {
                Log.e("CAMERA_UDP", "❌ Error sending UDP command: " + e.getMessage());
            }
        }).start();
    }
    public void showAssistantMessage(String message) {
        runOnUiThread(() -> {
            // Remove previous message view if it exists
            View existing = findViewById(R.id.assistantMessageBox);
            if (existing != null) {
                ((ViewGroup) existing.getParent()).removeView(existing);
            }

            // Create new message view
            TextView messageView = new TextView(this);
            messageView.setId(R.id.assistantMessageBox); // ✅ use unique ID only once
            messageView.setText(message);
            messageView.setBackgroundResource(R.drawable.popup_background);
            messageView.setTextColor(Color.BLACK);
            messageView.setTextSize(14);
            messageView.setPadding(20, 10, 20, 10);

            // Layout params to float near assistant image
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT);
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.setMarginStart(16);

            ConstraintLayout root = findViewById(R.id.rootLayout);
            root.addView(messageView, params);

            // Auto-remove after 3 seconds
            new Handler().postDelayed(() -> {
                root.removeView(messageView);
            }, 3000);
        });
    }

    @Override
    protected void onDestroy() {

        // Shut down TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        // Unregister Wi-Fi broadcast receiver
        unregisterReceiver(mReceiver);

        // Disconnect UDP driver
        if (udpDriver != null) {
            udpDriver.disconnect();
        }

        // ✅ Call parent class destroy method once
        super.onDestroy();
    }

    private void resetAllControlsToDefault() {
        Log.d("RESET", "🔄 Resetting all controls to default state...");

        // Reset toggleTakeOffLand to default (takeoff)
        toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
        toggleTakeOffLand.setTag(R.drawable.ic_takeoff);

        // Reset AltHold toggle to default (unchecked)
        if (toggleAlthold != null) {
            toggleAlthold.setOnCheckedChangeListener(null);
            toggleAlthold.setChecked(false);
            toggleAlthold.setSelected(false);
            toggleAlthold.setBackgroundResource(R.drawable.button_sports_selector);
            toggleAlthold.setOnCheckedChangeListener(altholdListener);
        }
        if (toggleTakeOffLand != null) {
            toggleTakeOffLand.setEnabled(true);
            toggleTakeOffLand.setAlpha(1f);
        }


        if (joystickHlock != null) {
            joystickHlock.setSelected(true);
            joystickHlock.setEnabled(true);
            joystickHlock.setAlpha(1f);
            joystickHlock.setBackgroundResource(R.drawable.button_yawlock_selector);
        }


        if (mJoystickViewLeft != null) {
            mJoystickViewLeft.setHorizontalLocked(true);  // ✅ Lock yaw axis!
        }
        EspUdpDriver.isReadyToFly = false;

        // Reset Arm switch to default (unchecked)
        if (armDisarmSwitch != null) {
            armDisarmSwitch.setChecked(false);
        }

        boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                 .getBoolean("is_dark_theme", false);

        if (!isDroneConnected) {
            mBatteryIcon.setImageResource(isDarkTheme ?
                    R.drawable.battery_0_light : R.drawable.battery_0_dark);
            wifiIcon.setImageResource(isDarkTheme ?
                    R.drawable.wifi4_light : R.drawable.wifi4_dark);

        }


        // Reset internal arm state
        arm_state = false;
        isFlying = false;
        first = true;
        // Optionally clear joystick or other controls here too
        Log.d("RESET", "✅ All controls returned to default.");
    }



    public void observeBroadcast(LifecycleOwner owner, Observer<String> observer) {
        mBroadcastData.observe(owner, observer);
    }

    public void removeBroadcastObserver(Observer<String> observer) {
        mBroadcastData.removeObserver(observer);
    }
    public class VideoRecorder {
        private MediaCodec encoder;
        private Surface inputSurface;
        private MediaMuxer muxer;
        private int videoTrack = -1;
        private boolean muxerStarted = false;

        public void start(File outFile, int width, int height, int bitRate, int frameRate) throws IOException {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();

            muxer = new MediaMuxer(outFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            videoTrack = -1;
            muxerStarted = false;
        }

        public Surface getInputSurface() {
            return inputSurface;
        }

        public void drainEncoder(boolean endOfStream) {
            final int TIMEOUT_USEC = 10000;
            if (endOfStream) {
                encoder.signalEndOfInputStream();
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int idx = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break;
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    videoTrack = muxer.addTrack(newFormat);
                    muxer.start();
                    muxerStarted = true;
                } else if (idx >= 0) {
                    ByteBuffer encoded = encoder.getOutputBuffer(idx);
                    if (info.size != 0 && muxerStarted) {
                        encoded.position(info.offset);
                        encoded.limit(info.offset + info.size);
                        muxer.writeSampleData(videoTrack, encoded, info);
                    }
                    encoder.releaseOutputBuffer(idx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        }

        public void stop() {
            drainEncoder(true); // 🔧 must flush before stopping
            encoder.stop();
            encoder.release();
            muxer.stop();
            muxer.release();
        }

    }

    public void showLogFileLocation() {
        TelemetryLogger logger = new TelemetryLogger(this);
        Toast.makeText(this, "Log file: " + logger.getLogFilePath(), Toast.LENGTH_LONG).show();
    }
    public void updateCameraFeedBitmap(Bitmap bmp) {
        runOnUiThread(() -> {
            ImageView cameraImageView = findViewById(R.id.cameraImageView);
            cameraImageView.setImageBitmap(bmp);
        });
    }

}
