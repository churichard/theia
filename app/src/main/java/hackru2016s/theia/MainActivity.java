package hackru2016s.theia;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private CameraPreview cameraView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the view
        this.setContentView(R.layout.camera_preview);

        // Initiate CameraView
        cameraView = new CameraPreview(this);

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraView);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_DPAD_CENTER) {
            takePicture();
            return true;
        }
        return super.onKeyDown(keycode, event);
    }

    private void takePicture() {
        // Create callback that is called when image has been captured
        Camera.PictureCallback mPicture = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                /* TODO Do something with byte array of image data */
                Log.d(TAG, "Picture taken");
                cameraView.getCamera().startPreview();
            }
        };

        // Take a picture and start preview again
        cameraView.getCamera().takePicture(null, null, mPicture);
    }
}
