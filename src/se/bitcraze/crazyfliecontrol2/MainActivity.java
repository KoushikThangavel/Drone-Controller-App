/**
 *    ||          ____  _ __
 * +------+      / __ )(_) /_______________ _____  ___
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import se.bitcraze.crazyflie.lib.crazyradio.Crazyradio;
import se.bitcraze.crazyfliecontrol.controller.Controls;
import se.bitcraze.crazyfliecontrol.controller.GamepadController;
import se.bitcraze.crazyfliecontrol.controller.GyroscopeController;
import se.bitcraze.crazyfliecontrol.controller.IController;
import se.bitcraze.crazyfliecontrol.controller.TouchController;
import se.bitcraze.crazyfliecontrol.prefs.PreferencesActivity;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;
import com.espressif.espdrone.android.R;

public class MainActivity extends EspActivity {

    private static final String LOG_TAG = "CrazyflieControl";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 42;
    private MainPresenter mPresenter;
    // Battery
    private ImageView mBatteryIcon;
    private TextView mTextView_battery;
    private int lastKnownBatteryPercentage=100;
    // Wifi Signal Quality
    private TextView mTextView_linkQuality;
    ImageButton settingsButton;
    ImageButton record_button;
    ImageButton record_button_stop;

    private JoystickView mJoystickViewLeft;
    private JoystickView mJoystickViewRight;
    private ImageButton mJoystickLeftHLock;
    private SharedPreferences mPreferences;
    private IController mController;
    private GamepadController mGamepadController;
    private String mRadioChannelDefaultValue;
    private String mRadioDatarateDefaultValue;
    private boolean mDoubleBackToExitPressedOnce = false;
    private Controls mControls;
    private SoundPool mSoundPool;
    private boolean mLoaded;
    private int mSoundConnect;
    private int mSoundDisconnect;
    private ImageButton mToggleConnectButton;
    private File mCacheDir;
    private TextView mTextViewDisplayName;
    private TextToSpeech textToSpeech;
    private boolean isOpeningSettings = false;
    private ImageView mBackgroundImage;
    private boolean retryMobileDataCheck = false;
    private SwitchCompat themeSwitch;
    private boolean isDroneConnected = false;
    private ImageView wifiIcon;
    private ImageView mHudArrow;
    private View currentDialogView;
    //private View currentDialogView;
    private AlertDialog currentDialog;
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;

//    private TextView location_title;
//    private TextView location_msg;

    @Override
    public void onCreate(Bundle savedInstanceState) {


        boolean isDark = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);

        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
// Initialize default values from preferences XML
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Now you can safely access the shared preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //showGyroModeDialog();
        checkAndShowControlModeDialog();



        boolean isDarkTheme = prefs.getBoolean("dark_theme", false);
        applyThemeToDialogsAndViews(isDarkTheme);

        wifiIcon = findViewById(R.id.linkQuality_icon);
        //        LayoutInflater inflater = getLayoutInflater();
        //        View dialogView = inflater.inflate(R.layout.dialogue_location_access, null);
        //// Now safely access the title TextView from the dialog layout
        //        location_title = dialogView.findViewById(R.id.title);
        //        location_msg = dialogView.findViewById(R.id.message_text);
        //  mHudArrow = findViewById(R.id.hud_arrow);
        //        new Handler().postDelayed(() -> {
        //            checkMobileDataSetting();
        //        }, 1000); // Delay = 1000 ms (1 second)

        mPresenter = new MainPresenter(this);
        setDefaultPreferenceValues();

        ////******************************************** BATTERY START  ****************************************************///

        mBatteryIcon = findViewById(R.id.battery_icon);
        // mTextView_battery = (TextView) findViewById(R.id.battery_text);
        // mTextView_linkQuality = (TextView) findViewById(R.id.linkQuality_text);

        setBatteryLevel(-1.0f);
        //  setLinkQualityText("N/A");

        ////******************************************** BATTERY END  ****************************************************///
        ImageButton takeoffButton = findViewById(R.id.toggle_takeoff_land);

        takeoffButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false; // Let click event pass through
        });

        ////******************************************** SETTINGS & THEME START ****************************************************///

        mBackgroundImage = findViewById(R.id.backgroundImage);

        mTextViewDisplayName = findViewById(R.id.textViewDisplayName);
        ConstraintLayout rootLayout = findViewById(R.id.rootLayout);
        settingsButton = findViewById(R.id.imageButton_settings);
