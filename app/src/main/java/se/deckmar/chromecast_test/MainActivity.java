package se.deckmar.chromecast_test;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements ChromeCastLibrary.ChromeCastLibraryListener {

    private static final String TAG = "JODE.MainActivity";
    public static final String CHROMECAST_APPLICATION_ID = "B3A723E6";

    private ChromeCastLibrary chromeCastLibrary;
    private Button buttonPlayPause;
    private TextView textPlaybackStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "This is a Snackbar! :)", Snackbar.LENGTH_LONG)
                        .setAction("Ok!", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Toast.makeText(getApplicationContext(), "Alright!", Toast.LENGTH_LONG).show();
                            }
                        }).show();
            }
        });

        buttonPlayPause = (Button) findViewById(R.id.playback_pause_play);
        buttonPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (chromeCastLibrary.isPlaying()) {
                    chromeCastLibrary.pause();
                } else {
                    chromeCastLibrary.play();
                }
            }
        });

        textPlaybackStatus = (TextView) findViewById(R.id.playback_status);
        textPlaybackStatus.setText("Ready to connect");

        /* Chromecast */
        chromeCastLibrary = new ChromeCastLibrary(getApplicationContext(), CHROMECAST_APPLICATION_ID, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        chromeCastLibrary.onCreateOptionsMenuEvent(menu);

        return true;
    }


    @Override
    protected void onStop() {
        super.onStop();

        chromeCastLibrary.onStopEvent();
    }

    @Override
    public void onChromecastConnected() {
        buttonPlayPause.setText("Starting..");
        chromeCastLibrary.playVideo("Big Buck Bunny", "video/mp4", "http://www.sample-videos.com/video/mp4/720/big_buck_bunny_720p_50mb.mp4");
    }

    @Override
    public void onChromecastStatusUpdate(String status) {
        Log.d(TAG, "Status update: " + status);
        textPlaybackStatus.setText(status);
    }

    @Override
    public void onChromecastPlaying() {
        buttonPlayPause.setText("Pause");
        buttonPlayPause.setEnabled(true);
    }

    @Override
    public void onChromecastPause() {
        buttonPlayPause.setText("Play");
    }

    @Override
    public void onChromecastDisconnected() {
        buttonPlayPause.setText("Ready to connect");
        buttonPlayPause.setEnabled(false);
        textPlaybackStatus.setText("Ready to connect");
    }
}
