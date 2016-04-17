package hackru2016s.theia;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.StrictMode;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.List;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.RecognitionResult.StatusCode;
import com.clarifai.api.Tag;

import java.util.Locale;


public class MainActivity extends Activity{

    private static final String TAG = MainActivity.class.getSimpleName();
    private CameraPreview cameraView;
    private TextToSpeech tts;
    private ClarifaiClient clarifai;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Set the view
        this.setContentView(R.layout.camera_preview);

        // Construct Clarifai Client
        clarifai = new ClarifaiClient(getString(R.string.CLARIFAI_APP_ID), getString(R.string.CLARIFAI_APP_SECRET));

        // Initiate CameraView
        cameraView = new CameraPreview(this);

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraView);
        //Initialize Text To Speech
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener(){
            public void onInit(int status){
                if(status == TextToSpeech.SUCCESS){
                    tts.setLanguage(Locale.US);
                    Log.d("TTS: ", "Started Successfully");
                }
                else{
                    Log.d("TTS: ", "Failed running");
                }
            }
        });

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

        // Do not hold text to speech during onPause
        if(tts != null){
            tts.stop();
            tts.shutdown();
        }
    }

    // On tap, take the picture
    public boolean onKeyDown(int keycode, KeyEvent event){
        if(keycode == KeyEvent.KEYCODE_DPAD_CENTER){
            takePicture();
            return true;
        }
        return super.onKeyDown(keycode, event);
    }

    // Say the sentence out loud
    private void textToSpeech(String toSpeak){
        tts.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
        Log.d("TTS", "Speech successfully ran");
    }

    // Create the sentence from the Tags
    private void createSentence(List<Tag> tags){
        String sentence = "Looks like ";
        int count = 0;
        for(Tag tag : tags){
            if (tag.getProbability() > 0.98 && count < 3) {
                sentence += tag.getName() + " or ";
                count++;
            }
            else if (tag.getProbability() > 0.90 && count < 3) {
                sentence += "probably " + tag.getName() + " or ";
                count++;
            }
            else if (tag.getProbability() > 0.70 && count < 3) {
                sentence += "maybe " + tag.getName() + " or ";
                count++;
            }
            else if (tag.getProbability() > 0.50 && count < 3) {
                sentence += "perhaps " + tag.getName() + " or ";
                count++;
            }
        }
        if (count == 0){
            sentence = "I'm not quite sure what you're looking at.     ";
        }
        sentence = sentence.substring(0,sentence.length()-4);
        Log.d("Sentence: ", sentence);
        textToSpeech(sentence);
    }

    // Call Clarifai API to recognize the image
    private void recognizeImage(byte[] image){
        List<RecognitionResult> results;
        results = clarifai.recognize(new RecognitionRequest(image));
        createSentence(results.get(0).getTags());
        Log.d("Recognize Image: ", "Ran sucessfully");
    }

    private void takePicture() {
        // Create callback that is called when image has been captured
        Camera.PictureCallback mPicture = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                recognizeImage(data);
                Log.d(TAG, "Picture taken");
                cameraView.getCamera().startPreview();
            }
        };

        // Take a picture and start preview again
        cameraView.getCamera().takePicture(null, null, mPicture);
    }
}