// record_button = findViewById(R.id.btn_record_toggle);
        //  record_button_stop = findViewById(R.id.btn_record_toggle);
        themeSwitch = findViewById(R.id.themeSwitch);
        // boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("is_dark_theme", false);
        settingsButton.setImageResource(isDarkTheme ? R.drawable.settings_light : R.drawable.settings_dark);
       // record_button.setImageResource(isDarkTheme ? R.drawable.record_start_light : R.drawable.record_start_dark);
        themeSwitch.setChecked(isDarkTheme);
        //mHudArrow = findViewById(R.id.hud_arrow);


        applyFullThemeState(isDarkTheme);


        // Listen for switch toggle
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            applyThemeToDialogsAndViews(isChecked);
            if (isChecked) {
                rootLayout.setBackgroundResource(R.drawable.default_dark);
                settingsButton.setImageResource(R.drawable.settings_light);
               // record_button.setImageResource(R.drawable.record_start_light);
                mTextViewDisplayName.setTextColor(Color.WHITE);
                wifiIcon.setImageResource(R.drawable.wifi4_light);
                // mHudArrow.setImageResource(R.drawable.hud_arrow_dark);
//                location_title.setTextColor(Color.parseColor("#000000")); // Black
//                location_msg.setTextColor(Color.WHITE);


            } else {
                rootLayout.setBackgroundResource(R.drawable.default_light);
               // record_button.setImageResource(R.drawable.record_start_dark);
                settingsButton.setImageResource(R.drawable.settings_dark);
                mTextViewDisplayName.setTextColor(Color.BLACK);
                wifiIcon.setImageResource(R.drawable.wifi4_dark);
//                location_title.setTextColor(Color.BLACK);
//                location_msg.setTextColor(Color.BLACK);
                // mHudArrow.setImageResource(R.drawable.hud_arrow_light);
            }

            // Save user preference
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putBoolean("is_dark_theme", isChecked);
            editor.apply();
            applyThemeBackground();

            // Update connect/disconnect button icon immediately after theme change
            boolean isConnected = false;
            if (mPresenter != null && mPresenter.getCrazyflie() != null) {
                isConnected = mPresenter.getCrazyflie().isConnected();
            }
            updateConnectButtonIcon(isConnected, isChecked);

            ////******************************************** BATTERY START ****************************************************///

            if (mBatteryIcon != null) {
                int batteryPercent = lastKnownBatteryPercentage; // Make sure you store this globally

                if (batteryPercent >= 90) {
                    mBatteryIcon.setImageResource(isChecked ?
                            R.drawable.battery_100_light : R.drawable.battery_100_dark);
                } else if (batteryPercent >= 70) {
                    mBatteryIcon.setImageResource(isChecked ?
                            R.drawable.battery_75_light : R.drawable.battery_75_dark);
                } else if (batteryPercent >= 50) {
                    mBatteryIcon.setImageResource(isChecked ?
                            R.drawable.battery_50_light : R.drawable.battery_50_dark);
                } else if (batteryPercent >= 25) {
                    mBatteryIcon.setImageResource(isChecked ?
                            R.drawable.battery_25_light : R.drawable.battery_25_dark);
                } else {
                    mBatteryIcon.setImageResource(isChecked ?
                            R.drawable.battery_0_light : R.drawable.battery_0_dark);
                }
            }
            //recreate();
            ////******************************************** BATTERY END ****************************************************///
        });

        ////******************************************** SETTINGS & THEME END ****************************************************///


        // SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String selectedTheme = prefs.getString("pref_theme", "default");
        mControls = new Controls(this, mPreferences);
        mControls.setDefaultPreferenceValues(getResources());


        ////******************************************** JOYSTICK CONTROL START ****************************************************///

        mJoystickViewLeft = (JoystickView) findViewById(R.id.joystick_left);
        mJoystickViewRight = (JoystickView) findViewById(R.id.joystick_right);
        mJoystickViewRight.setLeft(false);
        mController = new TouchController(mControls, this, mJoystickViewLeft, mJoystickViewRight);


        //initialize gamepad controller
        mGamepadController = new GamepadController(mControls, this, mPreferences);
        mGamepadController.setDefaultPreferenceValues(getResources());

        ////******************************************** JOYSTICK CONTROL END ****************************************************///

        ////******************************************** CONNECT/DISCONNECT START ****************************************************///

        //initialize buttons
        //mToggleConnectButton = findViewById(R.id.imageButton_connect);
        mToggleConnectButton = (ImageButton) findViewById(R.id.imageButton_connect);
        initializeMenuButtons();

        ////******************************************** CONNECT/DISCONNECT END ****************************************************///

        ////******************************************** YAWLOCK BUTTON START ****************************************************///

        mJoystickLeftHLock = findViewById(R.id.joystick_left_hlock);
        mJoystickLeftHLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean targetState = !mJoystickViewLeft.isHorizontalLocked();
                mJoystickViewLeft.setHorizontalLocked(targetState);
                mJoystickLeftHLock.setBackgroundResource(targetState ? R.drawable.custom_button :
                        R.drawable.custom_button_seledted);
            }
        });
        // Yawlock button UI background colour change code

        mJoystickLeftHLock.setOnClickListener(v -> {
            boolean isLocked = !mJoystickViewLeft.isHorizontalLocked();
            mJoystickViewLeft.setHorizontalLocked(isLocked);
            mJoystickLeftHLock.setSelected(isLocked); // for background selector
        });

        ////******************************************** YAWLOCK BUTTON END ****************************************************///

        ////******************************************** TEXT to SPEECH START ****************************************************///

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.US); // or Locale.UK, Locale.ENGLISH
                }
            }
        });

        ////******************************************** TEXT to SPEECH END ****************************************************///


        ////******************************************** ALTHOLD START ****************************************************///

        Button btnSports = findViewById(R.id.toggle_althold);

        btnSports.setOnClickListener(v -> {
            boolean isSelected = btnSports.isSelected();
            btnSports.setSelected(!isSelected);
            if (isSelected) {
                // Previously in Sports mode, now back to AltHold mode
                showTouchModeDialog();  // ✅ Show your informative dialog
            }
        });

        ////******************************************** ALTHOLD END ****************************************************///

        ////******************************************** USB RECIEVER START ****************************************************///

        IntentFilter filter = new IntentFilter();
        filter.addAction(this.getPackageName() + ".USB_PERMISSION");
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        ////******************************************** USB RECIEVER END ****************************************************///

        setCacheDir();

        ////******************************************** CUSTOM SWITCH START ****************************************************///

        SwitchCompat customSwitch = findViewById(R.id.customSwitch);
        customSwitch.getThumbDrawable().setTint(Color.WHITE);
        customSwitch.setThumbResource(R.drawable.ellipse_22);
        customSwitch.setTrackResource(R.drawable.custom_switch_track);
        customSwitch.setSwitchMinWidth(60);
        customSwitch.setSwitchPadding(10);
        // Set initial track drawable based on state
        updateSwitchTrack(customSwitch, customSwitch.isChecked());
        // Listener to change track when toggled
        customSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSwitchTrack(customSwitch, isChecked);
        });

        ////******************************************** CUSTOM SWITCH START ****************************************************///

    }

    ////******************************************** BATTERY START  ****************************************************///


    // @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
