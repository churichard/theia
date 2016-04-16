package hackru2016s.theia;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;


public class MainActivity extends Activity {

    private static final int TAKE_PICTURE_REQUEST = 1;

    private CameraPreview cameraView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initiate CameraView
        cameraView = new CameraPreview(this);

        // Set the view
        this.setContentView(cameraView);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Do not hold the camera during onResume
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Do not hold the camera during onPause
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }

//    private void takePicture() {
//        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
//            String thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);
//            String picturePath = data.getStringExtra(Intents.EXTRA_PICTURE_FILE_PATH);
//
//            processPictureWhenReady(picturePath);
//            // TODO: Show the thumbnail to the user while the full picture is being
//            // processed.
//        }
//
//        super.onActivityResult(requestCode, resultCode, data);
//    }
//
//    private void processPictureWhenReady(final String picturePath) {
//        final File pictureFile = new File(picturePath);
//
//        if (pictureFile.exists()) {
//            // The picture is ready; process it.
//        } else {
//            // The file does not exist yet. Before starting the file observer, you
//            // can update your UI to let the user know that the application is
//            // waiting for the picture (for example, by displaying the thumbnail
//            // image and a progress indicator).
//
//            final File parentDirectory = pictureFile.getParentFile();
//            FileObserver observer = new FileObserver(parentDirectory.getPath(),
//                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
//                // Protect against additional pending events after CLOSE_WRITE
//                // or MOVED_TO is handled.
//                private boolean isFileWritten;
//
//                @Override
//                public void onEvent(int event, String path) {
//                    if (!isFileWritten) {
//                        // For safety, make sure that the file that was created in
//                        // the directory is actually the one that we're expecting.
//                        File affectedFile = new File(parentDirectory, path);
//                        isFileWritten = affectedFile.equals(pictureFile);
//
//                        if (isFileWritten) {
//                            stopWatching();
//
//                            // Now that the file is ready, recursively call
//                            // processPictureWhenReady again (on the UI thread).
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    processPictureWhenReady(picturePath);
//                                }
//                            });
//                        }
//                    }
//                }
//            };
//            observer.startWatching();
//        }
//    }
}
