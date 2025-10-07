package se.bitcraze.crazyfliecontrol2;

import static se.bitcraze.crazyfliecontrol2.EspActivity.speak;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

import androidx.lifecycle.Observer;

import com.espressif.espdrone.android.R;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import se.bitcraze.crazyflie.lib.crtp.CrtpDriver;
import se.bitcraze.crazyflie.lib.crtp.CrtpPacket;

public class EspUdpDriver extends CrtpDriver {
    private static final String TAG = "EspUdpDriver";

    private static final String TAG_S = "BUTTON-CLICK";

    static final int APP_PORT = 2399;
    static final int DEVICE_PORT = 2390;
    static final String DEVICE_ADDRESS = "192.168.43.42";

    private volatile boolean mConnectMark = false;
    private static volatile DatagramSocket mSocket;
    private volatile ReceiveThread mReceiveThread;
    private volatile PostThread mPostThread;

    private final EspActivity mActivity;
    private final BlockingQueue<CrtpPacket> mInQueue;
    private final WifiManager mWifiManager;

    // Correct placement of global flags inside EspUdpDriver
    private static volatile boolean wifiReady = false;
    private static volatile boolean socketReady = false;
    private static boolean mIsArmed;

    public static boolean mIsdis_Armed;
    private static boolean ack_althold;
    private static boolean ack_sports;
    private static boolean mIs_take_off;
    //private static boolean mIs_land;
    private static volatile boolean mIs_althold_ack = false;
    private static volatile boolean mIs_sports_ack = false;
    public static boolean isReadyToFly = false;
    private ImageView mBackgroundImage;

    public static volatile boolean mIs_land = false;


    // Public accessor for other classes
    public static boolean isDroneConnected() {
        return wifiReady && socketReady;
    }

