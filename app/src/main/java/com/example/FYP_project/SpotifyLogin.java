package com.example.FYP_project;

import static com.spotify.sdk.android.auth.AccountsQueryParameters.CLIENT_ID;
import static com.spotify.sdk.android.auth.AccountsQueryParameters.REDIRECT_URI;
import static com.spotify.sdk.android.auth.LoginActivity.REQUEST_CODE;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import org.opencv.android.OpenCVLoader;

public class SpotifyLogin extends AppCompatActivity {

    private static final int REQUEST_CODE = 1337;
    private static final String REDIRECT_URI = "http://localhost:3000";
    private static final String CLIENT_ID = "a334b6fa3ecc4b658fcc6c9c601553e8";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spotify_login);
    }

    protected void onStart() {
        super.onStart();

        if(OpenCVLoader.initDebug()){
            Log.d("MainActivity: ","Opencv is loaded");
        }
        else {
            Log.d("MainActivity: ","Opencv failed to load");
        }
    }

    protected void onResume() {
        super.onResume();

        Button loginButton = findViewById(R.id.button_SpotifyLogin);
        loginButton.setOnClickListener(v -> {
            AuthorizationRequest.Builder builder =
                    new AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI);

            builder.setScopes(new String[]{"streaming"});
            AuthorizationRequest request = builder.build();

            AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request);

        });
    }

    protected void onPause() {
        super.onPause();
    }

    protected void onStop() {
        super.onStop();
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, intent);

            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    Intent intent1 = new Intent(this, CameraActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent1.putExtra("authorization_code", response.getAccessToken());
                    startActivity(intent1);
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
            }

        }
    }

    


}