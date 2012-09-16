package tv.cloudeo.cdosa;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import cloudeo.api.platform.*;
import cloudeo.api.service.*;
import cloudeo.api.service.listener.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CloudeoApp extends Activity
{
  private static final long CDO_SAMPLES_APP_ID = 1;
  private static final String CDO_SAMPLES_SECRET = "CloudeoTestAccountSecret";

  class MediaStatsView {
    TextView text;
    boolean up;

    MediaStatsView(TextView tv, boolean u) {
      text = tv;
      up = u;
    }
  }

  private Map<Long, MediaStatsView> _mediaStatsMap =
      new HashMap<Long, MediaStatsView>();

  private static final String LOG_TAG = "CloudeoApp";
  private String scopeId = "";
  private BroadcastHandler broadcastReceiver;

  /**
   * ===========================================================================
   * Activity lifecycle management
   * ===========================================================================
   */

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.v(LOG_TAG, "Starting Cloudeo Labs ...");

    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    broadcastReceiver = new BroadcastHandler(this);
    registerReceiver(broadcastReceiver,
        new IntentFilter(Intent.ACTION_HEADSET_PLUG));

    EditText url = (EditText) findViewById(R.id.edit_url);
    url.setSelection(url.getText().length());

    TextView stats = (TextView) findViewById(R.id.text_stats);
    _mediaStatsMap.put(-1L, new MediaStatsView(stats, true));

    initializeActions();
    initializeCloudeo();
  }

  // ===========================================================================

  @Override
  public void onDestroy() {
    Log.v(LOG_TAG, "Destroying Cloudeo Labs ...");

    unregisterReceiver(broadcastReceiver);

    scopeId = "";
    Platform.release();
    super.onDestroy();
  }

  // ===========================================================================

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  /**
   * ===========================================================================
   * Cloudeo Platform initialization
   * ===========================================================================
   */

  private void initializeCloudeo() {
    PlatformInitListener listener = new PlatformInitListener() {
      @Override
      public void onInitProgressChanged(InitProgressChangedEvent e) {
        // Actually not used by the platform for now. Just a placeholder
      }

      @Override
      public void onInitStateChanged(InitStateChangedEvent e) {
        onCdoInitStateChanged(e);
      }
    };
    PlatformInitOptions initOptions = new PlatformInitOptions();
    String storageDir =
        Environment.getExternalStorageDirectory().getAbsolutePath();
    initOptions.setStorageDir(storageDir);
    Platform.init(listener, initOptions);
  }

  // ===========================================================================

  private void onCdoInitStateChanged(InitStateChangedEvent e) {
    if (e.getState() == InitState.INITIALIZED) {
      onCdoInitialized();
    } else {
      onCdoInitError(e);
    }
  }

  // ===========================================================================

  private void onCdoInitialized() {
    Platform.getService().getVersion(new UIThreadResponder<String>(this) {
      @Override
      protected void handleResult(String version) {
        final TextView versionLabel =
            (TextView) findViewById(R.id.sdk_version_label);
        versionLabel.append(version);
      }

      @Override
      protected void handleError(int errCode, String errMessage) {
        //To change body of implemented methods use File | Settings | File Templates.
      }
    });

    Platform.getService().addServiceListener(
        new ResponderAdapter<Void>(),
        getListener());
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        final LinearLayout layout = (LinearLayout)
            findViewById(R.id.main_layout);
        final Button button = (Button) findViewById(R.id.button_connect);
        final TextView status = (TextView) findViewById(R.id.text_status);
        final TextView stats = (TextView) findViewById(R.id.text_stats);
        status.setTextColor(Color.YELLOW);
        status.setText("Ready");
        button.setEnabled(true);
        stats.setText("Uplink Stats");
        for (MediaStatsView view : _mediaStatsMap.values()) {
          if (!view.up)
            layout.removeView(view.text);
        }
      }
    });
    Platform.getService().setApplicationId(new ResponderAdapter<Void>(),
        CDO_SAMPLES_APP_ID);
  }

  // ===========================================================================

  private void onCdoInitError(InitStateChangedEvent e) {
    final Button button = (Button) findViewById(R.id.button_connect);
    final TextView status = (TextView) findViewById(R.id.text_status);
    button.setEnabled(true);
    status.setTextColor(Color.RED);
    String errMessage = "ERROR: (" + e.getErrCode() + ") " +
        e.getErrMessage();
    status.setText(errMessage);
    Log.v(LOG_TAG, errMessage);
  }

  /**
   * ===========================================================================
   * UI Actions initialization and handling
   * ===========================================================================
   */

  private void initializeActions() {
    // 1. Initialize all the button actions
    findViewById(R.id.button_connect).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        onConnectClicked();
      }
    });

    findViewById(R.id.button_disconnect).
        setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            onDisconnectClicked();
          }
        });

    // 2. Initialize the spinners (selects)
    Spinner ec = (Spinner) findViewById(R.id.spinner_ec);
    ec.setSelection(4);
    ec.setOnItemSelectedListener(
        new AdvAudioSettingsCtrl("enableAEC", "modeAECM"));
    Spinner ns = (Spinner) findViewById(R.id.spinner_ns);
    ns.setSelection(6);
    ns.setOnItemSelectedListener(
        new AdvAudioSettingsCtrl("enableNS", "modeNS"));
    Spinner agc = (Spinner) findViewById(R.id.spinner_agc);
    agc.setSelection(1);
    agc.setOnItemSelectedListener(
        new AdvAudioSettingsCtrl("enableAGC", "modeAGC"));
  }

  // ===========================================================================

  private void onConnectClicked() {
    final Button connect = (Button) findViewById(R.id.button_connect);
    final TextView status = (TextView) findViewById(R.id.text_status);

    status.setTextColor(Color.CYAN);
    status.setText("Connecting ...");

    connect.setEnabled(false);

    EditText edit = (EditText) findViewById(R.id.edit_url);
    String url = edit.getText().toString();

    ConnectionDescriptor desc = genConnDescriptor(url);

    final UIThreadResponder<MediaConnection> connectResponder =
        new UIThreadResponder<MediaConnection>(this) {
          @Override
          protected void handleResult(MediaConnection result) {
            onConnected();
          }

          @Override
          protected void handleError(int errCode, String errMessage) {
            onConnectError(errCode, errMessage);
          }
        };

    Platform.getService().connect(connectResponder, desc);
  }

  // ===========================================================================

  private ConnectionDescriptor genConnDescriptor(String url) {
    ConnectionDescriptor desc = new ConnectionDescriptor();
    desc.setAutopublishAudio(true);
    desc.setUrl(url);
    scopeId = desc.getUrl().split("/")[1];
    Random rand = new Random();
    long userId = (1 + rand.nextInt(9999));
    String salt = "Some random string salt";
    long timeNow = System.currentTimeMillis() / 1000;
    long expires = timeNow + (5 * 60);

    desc.setToken("" + userId);
    AuthDetails authDetails = new AuthDetails();
    authDetails.setUserId(userId);
    authDetails.setSalt(salt);
    authDetails.setExpires(expires);
    StringBuffer  signatureBodyBuilder = new StringBuffer();
    signatureBodyBuilder.
        append(CDO_SAMPLES_APP_ID).
        append(scopeId).
        append(userId).
        append(salt).
        append(expires).
        append(CDO_SAMPLES_SECRET);
    String signatureBody = signatureBodyBuilder.toString();
    MessageDigest digest=null;
    String signature = "";
    try {
      digest = MessageDigest.getInstance("SHA-256");
      digest.update(signatureBody.getBytes());
      signature = bytesToHexString(digest.digest());
    } catch (NoSuchAlgorithmException e1) {

    }
    authDetails.setSignature(signature);
    desc.setAuthDetails(authDetails);
    return desc;
  }
  private static String bytesToHexString(byte[] bytes) {
    // http://stackoverflow.com/questions/332079
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < bytes.length; i++) {
      String hex = Integer.toHexString(0xFF & bytes[i]);
      if (hex.length() == 1) {
        sb.append('0');
      }
      sb.append(hex);
    }
    return sb.toString();
  }
  // ===========================================================================

  private void onDisconnectClicked() {
    final Button disconnect = (Button) findViewById(R.id.button_disconnect);
    final TextView status = (TextView) findViewById(R.id.text_status);

    status.setTextColor(Color.CYAN);
    status.setText("Disconnecting ...");

    disconnect.setEnabled(false);

    final UIThreadResponder<Void> disconnectResponder =
        new UIThreadResponder<Void>(this) {
          @Override
          protected void handleResult(Void result) {
            onDisconnected();
          }

          @Override
          protected void handleError(int errCode, String errMessage) {
          }
        };

    Platform.getService().disconnect(disconnectResponder, scopeId);
  }

  // ===========================================================================

  private void onConnected() {
    final Button connect = (Button) findViewById(R.id.button_connect);
    final Button disconnect = (Button) findViewById(R.id.button_disconnect);
    final TextView status = (TextView) findViewById(R.id.text_status);

    status.setTextColor(Color.GREEN);
    status.setText("In Call");

    connect.setVisibility(View.GONE);

    disconnect.setVisibility(View.VISIBLE);
    disconnect.setEnabled(true);
  }

  // ===========================================================================

  private void onConnectError(int errCode, String errMessage) {
    final Button connect = (Button) findViewById(R.id.button_connect);
    final TextView status = (TextView) findViewById(R.id.text_status);

    status.setTextColor(Color.RED);
    status.setText("ERROR: (" + errCode + ") " + errMessage);

    connect.setEnabled(true);

    Log.v(LOG_TAG, "ERROR: " + errCode + " " + errMessage);
  }

  // ===========================================================================

  private void onDisconnected() {
    final Button connect = (Button) findViewById(R.id.button_connect);
    final Button disconnect = (Button) findViewById(R.id.button_disconnect);
    final TextView status = (TextView) findViewById(R.id.text_status);

    status.setTextColor(Color.YELLOW);
    status.setText("Ready");

    connect.setVisibility(View.VISIBLE);
    connect.setEnabled(true);

    disconnect.setVisibility(View.GONE);
  }

  /**
   * ===========================================================================
   * Cloudeo Service Events handling
   * ===========================================================================
   */

  private CloudeoServiceListener getListener() {
    return new CloudeoServiceListenerAdapter() {
      @Override
      public void onConnectionLost(final ConnectionLostEvent e) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            onCdoConnLost(e);
          }
        });
      }

      @Override
      public void onUserEvent(final UserStateChangedEvent e) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            onCdoUserEvent(e);
          }
        });

      }

      @Override
      public void onMediaStats(final MediaStatsEvent e) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            onCdoMediaStats(e);
          }
        });

      }
    };
  }

  // ===========================================================================

  private void onCdoConnLost(final ConnectionLostEvent e) {
    final TextView status = (TextView) findViewById(R.id.text_status);
    status.setTextColor(Color.RED);
    status.setText("Connection Lost: (" + e.getErrCode() + ") "
        + e.getErrMessage());
  }

  // ===========================================================================

  private void onCdoMediaStats(MediaStatsEvent e) {
    if (e.getMediaType() != MediaType.AUDIO) {
      return;
    }
    final long userId = e.getRemoteUserId();
    final MediaStatsView statsView = _mediaStatsMap.get(userId);
    final MediaStats stats = e.getStats();
    if (!statsView.up) {
      statsView.text.setText(
          "User " + userId
              + ": kbps = " + (8.0 * stats.getBitRate() / 1000.0)
              + " #Loss = " + stats.getTotalLoss()
              + " %Loss = " + stats.getLoss());
    } else {
      statsView.text.setText(
          "kbps = " + (8.0 * stats.getBitRate() / 1000.0)
              + " RTT = " + stats.getRtt()
              + " #Loss = " + stats.getTotalLoss()
              + " %Loss = " + stats.getLoss());
    }
  }

  // ===========================================================================

  private void onCdoUserEvent(UserStateChangedEvent e) {
    final long userId = e.getUserId();
    final boolean isConnected = e.isConnected();
    final CloudeoApp tmp = this;
    final LinearLayout layout = (LinearLayout)
        findViewById(R.id.main_layout);

    if (isConnected) {
      LinearLayout.LayoutParams lparams =
          new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 30);

      TextView tv = new TextView(tmp);
      tv.setLayoutParams(lparams);

      tv.setText("User " + userId + " Stats");
      tv.setGravity(Gravity.CENTER);
      tv.setTextSize(1, 13.0f);
      tv.setTypeface(Typeface.MONOSPACE);
      tv.setSingleLine();

      layout.addView(tv);

      _mediaStatsMap.put(userId,
          new MediaStatsView(tv, false));
    } else {
      final MediaStatsView statsView =
          _mediaStatsMap.get(userId);
      layout.removeView(statsView.text);

      _mediaStatsMap.remove(userId);
    }
  }

  /**
   * ===========================================================================
   * Advanced audio settings management
   * ===========================================================================
   */

  class AdvAudioSettingsCtrl implements AdapterView.OnItemSelectedListener {

    private String enablePropertyName;
    private String modePropertyName;

    AdvAudioSettingsCtrl(String enablePropertyName, String modePropertyName) {
      this.enablePropertyName = enablePropertyName;
      this.modePropertyName = modePropertyName;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view,
                               int position, long id) {
      if (position == 0) {
        Platform.getService().setProperty(new ResponderAdapter<Void>(),
            "global.dev.audio." + enablePropertyName, "0");
      } else {
        Platform.getService().setProperty(new ResponderAdapter<Void>(),
            "global.dev.audio." + enablePropertyName, "1");

        Platform.getService().setProperty(new ResponderAdapter<Void>(),
            "global.dev.audio." + modePropertyName, "" + (position - 1));
      }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
      onItemSelected(null, null, 0, 0);
    }
  }

  private void onHeadphonePlugged(boolean plugged) {
    Spinner ec = (Spinner) findViewById(R.id.spinner_ec);
    if (plugged)
      ec.setSelection(0); // disable echo cancellation
    else
      ec.setSelection(4);
  }

  private class BroadcastHandler extends BroadcastReceiver {
    private CloudeoApp _parent;

    public BroadcastHandler(CloudeoApp parent) {
      _parent = parent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("state", -1);
      _parent.onHeadphonePlugged(state == 1);
    }
  }

}