    private final Observer<String> mObserver = new Observer<String>() {
        @Override
        public void onChanged(String s) {
            int networkId = mWifiManager.getConnectionInfo().getNetworkId();
            if (networkId == -1) {
                wifiReady = false;
                disconnect();
                notifyConnectionLost("No Wi-Fi connected");
            } else {
                // 🟢 Fetch SSID name
                String ssid = mWifiManager.getConnectionInfo().getSSID().replace("\"", "");
                Log.d(TAG, "Connected SSID: " + ssid);

                // 🛑 If SSID does not start with "ARIS_", disconnect immediately
//                if (!ssid.startsWith("ARIS")) {
//                    wifiReady = false;
//                    disconnect();
//                    notifyConnectionLost("Invalid network (" + ssid + "). Please connect to ARIS drone.");
//                    return;  // Stop further connection
//                }

                // ✅ If SSID is correct, proceed
                wifiReady = true;

                if (mConnectMark) {
                    mConnectMark = false;
                    try {
                        InetAddress deviceAddress = InetAddress.getByName(DEVICE_ADDRESS);
                        mSocket = new DatagramSocket(null);
                        mSocket.setReuseAddress(true);
                        mSocket.bind(new InetSocketAddress(APP_PORT));

                        // Binding the socket to correct Wi-Fi network (Android 6+)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            ConnectivityManager cm = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
                            for (Network network : cm.getAllNetworks()) {
                                NetworkInfo networkInfo = cm.getNetworkInfo(network);
                                if (networkInfo != null
                                        && networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                                        && networkInfo.isConnected()
                                        && networkInfo.getExtraInfo() != null
                                        && networkInfo.getExtraInfo().contains(ssid)) {
                                    try {
                                        network.bindSocket(mSocket);
                                        Log.d(TAG, "Socket bound to ARIS Wi-Fi: " + ssid);
                                    } catch (IOException e) {
                                        Log.e(TAG, "Failed to bind socket to Wi-Fi " + ssid, e);
                                    }
                                    break;
                                }
                            }
                        }

                        // Start receiving and sending threads
                        mReceiveThread = new ReceiveThread(mSocket, mActivity);
                        mReceiveThread.setPacketQueue(mInQueue);
                        mReceiveThread.start();

                        mPostThread = new PostThread(mSocket, deviceAddress);
                        mPostThread.start();

                        socketReady = true;
                        notifyConnected();

                    } catch (IOException e) {
                        if (mSocket != null) {
                            mSocket.close();
                            mSocket = null;
                        }
                        mActivity.removeBroadcastObserver(mObserver);
                        socketReady = false;
                        notifyConnectionFailed("Create socket failed");
                    }
                }
            }
        }
    };


    public static synchronized void resetTakeoffAck() {
        mIs_take_off = false;
    }


    public static synchronized void resetLandAck() {
        mIs_land = false;
    }

    public EspUdpDriver(EspActivity activity) {
        mActivity = activity;
        mWifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mInQueue = new LinkedBlockingQueue<>();

    }


    public static synchronized boolean ack_isArmed() {
        boolean currentState = mIsArmed;
        mIsArmed = false;  // Reset after read
        return currentState;
    }



    public static synchronized boolean ack_dis_Armed() {
        boolean currentState = mIsdis_Armed;
        mIsdis_Armed = false;  // Reset after read
        return currentState;
    }



    public static synchronized boolean ack_take_off() {
        boolean currentState = mIs_take_off;
        mIs_take_off = false;  // Reset after read
        return currentState;
    }



    public static synchronized boolean ack_land() {
        boolean currentState = mIs_land;
        mIs_land = false;  // Reset after read
        return currentState;
    }

    public static synchronized boolean ack_althold() {
        boolean current = mIs_althold_ack;
        mIs_althold_ack = false; // reset after reading
        return current;
    }

    public static synchronized boolean ack_sports() {
        boolean current = mIs_sports_ack;
        mIs_sports_ack = false; // reset after reading
        return current;
    }


    public void connect() throws IOException {
        Log.w(TAG, "Connect()");
        mVideoReceiveThread = new VideoReceiveThread(mActivity);
        mVideoReceiveThread.start();
       //  speak(mActivity, "Aeris Drone connected");

        if (mSocket != null) {
            throw new IllegalStateException("Connection already started");
        }

        mConnectMark = true;
        notifyConnectionRequested();
        mActivity.observeBroadcast(mActivity, mObserver);

        // 🔽 Send the UDP connect packet here
        new Thread(() -> {
            try {
                byte[] connect_pck = {(byte) 0x71, (byte) 0x13, (byte) 0x55, (byte) 0x00};

                String DRONE_IP = DEVICE_ADDRESS;
                int DRONE_PORT = DEVICE_PORT;

                InetAddress droneAddress = InetAddress.getByName(DRONE_IP);
                DatagramPacket packet = new DatagramPacket(connect_pck, connect_pck.length, droneAddress, DRONE_PORT);

                mSocket.send(packet); // ✅ FIXED

                Log.d(TAG_S, "✅ Connect packet sent successfully");

                mActivity.runOnUiThread(() -> {
                    mActivity.showAssistantMessage("Connect packet sent to drone.");
                });

            } catch (Exception e) {
                Log.e(TAG_S, "❌ Failed to send connect packet: " + e.getMessage());
                mActivity.runOnUiThread(() -> {
                    mActivity.showAssistantMessage("Failed to send connect packet.");
                });
            }
        }).start();

    }


    @Override
    public void disconnect() {
        speak(mActivity,"Aeris Drone disconnected");
        if (mVideoReceiveThread != null) {
            //mVideoReceiveThread.shutdown();  // safely stop the thread
            mVideoReceiveThread = null;
        }
        mActivity.runOnUiThread(() -> {
            if (mActivity instanceof MainActivity) {
                ((MainActivity) mActivity).setDroneDisconnected();
            }

//            ImageView cameraView = mActivity.findViewById(R.id.cameraImageView);
//            cameraView.setVisibility(View.GONE); // Hide video feed
//            View backgroundView = mActivity.findViewById(R.id.backgroundImage); // Replace with your actual background ID
//            if (backgroundView != null) {
//                backgroundView.setVisibility(View.VISIBLE); // Show background
//            }
        });


        mActivity.removeBroadcastObserver(mObserver);
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
            mReceiveThread.interrupt();
            mReceiveThread.setPacketQueue(null);
            mReceiveThread = null;
            mPostThread.interrupt();
            mPostThread = null;
            notifyDisconnected();
            ((MainActivity) mActivity).setBatteryLevel(-1.0f);

        }
    }

    @Override
    public boolean isConnected() {
        return mSocket != null && !mSocket.isClosed();
    }


    // ✅ Only here, at the EspUdpDriver level (not inside ReceiveThread)
    @Override
    public void notifyConnected() {
        super.notifyConnected();
        Log.d(TAG, "notifyConnected(): wifiReady=" + wifiReady + ", socketReady=" + socketReady);
    }

    @Override
    protected void notifyDisconnected() {
        super.notifyDisconnected();
        socketReady = false;
        Log.d(TAG, "notifyDisconnected(): wifiReady=" + wifiReady + ", socketReady=" + socketReady);
    }

    @Override
    protected void notifyConnectionLost(String reason) {
        super.notifyConnectionLost(reason);
        socketReady = false;
        Log.d(TAG, "notifyConnectionLost(): wifiReady=" + wifiReady + ", socketReady=" + socketReady);
    }

    @Override
    public void sendPacket(CrtpPacket packet) {
        if (mSocket == null || mPostThread == null) {
            return;
        }

        mPostThread.sendPacket(packet);
    }

    @Override
    public CrtpPacket receivePacket(int wait) {
        try {
            return mInQueue.poll(wait, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "ReceivePacket Interrupted");
            return null;
        }
    }

    public DatagramSocket getSocket() {
        return mSocket;
    }


    private static class PostThread extends Thread {
        private BlockingQueue<CrtpPacket> mmQueue = new LinkedBlockingQueue<>();
        private DatagramSocket mmSocket;
        private InetAddress mmDevAddress;

        PostThread(DatagramSocket socket, InetAddress devAddress) {
            mmSocket = socket;
            mmDevAddress = devAddress;
        }

        void sendPacket(CrtpPacket packet) {
            mmQueue.add(packet);
        }




        @Override
        public void run() {
            while (!mmSocket.isClosed() && !isInterrupted()) {
                try {
                    CrtpPacket packet = mmQueue.take();
                    byte[] data = packet.toByteArray();

                    // ✅ Debug control values
                    if (data.length >= 6) {
                        int roll = data[1];
                        int pitch = data[2];
                        int yaw = data[3];
                        int thrust = data[4] ;//& 0xFF; // 🧠 Read actual joystick thrust byte (0–255)

                        Log.d("THRUST_DEBUG_3", "🎮 Real Joystick → Thrust=" + thrust + ", Roll=" + roll + ", Pitch=" + pitch + ", Yaw=" + yaw);
                    }


                    // ✅ Log full serialized data
                    Log.d("THRUST_DEBUG", "Serialized Data: " + Arrays.toString(data));
                    byte[] buf = new byte[data.length + 1];
                    System.arraycopy(data, 0, buf, 0, data.length);
                    int checksum = 0;
                    for (byte b : data) {
                        checksum += (b & 0xff);
                    }
                    buf[buf.length - 1] = (byte) checksum;
                    Log.w(TAG, "run: PostData: " + Arrays.toString(buf));
                    DatagramPacket udpPacket = new DatagramPacket(buf, buf.length, mmDevAddress, DEVICE_PORT);
                    mmSocket.send(udpPacket);
                } catch (IOException e) {
                    Log.w(TAG, "sendPacket: IOException: " + e.getMessage());
                    mmSocket.close();
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }

            Log.d(TAG, "run: PostThread End");
        }
    }
    private volatile VideoReceiveThread mVideoReceiveThread;

    static class VideoReceiveThread extends Thread {
        private static final int VIDEO_PORT = 4210;
        private static final int MAX_PACKET_SIZE = 512;
        private static final int HEADER_SIZE = 6;
        private static final int TIMEOUT_MS = 2000;

        private volatile boolean running = true;
        private final EspActivity activity;
        private final Handler uiHandler = new Handler(Looper.getMainLooper());

        private final Map<Integer, FrameBuffer> frameMap = new HashMap<>();
        private int lastFrameDisplayed = -1;

        private Surface recorderSurface = null;

        public void setRecorderSurface(Surface surface) {
            this.recorderSurface = surface;
        }

        public void clearRecorderSurface() {
            this.recorderSurface = null;
        }

        VideoReceiveThread(EspActivity activity) {
            this.activity = activity;
        }

        void stopReceiver() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            DatagramSocket videoSocket = null;
            try {
                videoSocket = new DatagramSocket(null);
                videoSocket.setReuseAddress(true);
                videoSocket.bind(new InetSocketAddress(VIDEO_PORT));
                videoSocket.setSoTimeout(TIMEOUT_MS);

                byte[] buffer = new byte[MAX_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (running) {
                    try {
                        videoSocket.receive(packet);
                        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                        if (data.length < HEADER_SIZE) continue;

                        int frameID = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        int packetID = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                        int totalPackets = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);

                        byte[] chunk = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
                        FrameBuffer frameBuffer = frameMap.get(frameID);

                        if (frameBuffer == null) {
                            frameBuffer = new FrameBuffer(totalPackets);
                            frameMap.put(frameID, frameBuffer);
                        }

                        frameBuffer.addPacket(packetID, chunk);

                        if (frameBuffer.isComplete() && frameID != lastFrameDisplayed) {
                            byte[] jpgBytes = frameBuffer.assemble();
                            final Bitmap bmp = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgBytes.length);

                            if (bmp != null) {
                                Bitmap filtered = bmp;

                                int w = filtered.getWidth() & ~1;
                                int h = filtered.getHeight() & ~1;
                                if (filtered.getWidth() != w || filtered.getHeight() != h) {
                                    filtered = Bitmap.createScaledBitmap(filtered, w, h, false);
                                }

                                Bitmap finalFiltered = filtered;

                                // Update live feed UI
                                uiHandler.post(() -> activity.updateCameraFeedBitmap(finalFiltered));

                                // Push to MediaCodec recorder surface
                                if (activity.recorder != null) {
                                    activity.pushFrameToRecorder(finalFiltered);
                                }
                            } else {
                                Log.e("VideoReceiveThread", "Decoded bitmap is NULL!");
                            }

                            lastFrameDisplayed = frameID;

                            // Clean old frames
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                frameMap.entrySet().removeIf(entry -> entry.getKey() <= frameID - 3);
                            }
                        }

                    } catch (SocketTimeoutException e) {
                        // Timeout expected
                    } catch (Exception e) {
                        Log.e("VideoReceiveThread", "Error: " + e);
                    }
                }
            } catch (Exception e) {
                Log.e("VideoReceiveThread", "Socket error: " + e);
            } finally {
                if (videoSocket != null) videoSocket.close();
            }
        }
    }



    static class FrameBuffer {
        private final byte[][] packets;
        private final boolean[] received;
        private final int totalPackets;
        private int receivedCount = 0;

        public FrameBuffer(int totalPackets) {
            this.totalPackets = totalPackets;
            this.packets = new byte[totalPackets][];
            this.received = new boolean[totalPackets];
        }

        public void addPacket(int packetID, byte[] data) {
            if (packetID >= 0 && packetID < totalPackets && !received[packetID]) {
                packets[packetID] = data;
                received[packetID] = true;
                receivedCount++;
            }
        }

        public boolean isComplete() {
            return receivedCount == totalPackets;
        }

        public byte[] assemble() {
            int totalSize = 0;
            for (byte[] p : packets) {
                if (p != null) totalSize += p.length;
            }

            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] p : packets) {
                if (p != null) {
                    System.arraycopy(p, 0, result, offset, p.length);
                    offset += p.length;
                }
            }
            return result;
        }
    }






    // in this code all the data received from this code

    // port 2399
    // IP
    private static class ReceiveThread extends Thread {

        private DatagramSocket mmSocket;
        private BlockingQueue<CrtpPacket> mmQueue;
        private EspActivity mActivity;  // add a reference to your activity

        ReceiveThread(DatagramSocket socket, EspActivity activity) {
            mmSocket = socket;
            mActivity = activity;
        }
        void setPacketQueue(BlockingQueue<CrtpPacket> queue) {
            mmQueue = queue;
        }
        @Override
        public void run() {
            byte[] buf = new byte[1024];
            DatagramPacket udpPacket = new DatagramPacket(buf, buf.length);
            while (!mmSocket.isClosed() && !isInterrupted()) {
                try {
                    mmSocket.receive(udpPacket);

                    int len = udpPacket.getLength();
                    byte[] raw = Arrays.copyOf(udpPacket.getData(), len);

                    // RAW DATA PRINT

                    //Log.d("RAW","raw"+raw.toString());
                    //Log.d("HUDD", "ARM ACK: CD CC AC 41 86");

                    //ARM-ACK
                    byte[] expectedRaw_1 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x41, (byte) 0x86};

                    if (Arrays.equals(raw, expectedRaw_1)) {

                        mIsArmed = true;
                        // your logic here
                        Log.d(TAG_S, "ARM ACK: CD CC AC 41 86");

                        }

                    //DISARM-ACK
                    byte[] expectedRaw_2 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x42, (byte) 0x87};

                    if (Arrays.equals(raw, expectedRaw_2)) {
                        mIsdis_Armed = true;

                        Log.d(TAG_S, "Change takeoff");
                                mActivity.runOnUiThread(() -> {
                                    ((MainActivity) mActivity).updateTakeoffButtonToTakeoff();
                                });


                        Log.d(TAG_S, "DISARM ACK: CD CC AC 42 87 Hahaha");
                    }
//                    if(mIsdis_Armed == true)
//                    {
//                        mIsdis_Armed = false;
//                    }

                    //Take off -ACK
                    byte[] expectedRaw_3 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x43, (byte) 0x88};

                    if (Arrays.equals(raw, expectedRaw_3)) {

                        mIs_take_off = true;
                        Log.d(TAG_S, "Take off ACK: CD CC AC 43 88");

                    }

                    //Land -ACK

                    byte[] expectedRaw_4 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x44, (byte) 0x89};

                    if (Arrays.equals(raw, expectedRaw_4)) {
                        mIs_land = true;
                       Log.d(TAG_S, "LAND ACK RECEIVED: CD CC AC 44 89");
                    }




                    //       Battery -ACK

                    byte[] expectedRaw_5 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x45,(byte) 0x8A};

                    if (Arrays.equals(raw, expectedRaw_5)) {
                       // mIs_land = true;
                       // mActivity.showAssistantMessage("Battery Low");
                       // speak(mActivity,"Battery Low");
                        Log.d(TAG_S, "BATTERY ACK RECEIVED: CD CC AC 45 8A");
                    }

                    // Sensor failsafe ACK
                    byte[] expectedRaw_6 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x46,(byte) 0x8B};

                    if (Arrays.equals(raw, expectedRaw_6)) {
                        // mIs_land = true;
                        mActivity.showAssistantMessage("Sensor failed");
                        speak(mActivity,"Sensor failed");
                        Log.d(TAG_S, "SENSOR ACK RECEIVED: CD CC AC 46 8B");
                    }

                    // AltHold Ack
                    byte[] expectedRaw_7 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x47, (byte) 0x8C};
                    if (Arrays.equals(raw, expectedRaw_7)) {
                        mIs_althold_ack = true;
                        Log.d(TAG_S, "ALTHOLD ACK RECEIVED: CD CC AC 47 8C");
                    }

                    // Sports Ack
                    byte[] expectedRaw_8 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x48, (byte) 0x8D};
                    if (Arrays.equals(raw, expectedRaw_8)) {
                        mIs_sports_ack = true;
                        Log.d(TAG_S, "SPORTS ACK RECEIVED: CD CC AC 48 8D");
                    }


                    // Crash Ack
                    byte[] expectedRaw_9 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x49, (byte) 0x8E};
                    if (Arrays.equals(raw, expectedRaw_9)) {
                        speak(mActivity,"Crash Detected");
                        mActivity.showAssistantMessage("Reboot the Drone");
                        Log.d(TAG_S, "Crash Detected: CD CC AC 49 8E");
                    }

                     

