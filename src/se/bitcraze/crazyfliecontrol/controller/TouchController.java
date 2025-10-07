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

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

/**
 * The TouchController uses the on-screen joysticks to control the roll, pitch, yaw and thrust values.
 * The mapping of the axes can be changed with the "mode" setting in the preferences.
 *
 * For example, mode 3 (default) maps roll to the left X-Axis, pitch to the left Y-Axis,
 * yaw to the right X-Axis and thrust to the right Y-Axis.
 *
 */
public class TouchController extends AbstractController {

    protected int mMovementRange = 1000;  // "resolution"

    protected JoystickView mJoystickViewLeft;
    protected JoystickView mJoystickViewRight;

    public TouchController(Controls controls, MainActivity activity, JoystickView joystickviewLeft, JoystickView joystickviewRight) {
        super(controls, activity);
        this.mJoystickViewLeft = joystickviewLeft;
        this.mJoystickViewRight = joystickviewRight;
        this.mJoystickViewLeft.setMovementRange(mMovementRange);
        this.mJoystickViewRight.setMovementRange(mMovementRange);
        updateAutoReturnMode();
    }
 
    private void updateAutoReturnMode() {
        this.mJoystickViewLeft.setAutoReturnMode(isLeftAnalogFullTravelThrust() ? JoystickView.AUTO_RETURN_BOTTOM : JoystickView.AUTO_RETURN_CENTER);
        this.mJoystickViewLeft.autoReturn(true);
        this.mJoystickViewRight.setAutoReturnMode(isRightAnalogFullTravelThrust() ? JoystickView.AUTO_RETURN_BOTTOM : JoystickView.AUTO_RETURN_CENTER);
        this.mJoystickViewRight.autoReturn(true);
    }

    @Override
    public void enable() {
        super.enable();
        this.mJoystickViewLeft.setOnJoystickMovedListener(_listenerLeft);
        this.mJoystickViewRight.setOnJoystickMovedListener(_listenerRight);
        updateAutoReturnMode();
    }

    @Override
    public void disable() {
        mControls.setRightAnalogY(0);
        mControls.setRightAnalogX(0);
        mControls.setLeftAnalogY(0);
        mControls.setLeftAnalogX(0);
        this.mJoystickViewLeft.setOnJoystickMovedListener(null);
        this.mJoystickViewRight.setOnJoystickMovedListener(null);
        super.disable();
    }

    public String getControllerName() {
        return "touch controller";
    }

    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

        @Override
        public void OnMoved(float pan, float tilt) {
            if (isRightAnalogFullTravelThrust()) {
                tilt = (tilt + 1.0f) / 2.0f;
            }
            mControls.setRightAnalogY(tilt);
            mControls.setRightAnalogX(pan);

//            ((MainActivity) mActivity).updateHudArrowRotation(pan * 255f);
//            ((MainActivity) mActivity).moveHudArrow(pan * 255f);
// 🔥 Move based on roll
        }

        @Override
        public void OnReleased() {
            mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
//            ((MainActivity) mActivity).updateHudArrowRotation(0f);
//            ((MainActivity) mActivity).moveHudArrow(0f);
        }

        public void OnReturnedToCenter() {
            mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
//            ((MainActivity) mActivity).updateHudArrowRotation(0f);
//            ((MainActivity) mActivity).moveHudArrow(0f);
        }
    };



    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {
        @Override
        public void OnMoved(float pan, float tilt) {
            float deadzone = 0.05f;
            float normalizedTilt = Math.abs(tilt) > deadzone ? tilt : 0f;  // -1.0 to 1.0
            float yaw = pan * 1.0f;  // keep yaw normalized too, -1.0 to +1.0

            Log.d("THRUST_DEBUG_NORMALIZED", "Normalized Thrust Input=" + normalizedTilt + ", Yaw=" + yaw);

            mControls.setLeftAnalogY(normalizedTilt);  // Pass normalized tilt
            mControls.setLeftAnalogX(yaw);
        }

        @Override
        public void OnReleased() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }

        public void OnReturnedToCenter() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }
    };



    public boolean isThrustRightAnalog() {
        return (mControls.getMode() == 1 || mControls.getMode() == 3);
    }

    public boolean isLeftAnalogFullTravelThrust() {
        return mControls.isTouchThrustFullTravel() && !isThrustRightAnalog();
    }

    public boolean isRightAnalogFullTravelThrust() {
        return mControls.isTouchThrustFullTravel() && isThrustRightAnalog();
    }

}
