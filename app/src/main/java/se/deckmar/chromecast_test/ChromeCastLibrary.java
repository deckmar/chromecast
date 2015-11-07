package se.deckmar.chromecast_test;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class ChromeCastLibrary {

    private static final String TAG = "JODE.ChromeCastLibrary";

    private String chromecastApplicationId;
    private Context applicationContext;
    private ChromeCastLibraryListener chromeCastLibraryListenerLister;

    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    private MediaRouterCallback mediaRouterCallback;
    private CastDevice selectedDevice;
    private GoogleApiClient apiClient;
    private boolean wasLaunched;
    private String applicationStatus;
    private String sessionId;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private boolean isPlaying;

    public ChromeCastLibrary(Context applicationContext, String chromecastApplicationId, ChromeCastLibraryListener chromeCastLibraryListenerLister) {
        this.chromecastApplicationId = chromecastApplicationId;
        this.applicationContext = applicationContext;
        this.chromeCastLibraryListenerLister = chromeCastLibraryListenerLister;

        Log.d(TAG, "Starting the MediaRouter stuff");
        mediaRouter = MediaRouter.getInstance(applicationContext);
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(chromecastApplicationId))
                .build();

        mediaRouterCallback = new MediaRouterCallback();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    public void onCreateOptionsMenuEvent(Menu menu) {
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
    }

    public void onStopEvent() {
        mediaRouter.removeCallback(mediaRouterCallback);
    }

    private void launchReceiver() {
        CastClientListener castListener = new CastClientListener();
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(selectedDevice, castListener);

        apiClient = new GoogleApiClient.Builder(applicationContext)
                .addApiIfAvailable(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(new ConnectionCallbacks())
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "Connection failed");
                    }
                })
                .build();

        apiClient.connect();
    }

    private void reconnectChannels() {
        Log.d(TAG, "Reconnect channels!");
    }

    private void teardown() {
        Log.d(TAG, "TEARDOWN!!");
        apiClient.disconnect();
        this.mRemoteMediaPlayer = null;
        chromeCastLibraryListenerLister.onChromecastDisconnected();
    }

    public boolean isPlaying() {
        return this.mRemoteMediaPlayer != null && isPlaying;
    }

    public void pause() {
        mRemoteMediaPlayer.pause(apiClient).setResultCallback(
            new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                @Override
                public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                    Status status = result.getStatus();
                    if (!status.isSuccess()) {
                        Log.w(TAG, "Unable to toggle pause: "
                                + status.getStatusCode());
                    }
                }
            });
    }

    public void play() {
        mRemoteMediaPlayer.play(apiClient).setResultCallback(
            new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                @Override
                public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                    Status status = result.getStatus();
                    if (!status.isSuccess()) {
                        Log.w(TAG, "Unable to toggle pause: "
                                + status.getStatusCode());
                    }
                }
            });
    }


    private void updateLaunchState() {
        if (mRemoteMediaPlayer == null) {
            mRemoteMediaPlayer = new RemoteMediaPlayer();
            mRemoteMediaPlayer.setOnStatusUpdatedListener(
                    new RemoteMediaPlayer.OnStatusUpdatedListener() {
                        @Override
                        public void onStatusUpdated() {
                            MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();

                            if (mediaStatus != null) {
                                isPlaying = mediaStatus.getPlayerState() ==
                                        MediaStatus.PLAYER_STATE_PLAYING;

                                Log.d(TAG, "Playing: " + isPlaying);

                                if (isPlaying) {
                                    chromeCastLibraryListenerLister.onChromecastPlaying();
                                } else {
                                    chromeCastLibraryListenerLister.onChromecastPause();
                                }
                            }
                        }
                    });

            mRemoteMediaPlayer.setOnMetadataUpdatedListener(
                    new RemoteMediaPlayer.OnMetadataUpdatedListener() {
                        @Override
                        public void onMetadataUpdated() {
                            MediaInfo mediaInfo = mRemoteMediaPlayer.getMediaInfo();

                            if (mediaInfo != null) {
                                MediaMetadata metadata = mediaInfo.getMetadata();

                                Log.d(TAG, "Media updated: " + metadata);
                            }
                        }
                    });
        }

        try {
            Cast.CastApi.setMessageReceivedCallbacks(apiClient,
                    mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer);
        } catch (IOException e) {
            Log.e(TAG, "Exception while creating media channel", e);
        }

        mRemoteMediaPlayer
                .requestStatus(apiClient)
                .setResultCallback(
                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    Log.e(TAG, "Failed to request status.");
                                } else {
                                    chromeCastLibraryListenerLister.onChromecastConnected();
                                }
                            }
                        });

    }

    public void playVideo(String videoTitle, String contentType, String videoUrl) {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, videoTitle);
        MediaInfo mediaInfo = new MediaInfo.Builder(videoUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(mediaMetadata)
                .build();
        try {
            mRemoteMediaPlayer.load(apiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                Log.d(TAG, "Media loaded successfully");
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG, "Problem occurred with media during loading", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem opening media during loading", e);
        }
    }


    /***
     * Listener interface
     */
    public interface ChromeCastLibraryListener {
        void onChromecastConnected();

        void onChromecastStatusUpdate(String status);

        void onChromecastPlaying();
        void onChromecastPause();

        void onChromecastDisconnected();
    }


    /***
     * MediaRouterCallback
     */
    private class MediaRouterCallback extends MediaRouter.Callback {

        public static final String TAG = "JODE.MediaRouterCb";

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteSelected(router, route);

            selectedDevice = CastDevice.getFromBundle(route.getExtras());
            String deviceId = selectedDevice.getDeviceId();

            Log.d(TAG, "Route Selected: " + deviceId);

            launchReceiver();
        }
        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            super.onRouteUnselected(router, route);

            Log.d(TAG, "Route Unselected");
            teardown();
            selectedDevice = null;
        }

    }


    /***
     * CastClientListener
     */
    private class CastClientListener extends Cast.Listener {
        
        private static final String TAG = "JODE.CastClientListener";
        
        @Override
        public void onApplicationStatusChanged() {
            super.onApplicationStatusChanged();

            Log.d(TAG, "Application status changed");

            if (apiClient != null) {
                String chromecastStatus = Cast.CastApi.getApplicationStatus(apiClient);
                Log.d(TAG, "onApplicationStatusChanged: " + chromecastStatus);

                chromeCastLibraryListenerLister.onChromecastStatusUpdate(chromecastStatus);
            }
        }

        @Override
        public void onVolumeChanged() {
            super.onVolumeChanged();

            Log.d(TAG, "Volume changed");

            if (apiClient != null) {
                Log.d(TAG, "onVolumeChanged: " + Cast.CastApi.getVolume(apiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            super.onApplicationDisconnected(statusCode);

            Log.d(TAG, "Application disconnected");
            teardown();
        }
    }


    /***
     * ConnectionCallbacks
     */
    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        private static final String TAG = "JODE.ConnectionCbs";

        public boolean mWaitingForReconnect;

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "Connected");

            if (mWaitingForReconnect) {
                mWaitingForReconnect = false;
                reconnectChannels();
            } else {
                try {
                    Cast.CastApi.launchApplication(apiClient, chromecastApplicationId, false)
                        .setResultCallback(
                            new ResultCallback<Cast.ApplicationConnectionResult>() {
                                @Override
                                public void onResult(Cast.ApplicationConnectionResult result) {
                                    Status status = result.getStatus();
                                    if (status.isSuccess()) {
                                        ApplicationMetadata applicationMetadata =
                                                result.getApplicationMetadata();
                                        sessionId = result.getSessionId();
                                        applicationStatus = result.getApplicationStatus();
                                        wasLaunched = result.getWasLaunched();

                                        updateLaunchState();
                                    } else {
                                        teardown();
                                    }
                                }
                            }
                        );
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch application", e);
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Connection suspended");

            mWaitingForReconnect = true;
        }
    }
}
