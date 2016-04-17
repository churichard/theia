package hackru2016s.theia;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SNAPSHOT_DELAY = 3500;
    private static final int NUM_BUCKETS = 4;
    private static final int DIFF_THRESHOLD = 50000;

    private CameraPreview cameraView;
    private byte[] lastImage;
    private float[] lastOrientation;
    private TextToSpeech tts;
    private ClarifaiClient clarifai;
    private boolean currentlyTakingPicture;
    private boolean manualActivation;
    private Handler checkImageHandler;
    private Runnable checkImageRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Set the view
        this.setContentView(R.layout.camera_preview);

        // Initiate CameraView
        cameraView = new CameraPreview(this);
        lastImage = null;
        lastOrientation = null;
        manualActivation = false;

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraView);

        // Initialize Text To Speech
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);
                    Log.d("TTS: ", "Started Successfully");
                } else {
                    Log.d("TTS: ", "Failed running");
                }
            }
        });

        // Construct Clarifai Client
        clarifai = new ClarifaiClient(getString(R.string.CLARIFAI_APP_ID), getString(R.string.CLARIFAI_APP_SECRET));

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        currentlyTakingPicture = false;

        checkImageHandler = new Handler();
        checkImageRunnable = new Runnable() {
            @Override
            public void run() {
                if (cameraView.isReady()) {
                    if (tts != null && !tts.isSpeaking()) {
                        manualActivation = false;
                        takePicture();
                    }
                }

                checkImageHandler.postDelayed(this, SNAPSHOT_DELAY);
            }
        };

        checkImageHandler.post(checkImageRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (cameraView == null) {
            // Initiate CameraView
            cameraView = new CameraPreview(this);

            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(cameraView);
        }

        if (tts == null) {
            // Initialize Text To Speech
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        tts.setLanguage(Locale.US);
                        Log.d("TTS: ", "Started Successfully");
                    } else {
                        Log.d("TTS: ", "Failed running");
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cameraView != null) {
            cameraView.releaseCamera();
            cameraView = null;
        }

        // Do not hold text to speech during onPause
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        checkImageHandler.removeCallbacks(checkImageRunnable);
    }

    // On tap, take the picture
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_DPAD_CENTER) {
            manualActivation = true;
            checkImageHandler.removeCallbacks(checkImageRunnable);
            takePicture();
            return true;
        }
        return super.onKeyDown(keycode, event);
    }

    // Say the sentence out loud
    private void textToSpeech(String toSpeak) {
        if (manualActivation ||
                (tts != null && !toSpeak.equals(""))) {
            tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
            Log.d("TTS", "Speech successfully ran");
        }
    }

    // Create the sentence from the Tags
    private String createSentence(List<Tag> tags) {
        String sentence = "Looks like ";
        String captionTags = "";
        int count = 0;
        for (Tag tag : tags) {
            if (tag.getName().equalsIgnoreCase("no person")) continue;

            if (tag.getProbability() > 0.98 && count < 3) {
                sentence += tag.getName() + " or ";
                captionTags += tag.getName() + ", ";
                count++;
            } else if (tag.getProbability() > 0.90 && count < 3) {
                sentence += "probably " + tag.getName() + " or ";
                captionTags += tag.getName() + ", ";
                count++;
            } else if (tag.getProbability() > 0.70 && count < 3) {
                sentence += "maybe " + tag.getName() + " or ";
                captionTags += tag.getName() + ", ";
                count++;
            } else if (tag.getProbability() > 0.50 && count < 3) {
                sentence += "perhaps " + tag.getName() + " or ";
                captionTags += tag.getName() + ", ";
                count++;
            }
        }
        if (count == 0) {
            sentence = "I'm not quite sure what you're looking at.     ";
        }
        sentence = sentence.substring(0, sentence.length() - 4);
        Log.d("Sentence: ", sentence);
        textToSpeech(sentence);
        return captionTags.substring(0, captionTags.length() - 2);
    }

    // Call Clarifai API to recognize the image
    private String recognizeImage(byte[] image) {
        if (manualActivation || lastImage == null
                || calcImageDiff(lastImage, image) < DIFF_THRESHOLD) {
            List<RecognitionResult> results;

            lastImage = image;
            results = clarifai.recognize(new RecognitionRequest(image));
            return createSentence(results.get(0).getTags());
        }

        return "";
    }

    private float calcImageDiff(byte[] image1, byte[] image2) {
        Bitmap bitmap1 = BitmapFactory.decodeByteArray(image1, 0, image1.length),
                bitmap2 = BitmapFactory.decodeByteArray(image2, 0, image2.length);
        int width = bitmap1.getWidth(), height = bitmap1.getHeight();
        int[] pixels1 = new int[width * height], pixels2 = new int[width * height];
        int[] redBuckets1 = new int[NUM_BUCKETS], redBuckets2 = new int[NUM_BUCKETS],
                greenBuckets1 = new int[NUM_BUCKETS], greenBuckets2 = new int[NUM_BUCKETS],
                blueBuckets1 = new int[NUM_BUCKETS], blueBuckets2 = new int[NUM_BUCKETS];
        long sum = 0;

        // Get all pixel data
        bitmap1.getPixels(pixels1, 0, width, 0, 0, width, height);
        bitmap2.getPixels(pixels2, 0, width, 0, 0, width, height);

        // Put pixels into buckets
        for (int i = 0; i < width * height; i += 10) {
            int red1 = Color.red(pixels1[i]), red2 = Color.red(pixels2[i]),
                    green1 = Color.green(pixels1[i]), green2 = Color.green(pixels2[i]),
                    blue1 = Color.blue(pixels1[i]), blue2 = Color.blue(pixels2[i]);

            // Bitmap 1 red histogram
            if (red1 >= 0 && red1 < 64) {
                redBuckets1[0]++;
            } else if (red1 >= 64 && red1 < 128) {
                redBuckets1[1]++;
            } else if (red1 >= 128 && red1 < 192) {
                redBuckets1[2]++;
            } else if (red1 >= 192 && red1 < 256) {
                redBuckets1[3]++;
            }

            // Bitmap 1 green histogram
            if (green1 >= 0 && green1 < 64) {
                greenBuckets1[0]++;
            } else if (green1 >= 64 && green1 < 128) {
                greenBuckets1[1]++;
            } else if (green1 >= 128 && green1 < 192) {
                greenBuckets1[2]++;
            } else if (green1 >= 192 && green1 < 256) {
                greenBuckets1[3]++;
            }

            // Bitmap 1 blue histogram
            if (blue1 >= 0 && blue1 < 64) {
                blueBuckets1[0]++;
            } else if (blue1 >= 64 && blue1 < 128) {
                blueBuckets1[1]++;
            } else if (blue1 >= 128 && blue1 < 192) {
                blueBuckets1[2]++;
            } else if (blue1 >= 192 && blue1 < 256) {
                blueBuckets1[3]++;
            }

            // Bitmap 2 red histogram
            if (red2 >= 0 && red2 < 64) {
                redBuckets2[0]++;
            } else if (red2 >= 64 && red2 < 128) {
                redBuckets2[1]++;
            } else if (red2 >= 128 && red2 < 192) {
                redBuckets2[2]++;
            } else if (red2 >= 192 && red2 < 256) {
                redBuckets2[3]++;
            }

            // Bitmap 2 green histogram
            if (green2 >= 0 && green2 < 64) {
                greenBuckets2[0]++;
            } else if (green2 >= 64 && green2 < 128) {
                greenBuckets2[1]++;
            } else if (green2 >= 128 && green2 < 192) {
                greenBuckets2[2]++;
            } else if (green2 >= 192 && green2 < 256) {
                greenBuckets2[3]++;
            }

            // Bitmap 2 blue histogram
            if (blue2 >= 0 && blue2 < 64) {
                blueBuckets2[0]++;
            } else if (blue2 >= 64 && blue2 < 128) {
                blueBuckets2[1]++;
            } else if (blue2 >= 128 && blue2 < 192) {
                blueBuckets2[2]++;
            } else if (blue2 >= 192 && blue2 < 256) {
                blueBuckets2[3]++;
            }
        }

        // Sum up differences in buckets
        for (int i = 0; i < NUM_BUCKETS; i++) {
            sum += Math.abs(redBuckets1[i] - redBuckets2[i])
                    + Math.abs(greenBuckets1[i] - greenBuckets2[i])
                    + Math.abs(blueBuckets1[i] - blueBuckets2[i]);
        }

        Log.d("ImageDiff", " = " + sum);
        return sum;
    }

    private void takePicture() {
        if (!currentlyTakingPicture) {
            currentlyTakingPicture = true;
            // Create callback that is called when image has been captured
            Camera.PictureCallback mPicture = new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
                    BitmapFactory.decodeByteArray(data, 0, data.length)
                            .compress(Bitmap.CompressFormat.JPEG, 50, compressedStream);

                    cameraView.getCamera().startPreview();
                    currentlyTakingPicture = false;

                    new RecognizeImageTask().execute(compressedStream.toByteArray());
                    Log.d(TAG, "Picture taken");
                }
            };

            // Take a picture and start preview again
            if (cameraView.isReady()) {
                cameraView.getCamera().takePicture(null, null, mPicture);
            }
        }
    }

    private class RecognizeImageTask extends AsyncTask<byte[], Void, String> {
        protected String doInBackground(byte[]... images) {
            return recognizeImage(images[0]);
        }

        protected void onPostExecute(String captionText) {
            Log.d("Recognize Image: ", "Ran successfully");
            if (!captionText.equals("")) {
                TextView caption = (TextView) findViewById(R.id.caption_text);
                caption.setText(captionText);
                caption.setVisibility(View.VISIBLE);
            }

            if (manualActivation) {
                checkImageHandler.post(checkImageRunnable);
            }
        }
    }
}
