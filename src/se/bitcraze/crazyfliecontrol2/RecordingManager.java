package se.bitcraze.crazyfliecontrol2;

// make sure this line matches EspActivity’s package

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class RecordingManager {
    private final Context ctx;
    private final int width, height, fps;
    private MediaCodec codec;
    private MediaMuxer muxer;
    private int videoTrack = -1;
    private boolean muxerStarted = false;
    private Surface inputSurface;
    private File outputFile;
    private Thread drainThread;

    public RecordingManager(Context ctx, int width, int height, int fps) {
        this.ctx = ctx;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }

    public MediaCodec getCodec() { return codec; }

    /*-------------------------------- START --------------------------------*/
    public Surface start() throws IOException {
        // 1. choose filename in scoped Movies/ESP/
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ESP");
        if (!dir.exists()) dir.mkdirs();
        outputFile = new File(dir, "REC_" + System.currentTimeMillis() + ".mp4");

        // 2. encoder config
//        MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", width, height);
//        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000);
//        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
//        //fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        int widthEven = width & ~1;  // even numbers
        int heightEven = height & ~1;

        MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", widthEven, heightEven);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);



        codec = MediaCodec.createEncoderByType("video/avc");
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = codec.createInputSurface();
        codec.start();

        new Handler(Looper.getMainLooper()).post(() -> {
            Canvas c = inputSurface.lockCanvas(null);
            if (c != null) {
                c.drawColor(Color.BLACK); // dummy frame for initialization
                inputSurface.unlockCanvasAndPost(c);
            }
        });

        // 3. muxer
        muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // 4. start drain loop
        drainThread = new Thread(this::drain);
        drainThread.start();
        return inputSurface;                 // hand this to VideoReceiveThread
    }

    /*-------------------------------- STOP --------------------------------*/
//    public void stop() {
//        if (codec == null) return;
//        codec.signalEndOfInputStream();      // lets drain() exit cleanly
//        try { drainThread.join(); } catch (InterruptedException ignored) {}
//        codec.stop();  codec.release();
//        if (muxerStarted) muxer.stop();
//        muxer.release();
//        publishToMediaStore();               // optional: Gallery visibility
//    }


    public void stop() {
        try {
            codec.signalEndOfInputStream();

            if (drainThread != null && drainThread.isAlive()) {
                drainThread.join(2000); // 2s timeout
                if (drainThread.isAlive()) {
                    Log.w("RECs", "⚠️ Drain thread did not terminate in time.");
                }
            }

            if (muxerStarted) {
                muxer.stop();
                muxer.release();
            }

            codec.stop();
            codec.release();
            inputSurface.release();

            saveVideoToStorage(outputFile);  // ✅ Ensures proper saving

            Log.i("RECs", "✅ Recording stopped");
        } catch (Exception e) {
            Log.e("RECs", "❌ Error during stop: " + e.getMessage());
        }
    }



    private void saveVideoToStorage(File recordedFile) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, recordedFile.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EspDrone");

            ContentResolver resolver = ctx.getContentResolver();
            Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri videoUri = resolver.insert(collection, values);

            if (videoUri == null) {
                Log.e("RECs", "❌ Failed to create MediaStore entry.");
                return;
            }

            try (OutputStream out = resolver.openOutputStream(videoUri)) {
                if (out != null) {
                    Files.copy(recordedFile.toPath(), out);
                    out.flush(); // ✅ ensure all bytes are written
                    Log.i("RECs", "✅ Video saved to gallery: " + videoUri.toString());
                } else {
                    Log.e("RECs", "❌ OutputStream is null.");
                }
            }
        } catch (Exception e) {
            Log.e("RECs", "❌ Error saving video: " + e.getMessage());
        }
    }





    /*-------------------------------- DRAIN --------------------------------*/
    /*-------------------------------- DRAIN --------------------------------*/
    private void drain() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int samples = 0;

        while (true) {
            int idx = codec.dequeueOutputBuffer(info, 10_000);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) continue;

            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrack = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                muxerStarted = true;
                continue;
            }

            if (idx >= 0) {
                ByteBuffer encodedData = codec.getOutputBuffer(idx);
                if (encodedData != null && info.size > 0 && muxerStarted) {
                    encodedData.position(info.offset);
                    encodedData.limit(info.offset + info.size);
                    muxer.writeSampleData(videoTrack, encodedData, info);
                    samples++;
                }
                codec.releaseOutputBuffer(idx, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i("RECs", "Encoded samples = " + samples);
                    break;
                }
            }
        }
    }



    /*-------------------------------- GALLERY PUBLISH ----------------------*/
    private void publishToMediaStore() {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, outputFile.getName());
        cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/ESP");
        Uri uri = ctx.getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
            Files.copy(outputFile.toPath(), os);
        } catch (IOException ignored) {}
    }


    public Surface getInputSurface() {
        return inputSurface;
    }


}