//            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
//            if (matches != null && !matches.isEmpty()) {
//                String spokenText = matches.get(0).toLowerCase(Locale.ROOT);
//             //   handleVoiceCommand(spokenText);
//            }
//        }
//    }

//    private void handleVoiceCommand(String command) {
//        switch (command) {
//            case "takeoff mon":
//                // Call the same function or logic you use for the takeoff button
//                //takeoff();
//                break;
//
//            case "land":
//              //  land();
//                break;
//
//            case "takeoff":
//                arm(true);
//                break;
//
//            case "disarm":
//                arm(false);
//                break;
//            case "Altitude hold mode":
//               // althold_1();
//                break;
//            case "Sports Mode":
//               // althold_1();
//                break;
//            default:
//                Toast.makeText(this, "Unknown command: " + command, Toast.LENGTH_SHORT).show();
//        }
//    }

    public void setBatteryLevel(float battery) {

//        if (!isDroneConnected) {
//            boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
//                    .getBoolean("is_dark_theme", false);
//            mBatteryIcon.setImageResource(isDarkTheme ?
//                    R.drawable.battery_0_light : R.drawable.battery_0_dark);
//            Log.d("BATTERY", "Drone disconnected, skipping battery update.");
//            return;
//        }
        // Define min and max voltage as variables
        float minVoltage = 3.2f; // Voltage for 0%
        float maxVoltage = 4.2f; // Voltage for 100%

        // Calculate the voltage range
        float voltageRange = maxVoltage - minVoltage;

        // Normalize battery voltage to a 0-1 range
        float normalizedBattery = (battery - minVoltage) / voltageRange;

        // Convert to percentage
        int batteryPercentage = (int) (normalizedBattery * 100);

        // Clamp values to ensure within 0-100%
        if (battery == -1f) {
            batteryPercentage = 0;
        } else if (batteryPercentage < 0) {
            batteryPercentage = 0;
        } else if (batteryPercentage >= 100) {
            batteryPercentage = 100;
        }

        // Store last known percentage
        lastKnownBatteryPercentage = batteryPercentage;

        final int fBatteryPercentage = batteryPercentage;



        if (!isDroneConnected) {
            // Force battery icon to 0 when disconnected
            runOnUiThread(() -> {
                boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("is_dark_theme", false);
                mBatteryIcon.setImageResource(isDarkTheme ?
                        R.drawable.battery_0_light : R.drawable.battery_0_dark);
                wifiIcon.setImageResource(isDarkTheme ?
                        R.drawable.wifi4_light : R.drawable.wifi4_dark);
//                mBackgroundImage.setImageResource(isDarkTheme ?
//                        R.drawable.default_light : R.drawable.default_dark);
                // mTextView_battery.setText("Battery: 0%");
            });
            return;
        }

        // Update UI
        runOnUiThread(() -> {
            //mTextView_battery.setText(format(R.string.battery_text, fBatteryPercentage));
            //Log.d("1_battery_2","Voltage --->  "+ battery );
            Log.d("1_battery_2", "Decimal --->  " + normalizedBattery);
            Log.d("1_battery_2", "   Percent --->  " + fBatteryPercentage);
            boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("is_dark_theme", false);
//                if (fBatteryPercentage > 80) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_100_light : R.drawable.battery_100_dark);
//                } else if (fBatteryPercentage >= 60 && fBatteryPercentage < 80) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_80_light : R.drawable.battery_80_dark);
//                } else if (fBatteryPercentage >= 40 && fBatteryPercentage < 60) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_60_light : R.drawable.battery_60_dark);
//                } else if (fBatteryPercentage >= 30 && fBatteryPercentage < 40) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_40_light : R.drawable.battery_40_dark);
//                } else if (fBatteryPercentage >= 20 && fBatteryPercentage <= 30) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_20_light : R.drawable.battery_20_dark);
//                } else if (fBatteryPercentage < 20) {
//                    mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_20_light : R.drawable.battery_20_dark);
//                }

            if (fBatteryPercentage > 80) {
                mBatteryIcon.setImageResource(isDarkTheme ?
                        R.drawable.battery_100_light : R.drawable.battery_100_dark);
            } else if (fBatteryPercentage > 60 && fBatteryPercentage < 80) {
                mBatteryIcon.setImageResource(isDarkTheme ?
                        R.drawable.battery_half_light : R.drawable.battery_half_dark);
            } else if (fBatteryPercentage < 60) {
                mBatteryIcon.setImageResource(isDarkTheme ?
                        R.drawable.battery_low_light : R.drawable.battery_low_dark);
            }


//            if (fBatteryPercentage >= 90) {
//                mBatteryIcon.setImageResource(isDarkTheme ?
//                        R.drawable.battery_100_light : R.drawable.battery_100_dark);
//            } else if (fBatteryPercentage >= 70) {
//                mBatteryIcon.setImageResource(isDarkTheme ?
//                        R.drawable.battery_75_light : R.drawable.battery_75_dark);
//            } else if (fBatteryPercentage >= 50) {
//                mBatteryIcon.setImageResource(isDarkTheme ?
//                        R.drawable.battery_50_light : R.drawable.battery_50_dark);
//            } else if (fBatteryPercentage >= 25) {
//                mBatteryIcon.setImageResource(isDarkTheme ?
//                        R.drawable.battery_25_light : R.drawable.battery_25_dark);
//            } else {
//                mBatteryIcon.setImageResource(isDarkTheme ?
//                        R.drawable.battery_0_light : R.drawable.battery_0_dark);
//            }
        });
//        }
//        else{
//            boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
//                    .getBoolean("is_dark_theme", false);
//            mBatteryIcon.setImageResource(isDarkTheme ?
//                            R.drawable.battery_0_light : R.drawable.battery_0_dark);
//        }
    }
    public void setDroneConnected(boolean connected) {
        isDroneConnected = connected;
        runOnUiThread(() -> {
            // ✅ Show camera feed
            ImageView cameraFeed = findViewById(R.id.cameraImageView);
            if (cameraFeed != null) {
                cameraFeed.setVisibility(View.VISIBLE);
            }

            // ✅ Optional: Hide background (not needed if camera overlays it)
            // mBackgroundImage.setVisibility(View.GONE);

            // ✅ Update icons (battery/wifi) if needed
        });
    }
    public void setDroneDisconnected() {
        isDroneConnected = false;
        runOnUiThread(() -> {
            boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("is_dark_theme", false);
            // 1. Set background
            if (mBackgroundImage != null) {
                mBackgroundImage.setImageResource(isDarkTheme ?
                        R.drawable.default_dark : R.drawable.default_light);
            }

            // 2. Hide the camera feed so background becomes visible
            ImageView cameraFeed = findViewById(R.id.cameraImageView);
            if (cameraFeed != null) {
                cameraFeed.setVisibility(View.GONE);  // ✅ This reveals the background
            }
        });
    }

