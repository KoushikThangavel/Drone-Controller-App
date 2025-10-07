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

package se.bitcraze.crazyfliecontrol.controller;

import android.util.Log;

import se.bitcraze.crazyfliecontrol2.MainActivity;


/**
 * The AbstractController implements the basic methods of IController class
 *
 */
public abstract class AbstractController implements IController {

    protected Controls mControls;
    protected boolean mIsDisabled;
    protected MainActivity mActivity;

    protected static final int MAX_THRUST = 65000;
    protected static final float MIN_TARGET_HEIGHT = 0.1f; // 10cm
    protected static final float MAX_TARGET_HEIGHT = 1.0f; // 100cm
    public static final float INITIAL_TARGET_HEIGHT = 0.4f; // 40cm
    protected float targetHeight = INITIAL_TARGET_HEIGHT;

    public AbstractController(Controls controls, MainActivity activity) {
        mControls = controls;
        mActivity = activity;
    }

    public void enable() {
        mIsDisabled = false;
    }

    public void disable() {
        mIsDisabled = true;
    }

    public boolean isDisabled() {
        return mIsDisabled;
    }

    public String getControllerName() {
        return "unknown controller";
    }

//    public void updateFlightData() {
//        mActivity.updateFlightData();
//	}

    /*
     * Thrust value in percent (used in the UI)
     */
    public float getThrust() {
        float rawThrust = ((mControls.getMode() == 1 || mControls.getMode() == 3)
                ? mControls.getRightAnalog_Y()
                : mControls.getLeftAnalog_Y());
        Log.d("THRUST_log", "calculated thrust: " + rawThrust);
        if (Math.abs(rawThrust) > mControls.getDeadzone()) {
            return rawThrust * 100f; // From -100 to +100 ✅
        }
        return 0;
    }


    /*
     * Absolute thrust value (gets send to the Crazyflie)
     */
    @Override
    public float getThrustAbsolute() {
        float rawThrust = ((mControls.getMode() == 1 || mControls.getMode() == 3)
                ? mControls.getRightAnalog_Y()
                : mControls.getLeftAnalog_Y());
        float deadzone = 0.05f;

        Log.d("RAW_THRUST_DEBUG", "rawThrust = " + rawThrust);

        if (Math.abs(rawThrust) > deadzone) {
            // Clamp rawThrust to -1.0 to +1.0 to prevent over-scaling
            rawThrust = Math.max(-1.0f, Math.min(1.0f, rawThrust));

            float scaledThrust = rawThrust * 60000f;  // Proper scaling
            Log.d("THRUST_ABSOLUTE_FINAL", "Clamped Scaled Thrust = " + scaledThrust);
            return scaledThrust;
        }
        return 0;
    }

    public float getRoll() {
        float roll = (mControls.getMode() == 1 || mControls.getMode() == 2) ? mControls.getRightAnalog_X() : mControls.getLeftAnalog_X();


        Log.d("ROLL_TRIM","this is the roll "+roll);

        return ((roll * mControls.getDeadzone(roll)) + mControls.getRollTrim()) * mControls.getRollPitchFactor();
    }

//    public float getRoll() {
//        float raw = (mControls.getMode() == 1 || mControls.getMode() == 2)
//                ? mControls.getRightAnalog_X()
//                : mControls.getLeftAnalog_X();
//
//        // Scale -1.0 → -15, 0 → 0, +1.0 → +15
//        float normalized = raw * 15.0f;
//
//        return normalized;
//    }






    public float getPitch() {
        float pitch = (mControls.getMode() == 1 || mControls.getMode() == 3) ? mControls.getLeftAnalog_Y() : mControls.getRightAnalog_Y();
        return ((pitch * mControls.getDeadzone(pitch)) + mControls.getPitchTrim()) * mControls.getRollPitchFactor();
    }

    public float getYaw() {
        float yaw = 0;
        yaw = (mControls.getMode() == 1 || mControls.getMode() == 2) ? mControls.getLeftAnalog_X() : mControls.getRightAnalog_X();
        return yaw * mControls.getYawFactor() * mControls.getDeadzone(yaw);
    }

    /**
     * Return initial target height by default
     *
     * @return
     */
    public float getTargetHeight() {
        return targetHeight;
    }

    public void setTargetHeight(float th) {
        this.targetHeight = th;
    }

    /**
     * Disabled by default
     *
     * @return
     */
    public boolean isHover() {
        return false;
    }
}