//                    // Crash Ack
//                    byte[] expectedRaw_9 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x49, (byte) 0x8E};
//                    // At the class level (EspActivity or EspUdpDriver):
//                    boolean crashHandled = false;
//
//// In your packet receive logic:
//                    if (Arrays.equals(raw, crashAckBytes)) {
//                        if (!isCrashState) {
//                            isCrashState = true;
//                            // Disable controls
//                            runOnUiThread(() -> {
//                                disableAllControls();
//                                showAssistantMessage("Crash detected, waiting for Ready to Fly...");
//                            });
//                        }
//                    }
//
//// When Ready to Fly ACK is received
//                    if (Arrays.equals(raw, readyToFlyBytes)) {
//                        isCrashState = false;
//                        runOnUiThread(() -> {
//                            enableControls();
//                            showAssistantMessage("Ready to fly");
//                        });
//                    }

                    /// Gyro - Crashed
                    byte[] expectedRaw_10 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x50, (byte) 0x8F};
                    if (Arrays.equals(raw, expectedRaw_10)) {
                        speak(mActivity,"Gyro Crashed");
                        mActivity.showAssistantMessage("Gyro Crashed");
                        Log.d(TAG_S, "SPORTS ACK RECEIVED: CD CC AC 50 8F");
                    }

                    //Ready to fly
                    byte[] expectedRaw_11 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x51, (byte) 0x96};
                    if (Arrays.equals(raw, expectedRaw_11)) {
                        Log.d(TAG_S, "Ready to Fly: CD CC AC 51 90");
                        speak(mActivity,"Ready to fly");
                        mActivity.showAssistantMessage("Ready to fly");
                        EspUdpDriver.isReadyToFly = true;

                        if (mActivity instanceof MainActivity) {
                            ((MainActivity) mActivity).runOnUiThread(() -> {
                                ((MainActivity) mActivity).setDroneReady();
                            });
                        }
                    }


                    byte[] expectedRaw_12 = {(byte) 0xCD, (byte) 0xCC, (byte) 0xAC, (byte) 0x52, (byte) 0x97};
                    if (Arrays.equals(raw, expectedRaw_12)) {
                        speak(mActivity,"Landed Disarm the Drone");
                        mActivity.showAssistantMessage("Landed Disarm the Drone");
                        Log.d(TAG_S, "LAND DISARM: CD CC AC 52 97");
                    }

                    // Logging received bytes
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < len; i++) {
                        hex.append(String.format("%02X ", raw[i]));
                    }
                    Log.d("RAW_2", "Received bytes: " + hex.toString());

                    // here i just filter the wifi connection data
                    //0x21 0xFF 0xFF 0x02 0x21

                    // Check explicitly for battery packet type