//            mBatteryIcon.setImageResource(isDarkTheme ?
//                    R.drawable.battery_0_light : R.drawable.battery_0_dark);
    ////******************************************** BATTERY END  ****************************************************///

    ////******************************************** CONNECT BUTTON START  ****************************************************///

    private void updateConnectButtonIcon(boolean isConnected, boolean isDarkTheme) {
        if (mToggleConnectButton == null) return;

        if (isConnected && isDarkTheme) {
            mToggleConnectButton.setImageResource(R.drawable.connect_light); // ✅ white icon for dark bg
        } else if (isConnected && !isDarkTheme) {
            mToggleConnectButton.setImageResource(R.drawable.connect_dark); // ✅ black icon for white bg
        } else if (!isConnected && isDarkTheme) {
            mToggleConnectButton.setImageResource(R.drawable.disconnect_light); // ✅ white icon for dark bg
        } else {
            mToggleConnectButton.setImageResource(R.drawable.disconnect_dark); // ✅ black icon for white bg
        }
    }

    ////******************************************** CONNECT BUTTON END  ****************************************************///

    private void updateSwitchTrack(SwitchCompat switchCompat, boolean isChecked) {
        if (isChecked) {
            switchCompat.setTrackResource(R.drawable.arm_button); // ON state
        } else {
            switchCompat.setTrackResource(R.drawable.disarm_button); // OFF state
        }
    }
    ////******************************************** HUD Control Start  ****************************************************///

