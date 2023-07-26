package com.example.FYP_project;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationHandler;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final String TAG="MainActivity";

    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    // call java class
    private facialExpressionRecognition facialExpressionRecognition;
    Timer timer = new Timer();
    private String EmotionAvg = "Neutral",EmotionComp=null, CurrEmotion="Neutral";

    private static List<String> emotion_slist = new ArrayList<>();

    //Spotify API stuff
    private static final int REQUEST_CODE = 1337;
    private static final String REDIRECT_URI = "http://localhost:3000";
    private static final String CLIENT_ID = "a334b6fa3ecc4b658fcc6c9c601553e8";
    private SpotifyAppRemote mSpotifyAppRemote;

    private Toast toast;

    //playlist flags
    private boolean plAngry = false, plSad = false;
    private String nowPlaying = "", track="",artist="";

    //playlist info
    FloatingActionButton fabnext , fabprev , fabplaypause;
    Button ButtSpotify;
    TextView tvtrack , tvartist ;

    private BaseLoaderCallback mLoaderCallback =new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status){
                case LoaderCallbackInterface
                        .SUCCESS:{
                    Log.i(TAG,"OpenCv Is loaded");
                    mOpenCvCameraView.setCameraIndex(1);//change back/forward camera
                    mOpenCvCameraView.enableView();
                }
                default:
                {
                    super.onManagerConnected(status);

                }
                break;
            }
        }
    };

    public CameraActivity(){
        Log.i(TAG,"Instantiated new "+this.getClass());
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Spotify authorization and connection block
        {




            ConnectionParams connectionParams =
                    new ConnectionParams.Builder(CLIENT_ID)
                            .setRedirectUri(REDIRECT_URI)
                            .showAuthView(true)
                            .build();

            SpotifyAppRemote.connect(this, connectionParams,
                    new Connector.ConnectionListener() {
                        public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                            mSpotifyAppRemote = spotifyAppRemote;
                            Log.d("PlayPage", "Connected! Yay!");
                            //display toast message
                            Toast.makeText(CameraActivity.this, "Connected to Spotify!", Toast.LENGTH_LONG).show();
                            SubscribeToPlayerState();
                        }

                        public void onFailure(Throwable throwable) {
                            Log.e("PlayPage", throwable.getMessage(), throwable);
                            //display toast message
                            Toast.makeText(CameraActivity.this, "Failed to connect to Spotify!", Toast.LENGTH_LONG).show();
                        }
                    });
        }

        //camera startup block
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int MY_PERMISSIONS_REQUEST_CAMERA=0;
        // if camera permission is not given it will ask for it on device
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(CameraActivity.this, new String[] {Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        setContentView(R.layout.activity_camera);

        fabnext = findViewById(R.id.fab_next);
        fabprev = findViewById(R.id.fab_prev);
        fabplaypause = findViewById(R.id.fab_playpause);
        ButtSpotify = findViewById(R.id.ButtSpotify);

        tvtrack = findViewById(R.id.tv_songname);
        tvartist = findViewById(R.id.tv_artistname);

        mOpenCvCameraView=(CameraBridgeViewBase) findViewById(R.id.frame_Surface);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // this will load cascade classifier and model
        // this only happen one time when you start CameraActivity
        try{
            // input size of model is 48
            int inputSize=48;
            facialExpressionRecognition=new facialExpressionRecognition(getAssets(),CameraActivity.this,
                    "modelMMA500.tflite",inputSize);
        }
        catch (IOException e){
            e.printStackTrace();
        }

        //emotion averaging based on which emotion shows up the most
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (facialExpressionRecognition.getEmotion_Display()!=null){
                    emotion_slist.add(facialExpressionRecognition.getEmotion_Display());
                    Log.d("emotion_slist", emotion_slist.toString());
                }

                if (emotion_slist.size()==100){
                    String mode = CalcEmotionMode(emotion_slist);
                    emotion_slist.clear();
                    EmotionAvg = mode;
                }
            }

        }, 0, 25);

        //play playlist based on emotion

        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                String EmotionAvg2 = EmotionAvg;

                if (CurrEmotion.equals(EmotionAvg2) || CurrEmotion.equals(null))
                {
                    //buffer for same emotions
                    Log.d("PlayPage,EMotions", "Current Emotion: " + CurrEmotion+ " //Average Emotion: " + EmotionAvg2);
                    //CurrEmotion = EmotionAvg2;
                }
                else if (!CurrEmotion.equals(EmotionAvg2))
                {
                    if (EmotionAvg2.equals("Angry") && !plAngry)
                    {
                        mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX4nNmLlb3JR2");
                        SubscribeToPlayerState();

                        plAngry = true;
                        plSad = false;

                        nowPlaying = "Angry Playlist";




                    }

                    else if (EmotionAvg2.equals("Sad") && !plSad)
                    {
                        mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:7GhawGpb43Ctkq3PRP1fOL");
                        SubscribeToPlayerState();

                        plAngry = false;
                        plSad = true;

                        nowPlaying = "Sad Playlist";
                    }

                    else{}

                    CurrEmotion = EmotionAvg2;
                    Log.d("PlayPage,EMotions", "Current Emotion: " + CurrEmotion+ " //Average Emotion: " + EmotionAvg2);
                }
            }
        }, 0, 1000);



    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()){
            //if load success
            Log.d(TAG,"Opencv initialization is done");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else{
            //if not loaded
            Log.d(TAG,"Opencv is not loaded. try again");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,this,mLoaderCallback);
        }


        //playback buttons
        fabnext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpotifyAppRemote.getPlayerApi().skipNext();
                fabplaypause.setImageResource(R.drawable.ic_pause);
            }
        });

        fabprev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpotifyAppRemote.getPlayerApi().skipPrevious();
                fabplaypause.setImageResource(R.drawable.ic_pause);
            }
        });

        fabplaypause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSpotifyAppRemote.getPlayerApi().getPlayerState().setResultCallback(new CallResult.ResultCallback<PlayerState>() {
                    @Override
                    public void onResult(PlayerState playerState) {
                        if (playerState.isPaused){
                            mSpotifyAppRemote.getPlayerApi().resume();
                            //change play button to pause button
                            fabplaypause.setImageResource(R.drawable.ic_pause);
                        }
                        else{
                            mSpotifyAppRemote.getPlayerApi().pause();
                            //change pause button to play button
                            fabplaypause.setImageResource(R.drawable.ic_play);
                        }
                    }
                });
            }
        });

        //spotify button
        ButtSpotify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //open spotify app
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
                startActivity(intent);
            }
        });


    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }
    }

    public void onDestroy(){
        super.onDestroy();
        if(mOpenCvCameraView !=null){
            mOpenCvCameraView.disableView();
        }

    }

    public void onCameraViewStarted(int width ,int height){
        mRgba=new Mat(height,width, CvType.CV_8UC4);
        mGray =new Mat(height,width,CvType.CV_8UC1);


    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        mRgba=inputFrame.rgba();
        mGray=inputFrame.gray();
        //output                                         input
        mRgba=facialExpressionRecognition.recognizeImage(mRgba);

        //set text view for emotion displayed on screen

        runOnUiThread(new Runnable() {
            public void run() {
                TextView textView = (TextView) findViewById(R.id.CurrentEmotionText);
                TextView OnOffLabel = (TextView) findViewById(R.id.MoodCorrOnOff);
                textView.setText(EmotionAvg);

                if(EmotionAvg.equals("Sad") || EmotionAvg.equals("Angry"))
                {
                    OnOffLabel.setText("On");
                }

                else
                {
                    OnOffLabel.setText("Off");
                }
            }
        });



        return mRgba;


    }
    public void onCameraViewStopped(){
        mRgba.release();
    }


    public String CalcEmotionMode(List<String> emotion_slist)
    {
        String mode = "";
        int maxCount = 0;
        for (int i = 0; i < emotion_slist.size(); ++i) {
            int count = 0;
            for (int j = 0; j < emotion_slist.size(); ++j) {
                if (emotion_slist.get(j).equals(emotion_slist.get(i)))
                    ++count;
            }
            if (count > maxCount) {
                maxCount = count;
                mode = emotion_slist.get(i);
            }
        }
        return mode;
    }

    private void SubscribeToPlayerState() {
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track2 = playerState.track;


                    if (track2 != null) {
                        //Log.d("Track info", track.name + " by " + track.artist.name + "in playlist " + track.uri);
                        /*
                        if (toast != null)
                            toast.cancel();
                        toast  = Toast.makeText(CameraActivity.this, "Now Playing: " + nowPlaying, Toast.LENGTH_LONG);
                        toast.show();

                         */

                        try{ toast.getView().isShown();     // true if visible
                            toast.setText("Now Playing: " +  track2.name + " by " + track2.artist.name);
                        } catch (Exception e) {         // invisible if exception
                            toast = Toast.makeText(this, "Now Playing: " + track2.name + " by " + track2.artist.name, Toast.LENGTH_LONG);
                        }
                        toast.show();  //finally display it

                        tvartist.setText(track2.artist.name);
                        tvtrack.setText(track2.name);
                        //check if player is paused
                        if (playerState.isPaused){
                            fabplaypause.setImageResource(R.drawable.ic_play);
                        }
                        else{
                            fabplaypause.setImageResource(R.drawable.ic_pause);
                        }


                    }


                });
    }

    //music player images







}