//                    if (!(len == 5
//                            && (raw[0] & 0xFF) == 0x21
//                            && (raw[1] & 0xFF) == 0xFF
//                            && (raw[2] & 0xFF) == 0xFF
//                            && (raw[3] & 0xFF) == 0x02
//                            && (raw[4] & 0xFF) == 0x21)) {
//                        // Identified battery packet (by starting bytes '73 68')
//                        float voltage = ByteBuffer.wrap(raw, 0, 4)
//                                .order(ByteOrder.LITTLE_ENDIAN)
//                                .getFloat();
//                        Log.d("ganesh", String.format("✅ Battery Voltage = %.2f V", voltage));

                    if ((raw[0] & 0xFF) == 0xCE && raw.length >= 3) {
                        int rssi = (byte) raw[2];  // ✅ read actual RSSI byte

                     //   Log.d("ganesh", "📶 Corrected RSSI value = " + rssi + " dBm");

                        mActivity.runOnUiThread(() -> {
                            if (mActivity instanceof MainActivity) {
                                ((MainActivity) mActivity).setRSSILevel(rssi);
                            }
                        });
                    }
                    //Updated comments

                    if ((raw[0] & 0xFF) == 0xBE && len >= 6) {
                        // This packet starts with BE, process as battery voltage
                        // Use bytes [1] to [5] for conversion (5 bytes total)

                        // Assuming voltage is stored in bytes 1-4 (like float)
                        float voltage = ByteBuffer.wrap(raw, 1, 4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .getFloat();

                        Log.d("ganesh", String.format("🔋 Filtered Battery Voltage = %.2f V", voltage));

                        if (voltage== 0.00){
                            Log.d("ganesh","this waste data" +voltage);
                        }

                        else {
                            mActivity.runOnUiThread(() -> {
                                if (mActivity instanceof MainActivity) {
                                    MainActivity main = (MainActivity) mActivity;
                                    main.setDroneConnected(true);     // 🔄 Updates the real MainActivity flag
                                    main.setBatteryLevel(voltage);    // 🔄 Battery UI now updates
                                }
                            });

                        }

                    }
                    else {
                        Log.d("sundar", "❌ Skipped non-battery packet");
                    }

                } catch (IOException e) {
                    Log.e("ganesh", "IO exception: " + e.getMessage());
                    break;
                }
            }
        }

    }
}

