//    public void updateHudArrowRotation(float roll) {
//        float maxTilt = 45f; // max visual tilt in degrees
//        float clampedRoll = Math.max(-255f, Math.min(255f, roll)); // clamp between -255 and 255
//        float rotationAngle = (clampedRoll / 255f) * maxTilt;
//
//        if (mHudArrow != null) {
//            mHudArrow.setRotation(rotationAngle);
//        }
//    }
//
//    public void moveHudArrow(float roll) {
//        float maxMoveDistance = 200f; // maximum movement left and right
//        float clampedRoll = Math.max(-255f, Math.min(255f, roll));
//        float translationX = (clampedRoll / 255f) * maxMoveDistance;
//
//        if (mHudArrow != null) {
//            mHudArrow.setTranslationX(translationX);
//        }
//    }
//    public void updateArrow(float roll, float pitch) {
//        if (mHudArrow == null) return;
//
//        float maxRollDegrees = 50.0f;   // left-right tilt for roll
//        float maxPitchDegrees = 50.0f;  // front-back tilt for pitch
//
//        float rollRotation = maxRollDegrees * roll;     // left-right tilt
//        float pitchTilt = maxPitchDegrees * pitch;      // front-back tilt
//
//        runOnUiThread(() -> {
//            mHudArrow.setRotation(rollRotation);         // horizontal rotation (roll)
//            mHudArrow.setRotationX(pitchTilt);           // vertical tilt (pitch)
//        });
//    }




    ////******************************************** HUD Control End  ****************************************************///

    private boolean isMobileDataEnabledSystemLevel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Class<?> cmClass = Class.forName(cm.getClass().getName());
            java.lang.reflect.Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(cm);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Mobile Data check via reflection failed", e);
            return false; // assume false if we can't determine
        }
    }
    private void checkAndShowControlModeDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isGyroMode = prefs.getBoolean("pref_use_gyro_bool", false);

        if (isGyroMode) {
            showGyroModeDialog();
        } else {
            showTouchModeDialog();  // 👈 new dialog for non-gyro mode
        }
    }

    private void setCacheDir() {
        if (isExternalStorageWriteable()) {
            Log.d(LOG_TAG, "External storage is writeable.");
            if (mCacheDir == null) {
                File appDir = getApplicationContext().getExternalFilesDir(null);
                mCacheDir = new File(appDir, "TOC_cache");
                mCacheDir.mkdirs();
            }
        } else {
            Log.d(LOG_TAG, "External storage is not writeable.");
            mCacheDir = null;
        }
    }

    private boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void setDefaultPreferenceValues() {
        // Set default preference values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Initialize preferences
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mRadioChannelDefaultValue = getString(R.string.preferences_radio_channel_defaultValue);
        mRadioDatarateDefaultValue = getString(R.string.preferences_radio_datarate_defaultValue);
    }

    private void checkScreenLock() {
        boolean isScreenLock = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_SCREEN_ROTATION_LOCK_BOOL, false);
        if (isScreenLock) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private void initializeMenuButtons() {
        // ✅ CONNECT/DISCONNECT BUTTON with mobile data check
        mToggleConnectButton.setOnClickListener(v -> {
            if (mPresenter != null && mPresenter.getCrazyflie() != null && mPresenter.getCrazyflie().isConnected()) {
                mPresenter.disconnect();
            } else {
                connectUDP(); // or Crazyradio
            }
        });

        mToggleConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mPresenter != null && mPresenter.getCrazyflie() != null && mPresenter.getCrazyflie().isConnected()) {
                        mPresenter.disconnect();
                        return;
                    }

                    // Step 1: Check Mobile Data
                    if (isMobileDataEnabledSystemLevel()) {
                        showMobileDataDialog(); // prompt user
                        retryMobileDataCheck = true; // set flag to retry after return
                        return;
                    }


                    // Step 2: Check Location/GPS
                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    boolean isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                    if (!isLocationEnabled) {
                        boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                                .getBoolean("is_dark_theme", false);

                        showLocationDialog(isDarkTheme); // ✅ Pass theme state to dialog // blocks further connection
                        return;
                    }

                    // Step 3: Proceed with connection
                    if (isCrazyradioAvailable(MainActivity.this)) {
                        connectCrazyradio();
                    } else {
                        connectBlePreChecks();
                    }

                } catch (IllegalStateException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });


        // ✅ SETTINGS BUTTON
        settingsButton = findViewById(R.id.imageButton_settings);
        boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("is_dark_theme", false);
        settingsButton.setImageResource(isDarkTheme ? R.drawable.settings_light : R.drawable.settings_dark);
        SwitchCompat themeSwitch = findViewById(R.id.themeSwitch);
        themeSwitch.setChecked(isDarkTheme);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isOpeningSettings = true;
                Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                startActivity(intent);
            }
        });
    }

    private void showMobileDataDialog() {
        boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialogue_data_access, null);

        // Set curved background dynamically
        int backgroundDrawable = isDarkTheme ?
                R.drawable.dialog_bg_dark : R.drawable.dialog_bg_light;
        dialogView.setBackgroundResource(backgroundDrawable);

        // UI Elements
        TextView title = dialogView.findViewById(R.id.title_data);
        TextView message = dialogView.findViewById(R.id.message_text_data);
        ImageView cancelBtn = dialogView.findViewById(R.id.ok_button);

        // Set text color based on theme
        int textColor = Color.parseColor(isDarkTheme ? "#FFFFFF" : "#000000");
        title.setTextColor(textColor);
        message.setTextColor(textColor);

        // Create and show dialog
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialogTheme)
                .setView(dialogView)
                .create();

        dialog.show();

        // Immersive flags and size
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            window.setLayout(
                    (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 450, getResources().getDisplayMetrics()
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Reapply immersive mode
        new Handler().postDelayed(this::setHideyBar, 100);

        // Cancel action
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
    }

    private void showLocationDialog(boolean isDarkTheme) {
        LayoutInflater inflater = LayoutInflater.from(this);
        currentDialogView = inflater.inflate(R.layout.dialogue_location_access, null);

// ✅ Set background based on theme
        int backgroundDrawable = isDarkTheme ?
                R.drawable.dialog_bg_dark : R.drawable.dialog_bg_light;

        currentDialogView.setBackgroundResource(backgroundDrawable);


        View dialogView = currentDialogView;


        TextView title = dialogView.findViewById(R.id.title);
        TextView message = dialogView.findViewById(R.id.message_text);
        ImageView cancelBtn = dialogView.findViewById(R.id.cancel_button);
        ImageView settingsBtn = dialogView.findViewById(R.id.settings_button);

        // Set dynamic text color (or use from colors.xml if preferred)
        int textColor = Color.parseColor(isDarkTheme ? "#FFFFFF" : "#000000");
        title.setTextColor(textColor);
        message.setTextColor(textColor);

        // Optional: set different icons based on theme if needed
        // (Only if you're not using drawable/drawable-night for automatic switching)
    /*
    cancelBtn.setImageResource(isDarkTheme ? R.drawable.ic_cancel_dark : R.drawable.ic_cancel_light);
    settingsBtn.setImageResource(isDarkTheme ? R.drawable.ic_gotosettings_dark : R.drawable.ic_gotosettings_light);
    */

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialogTheme)
                .setView(dialogView)
                .create();

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

// Reapply app-wide immersive mode as backup
        new Handler().postDelayed(() -> setHideyBar(), 100);
        // Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 450, getResources().getDisplayMetrics()
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Set button actions
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        settingsBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        });
    }
    private void showTouchModeDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDarkTheme = prefs.getBoolean("is_dark_theme", false);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialogue_flat_popup, null);

        int bgDrawable = isDarkTheme ? R.drawable.dialog_bg_dark : R.drawable.dialog_bg_light;
        dialogView.setBackgroundResource(bgDrawable);

        int textColor = Color.parseColor(isDarkTheme ? "#FFFFFF" : "#000000");

        TextView title = dialogView.findViewById(R.id.title_2);
        TextView message = dialogView.findViewById(R.id.message_text_2);
        ImageView okButton = dialogView.findViewById(R.id.ok_button);

//        title.setText("Pre - caution");
//        message.setText("Keep the drone on flat surface");
        title.setTextColor(textColor);
        message.setTextColor(textColor);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialogTheme)
                .setView(dialogView)
                .create();

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setLayout(
                    (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 450, getResources().getDisplayMetrics()
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        okButton.setOnClickListener(v -> dialog.dismiss());
    }
    public void updateTakeoffButtonToTakeoff() {
        if (toggleTakeOffLand != null) {
            toggleTakeOffLand.setImageResource(R.drawable.ic_takeoff);
            toggleTakeOffLand.setTag(R.drawable.ic_takeoff);
        }
    }

    private void showGyroModeDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDarkTheme = prefs.getBoolean("is_dark_theme", false);
        boolean isGyroMode = prefs.getBoolean("pref_use_gyro_bool", false);

        if (!isGyroMode) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialogue_gyro_mode, null);

        // Set background based on theme
        int bgDrawable = isDarkTheme ? R.drawable.dialog_bg_dark : R.drawable.dialog_bg_light;
        dialogView.setBackgroundResource(bgDrawable);

        // Set text colors
        int textColor = Color.parseColor(isDarkTheme ? "#FFFFFF" : "#000000");
        TextView title = dialogView.findViewById(R.id.title);
        TextView message = dialogView.findViewById(R.id.message_text);
        title.setTextColor(textColor);
        message.setTextColor(textColor);

        ImageView okButton = dialogView.findViewById(R.id.ok_button);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialogTheme)
                .setView(dialogView)
                .create();

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setLayout(
                    (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 450, getResources().getDisplayMetrics()
                    ),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Dismiss dialog on click
        okButton.setOnClickListener(v -> dialog.dismiss());
    }

    //applyThemeToDialogsAndViews
    private void applyThemeToDialogsAndViews(boolean isDarkTheme) {
        if (currentDialogView == null) return;

        int bgColor = isDarkTheme ? Color.parseColor("#1F1F1F") : Color.WHITE;
        currentDialogView.setBackgroundColor(bgColor);
    }




    public boolean isMobileDataCurrentlyActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    if (capabilities != null) {
                        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    }
                }
            } else {
                // For older APIs, use getActiveNetworkInfo()
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }



    private void updateConnectButtonState() {
        boolean mobileDataOn = isMobileDataCurrentlyActive(); // <-- strong check
        boolean shouldBlock = mobileDataOn;

//        if (mToggleConnectButton != null) {
//            mToggleConnectButton.setEnabled(!shouldBlock);
//            mToggleConnectButton.setAlpha(shouldBlock ? 0.5f : 1.0f); // grey out button
//        }
//        if (shouldBlock) {
//            showMobileDataDialog();  // Show dialog as long as it's ON
//        }
    }



    //<-------- Different themes --------->
    private void applyThemeBackground() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String selectedTheme = prefs.getString("pref_theme", "default");
        boolean isDarkMode = prefs.getBoolean("is_dark_theme", false);

        String imageName;
        // XML Backgrounds
        if ("default".equals(selectedTheme)) {
            imageName = isDarkMode ? "default_dark" : "default_light";
        } else {
            imageName = selectedTheme + "_" + (isDarkMode ? "dark" : "light");
        }

        int drawableId = getResources().getIdentifier(imageName, "drawable", getPackageName());

        if (drawableId != 0 && mBackgroundImage != null) {
            mBackgroundImage.setImageResource(drawableId);
        }

    }

    private void applyFullThemeState(boolean isDarkTheme) {
        ConstraintLayout rootLayout = findViewById(R.id.rootLayout);
        if (isDarkTheme) {
            rootLayout.setBackgroundResource(R.drawable.default_dark);
            settingsButton.setImageResource(R.drawable.settings_light);
            mTextViewDisplayName.setTextColor(Color.WHITE);
            // mHudArrow.setImageResource(R.drawable.hud_arrow_dark);
        } else {
            rootLayout.setBackgroundResource(R.drawable.default_light);
            settingsButton.setImageResource(R.drawable.settings_dark);
            mTextViewDisplayName.setTextColor(Color.BLACK);
            //mHudArrow.setImageResource(R.drawable.hud_arrow_light);
        }

        applyThemeBackground(); // optional background image logic
    }


    private void connectCrazyradio() {
        int radioChannel = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, mRadioChannelDefaultValue));
        int radioDatarate = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, mRadioDatarateDefaultValue));
        mPresenter.connectCrazyradio(radioChannel, radioDatarate, mCacheDir);
    }



    private void connectBlePreChecks() {

        // Check if Bluetooth LE is supported by the Android version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.e(LOG_TAG, Build.VERSION.SDK_INT + "does not support Bluetooth LE.");
            Toast.makeText(this, Build.VERSION.SDK_INT + "does not support Bluetooth LE. Please use a Crazyradio to connect to the Crazyflie instead.", Toast.LENGTH_LONG).show();
            return;
        }
        // Check if Bluetooth LE is supported by the hardware
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(LOG_TAG, "Device does not support Bluetooth LE.");
            Toast.makeText(this, "Device does not support Bluetooth LE. Please use a Crazyradio to connect to the Crazyflie instead.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isMobileDataCurrentlyActive()) {
                Log.e(LOG_TAG, "Mobile data is ON. Must be turned OFF for drone connection.");
                showMobileDataDialog(); // Prompt user to disable it
                return;
            }
        }

        // Since Android version 6, ACCESS_COARSE_LOCATION is required for Bluetooth scanning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.e(LOG_TAG, "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.");
            requestPermissions(Manifest.permission.ACCESS_FINE_LOCATION, MY_PERMISSIONS_REQUEST_LOCATION);
        } else {
            connect();
        }
    }

    private void connect() {
        connectUDP();
    }

    private void connectUDP() {
        mPresenter.connectUDP(mCacheDir);
    }



    private void checkLocationSettings() {
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isEnabled = service.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!isEnabled) {
            // Get theme preference
            boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("is_dark_theme", false);

            // Show your custom themed dialog
            showLocationDialog(isDarkTheme);
        } else {
            connect(); // ✅ Proceed if GPS is already enabled
        }
    }


    private void requestPermissions(String permission, int request) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted. Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, permission)) {
                // Show an explanation to the user *asynchronously* -- don't block this thread waiting for the user's response!
                // After the user sees the explanation, try again to request the permission.
                Log.d(LOG_TAG, "ACCESS_COARSE_LOCATION permission request has been denied.");
                //Toast.makeText(this,  "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, request);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, request);
            }
        } else {
            // Permission has already been granted
            checkLocationSettings();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the contacts-related task you need to do.
                    checkLocationSettings();
                } else {
                    // permission denied, boo! Disable the functionality that depends on this permission.
                    Log.d(LOG_TAG, "ACCESS_COARSE_LOCATION permission request has been denied.");
                    Toast.makeText(this, "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
//        float rollTrim = Float.parseFloat(prefs.getString(PreferencesActivity.KEY_PREF_ROLLTRIM, "0"));
//        //float pitchTrim = Float.parseFloat(prefs.getString(PreferencesActivity.KEY_PREF_PITCHTRIM, "0"));
//        mControls.setTrim(rollTrim);

      //  SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateConnectButtonState();
        //Themes
        boolean isConnected = mPresenter != null && mPresenter.getCrazyflie() != null && mPresenter.getCrazyflie().isConnected();
        boolean isDark = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);

        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        new Handler().postDelayed(() -> {
            if (retryMobileDataCheck) {
                retryMobileDataCheck = false;
                if (!isMobileDataCurrentlyActive()) {
                    retryMobileDataCheck = false;
                    updateConnectButtonState(); // re-enable connect button
                    // don't auto-connect, let user click again
                    showToastie("Mobile data turned OFF. You may now connect.");
                } else {
                    updateConnectButtonState(); // keep it disabled
                    showToastie("Mobile data is still ON. Please turn it OFF.");
                }

            }
        }, 1000);

//        SharedPreferences.Editor editor = prefs.edit();
//        if (!isConnected) {
//            editor.putBoolean("is_dark_theme", false);
//            editor.apply();
//        }

        boolean isDarkTheme = prefs.getBoolean("is_dark_theme", false); // read again after possible change

        // settings button theme
        settingsButton.setImageResource(isDarkTheme ? R.drawable.settings_light : R.drawable.settings_dark);

        //Connect & Disconnect
        updateConnectButtonIcon(isConnected, isDarkTheme);
        isOpeningSettings = false;
        applyFullThemeState(isDarkTheme);


        applyThemeBackground(); // simultaneous theme change
        if (themeSwitch != null) {
            themeSwitch.setChecked(isDarkTheme); // ✅ sync toggle to current theme
        }

        //  boolean isDark = PreferenceManager.getDefaultSharedPreferences(this)
        // .getBoolean("is_dark_theme", false);
        mTextViewDisplayName.setTextColor(isDark ? Color.WHITE : Color.BLACK);

        //TODO: improve
        PreferencesActivity.setDefaultJoystickSize(this);
        mJoystickViewLeft.setPreferences(mPreferences);
        mJoystickViewRight.setPreferences(mPreferences);
        mControls.setControlConfig();
        mGamepadController.setControlConfig();
        resetInputMethod();
        checkScreenLock();
        if (mPreferences.getBoolean(PreferencesActivity.KEY_PREF_IMMERSIVE_MODE_BOOL, false)) {
            setHideyBar();
        }
        mTextViewDisplayName = findViewById(R.id.textViewDisplayName); //Must be before setText()
        String userName = prefs.getString("pref_username", null);

        if (userName != null && !userName.isEmpty()) {
            mTextViewDisplayName.setText("Welcome, " + userName + "!");
            speak(this,"Welcome"+userName);
        } else {
            mTextViewDisplayName.setText("Welcome!");
        }
        //ensure connect button sync theme
        isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);
        isConnected = false;
        if (mPresenter != null && mPresenter.getCrazyflie() != null) {
            isConnected = mPresenter.getCrazyflie().isConnected();
        }
        updateConnectButtonIcon(isConnected, isDarkTheme);

    }


    @Override
    protected void onRestart() {
        super.onRestart();
        //showGyroModeDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause()");

        /// Disconnect issue
        if (mControls != null) {
            mControls.resetAxisValues();
        }
        if (mController != null) {
            mController.disable();
        }
        if (!isOpeningSettings) {
            mPresenter.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");
        unregisterReceiver(mUsbReceiver);
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }

        mPresenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                mDoubleBackToExitPressedOnce = false;

            }
        }, 2000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mPreferences.getBoolean(PreferencesActivity.KEY_PREF_IMMERSIVE_MODE_BOOL, false)) {
            setHideyBar(); // 👈 good: no delay
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setHideyBar() {
        Log.i(LOG_TAG, "Activating immersive mode");
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        if (Build.VERSION.SDK_INT >= 14) {
            newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a joystick since a generic motion event could be almost anything.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && event.getAction() == MotionEvent.ACTION_MOVE && mController instanceof GamepadController) {
            mGamepadController.dealWithMotionEvent(event);
            //updateFlightData();
            return true;
        } else {
            return super.dispatchGenericMotionEvent(event);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // do not call super if key event comes from a gamepad, otherwise the buttons can quit the app
        if (isJoystickButton(event.getKeyCode()) && mController instanceof GamepadController) {
            mGamepadController.dealWithKeyEvent(event);
            // exception for OUYA controllers
            if (!Build.MODEL.toUpperCase(Locale.getDefault()).contains("OUYA")) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isJoystickButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return KeyEvent.isGamepadButton(keyCode);
        }
    }

    private void resetInputMethod() {
        mController.disable();
        //updateFlightData();
        switch (mControls.getControllerType()) {
            case 0:
                // Use GyroscopeController if activated in the preferences
                if (mControls.isUseGyro()) {
                    mController = new GyroscopeController(mControls, this, mJoystickViewLeft, mJoystickViewRight);
                } else {
                    // TODO: reuse existing touch controller?
                    mController = new TouchController(mControls, this, mJoystickViewLeft, mJoystickViewRight);
                }
                break;
            case 1:
                // TODO: show warning if no game pad is found?
                mController = mGamepadController;
                break;
            default:
                break;

        }
        mController.enable();
        Toast.makeText(this, "Using " + mController.getControllerName(), Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "mUsbReceiver action: " + action);
            if ((MainActivity.this.getPackageName() + ".USB_PERMISSION").equals(action)) {
                //reached only when USB permission on physical connect was canceled and "Connect" or "Radio Scan" is clicked
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(LOG_TAG, "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio detached");
                    Toast.makeText(MainActivity.this, "Crazyradio detached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundDisconnect);
                    if (mPresenter != null && mPresenter.getCrazyflie() != null) {
                        Log.d(LOG_TAG, "linkDisconnect()");
                        mPresenter.disconnect();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio attached");
                    Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundConnect);
                }
            }
        }
    };

    private void playSound(int sound) {
        if (mLoaded) {
            float volume = 1.0f;
            mSoundPool.play(sound, volume, volume, 1, 0, 1f);
        }
    }

    public MainPresenter getPresenter() {
        return mPresenter;
    }

    public IController getController() {
        return mController;
    }

    public Controls getControls() {
        return mControls;
    }

    public static boolean isCrazyradioAvailable(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new IllegalArgumentException("UsbManager == null!");
        }
        List<UsbDevice> usbDeviceList = UsbLinkAndroid.findUsbDevices(usbManager, (short) Crazyradio.CRADIO_VID, (short) Crazyradio.CRADIO_PID);
        return !usbDeviceList.isEmpty();
    }

//    public void setLinkQualityText(final String quality) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mTextView_linkQuality.setText(format(R.string.linkQuality_text, quality));
//            }
//        });
//    }

    private String format(int identifier, Object o) {
        return String.format(getResources().getString(identifier), o);
    }

    public void showToastie(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setConnectionButtonConnected() {
        //setConnectionButtonBackground(R.drawable.custom_button_connected);

        //this is to update the icon
        boolean isDark = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);
        updateConnectButtonIcon(true, isDark); // ✅ Connected = true
    }

    public void setConnectionButtonConnectedBle() {
        setConnectionButtonBackground(R.drawable.custom_button_connected_ble);
    }

    public void setConnectionButtonDisconnected() {
        // setConnectionButtonBackground(R.drawable.custom_button);
        boolean isDark = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);
        updateConnectButtonIcon(false, isDark);
    }

    public void setConnectionButtonBackground(final int drawable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //  mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(drawable));
            }
        });
    }

    public void disableButtonsAndResetBatteryLevel() {
        setBatteryLevel(-1.0f);
    }
    public void setRSSILevel(int rssi) {
        boolean isDarkTheme = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("is_dark_theme", false);
        ImageView wifiIcon = findViewById(R.id.linkQuality_icon);
//        mBatteryIcon.setImageResource(isDarkTheme ?
//                R.drawable.battery_100_light : R.drawable.battery_100_dark);
        if (rssi >= -40) {
            wifiIcon.setImageResource(isDarkTheme ?
                    R.drawable.wifi4_light : R.drawable.wifi4_dark); //good
        } else if (rssi >= -60) {
            wifiIcon.setImageResource(isDarkTheme ?
                    R.drawable.wifi3_light : R.drawable.wifi3_dark); //good
        } else if (rssi >= -70) {
            wifiIcon.setImageResource(isDarkTheme ?
                    R.drawable.wifi2_light : R.drawable.wifi2_dark); //good
        }
        else if (rssi >= -80) {
            wifiIcon.setImageResource(isDarkTheme ?
                    R.drawable.wifi1_light : R.drawable.wifi1_dark); //good
        }
//        else {
//            wifiIcon.setImageResource(R.drawable.wifi_weak);
//        }

        //  Log.d("ganesh", "📶 RSSI UI updated: " + rssi + " dBm");
    }



}




