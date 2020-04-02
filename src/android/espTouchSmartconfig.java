package io.kiot.smartconfig;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchListener;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.security.TouchAES;

//import org.apache.cordova.CallbackContext;
//import org.apache.cordova.CordovaPlugin;
//import org.apache.cordova.PluginResult;
import org.apache.cordova.*;

import android.net.wifi.WifiManager;
import android.net.wifi.ScanResult;
import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class espTouchSmartconfig extends CordovaPlugin {
  private static final String ACTION_REQUEST_LOCATION_PERMISSION = "requestLocationPermission";
  private static final String ACTION_STOP_CONFIG = "stopConfig";
  private static final String ACTION_START_CONFIG = "startConfig";
  private static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
  private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
  private static final int LOCATION_REQUEST_CODE = 1;
  private static final int START_SMART_CONFIG_CODE = 2;

  private WifiManager wifiManager;
  CallbackContext receivingCallbackContext = null;
  JSONArray receivedArgs;
  IEsptouchTask mEsptouchTask = null;
  private static final String TAG = "espTouchSmartconfig";

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    this.wifiManager = (WifiManager) cordova.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
  }

  @Override
  public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext)
    throws JSONException {
    receivingCallbackContext = callbackContext; // modified by lianghuiyuan
    this.receivedArgs = args;
    if (action.equals(ACTION_START_CONFIG)) {
      return this.startSmartConfig(callbackContext, args);
    } else if (action.equals(ACTION_STOP_CONFIG)) {
      return this.stopSmartConfig(callbackContext);
    } else if (action.equals(ACTION_REQUEST_LOCATION_PERMISSION)) {
      this.requestLocationPermission(LOCATION_REQUEST_CODE);
      return true;
    }
    else {
      callbackContext.error("can not find the function " + action);
      return false;
    }
  }

  private boolean startSmartConfig(CallbackContext callbackContext, final JSONArray args) throws JSONException {
    // Check for permission. If permission not yet granted than request permission.
    if(!cordova.hasPermission(ACCESS_COARSE_LOCATION)){
      Log.d(TAG, "Will request permission");
      requestLocationPermission(START_SMART_CONFIG_CODE);
      return true;
    }
    Log.d(TAG, "Will start smart config ");
    final String apSsid = args.getString(0);
    final String apBssid = args.getString(1);
    final String apPassword = args.getString(2);
    final String broadcast = args.getString(3);
    final String taskResultCountStr = args.getString(4);
    final String encryptionKey = args.getString(5);
    final int taskResultCount = Integer.parseInt(taskResultCountStr);
    final Object mLock = new Object();
    final boolean useEncryption = encryptionKey.length()> 0;


    cordova.getThreadPool().execute(new Runnable() {
                                      public void run() {
                                        synchronized (mLock) {
                                          byte[] encKey;
                                          cancelEspTouchTask();
                                          if(useEncryption){
                                            encKey =  encryptionKey.getBytes();
                                            TouchAES encryptor = new TouchAES(encKey);
                                            mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, encryptor, cordova.getActivity());
                                          }else{
                                            mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, cordova.getActivity());
                                          }
                                          mEsptouchTask.setPackageBroadcast(Integer.parseInt(broadcast) == 1);
                                          mEsptouchTask.setEsptouchListener(myListener);

                                        }
                                        List<IEsptouchResult> resultList = mEsptouchTask.executeForResults(taskResultCount);
                                        IEsptouchResult firstResult = resultList.get(0);
                                        if (!firstResult.isCancelled()) {
                                          int count = 0;
                                          final int maxDisplayCount = taskResultCount;
                                          if (firstResult.isSuc()) {
                                            JSONArray resultArray = new JSONArray();
                                            for (IEsptouchResult resultInList : resultList) {
                                              JSONObject resultObj = new JSONObject();
                                              try {
                                                resultObj.put("id", count);
                                                resultObj.put("bssid", resultInList.getBssid());
                                                resultObj.put("ip", resultInList.getInetAddress().getHostAddress());
                                                resultArray.put(count, resultObj);
                                              } catch (JSONException e) {
                                                e.printStackTrace();
                                                Log.e(TAG, e.toString());
                                                sendPluginCallbackResult(PluginResult.Status.ERROR, "An unexpected error occured");
                                              }
                                              count++;
                                              if (count >= maxDisplayCount) {
                                                break;
                                              }
                                            }
                                            if (count < resultList.size()) {
                                              Log.d(TAG, "\nthere's " + (resultList.size() - count)
                                                + " more resultList(s) without showing\n");
                                            }
                                            sendPluginCallbackResult(PluginResult.Status.OK, resultArray.toString());
                                          } else {
                                            sendPluginCallbackResult(PluginResult.Status.ERROR, "No Device Found!");
                                          }
                                        }
                                      }
                                    }// end runnable
    );
    return true;
  }

  private boolean stopSmartConfig(CallbackContext callbackContext) {
    cancelEspTouchTask();
    sendPluginCallbackResult(PluginResult.Status.OK, "Cancel Success");
    return true;
  }

  private void cancelEspTouchTask(){
    if(mEsptouchTask != null){
      mEsptouchTask.interrupt();
      mEsptouchTask = null;
    }
  }

  private void sendPluginCallbackResult(PluginResult.Status status, String message) {
    PluginResult result = new PluginResult(status, message);
    result.setKeepCallback(true); // keep callback after this call
    receivingCallbackContext.sendPluginResult(result);
  }

  private boolean isSDKAtLeastP() {
    return Build.VERSION.SDK_INT >= 28;
  }
  /**
   * Handle Android Permission Requests
   */
  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
    throws JSONException {

    for (int r : grantResults) {
      if (r == PackageManager.PERMISSION_DENIED) {
        receivingCallbackContext.error( "PERMISSION_DENIED" );
        return;
      }
    }

    switch (requestCode) {
      case START_SMART_CONFIG_CODE:
        startSmartConfig(receivingCallbackContext, receivedArgs); // Call method again after permissions approved
        break;
      case LOCATION_REQUEST_CODE:
        receivingCallbackContext.success("PERMISSION_GRANTED");
        break;
    }
  }


  /**
   * Request ACCESS_FINE_LOCATION Permission
   *
   * @param requestCode
   */
  protected void requestLocationPermission(int requestCode) {
    // TODO: Ask for FINE_LOCATION as done in Wifiwizard.
    cordova.requestPermission(this, requestCode, ACCESS_COARSE_LOCATION);
  }

  // listener to get result
  // Not useful. Not doing anything. 
  private IEsptouchListener myListener = new IEsptouchListener() {
    @Override
    public void onEsptouchResultAdded(final IEsptouchResult result) {
      String text = "bssid=" + result.getBssid() + ",InetAddress=" + result.getInetAddress().getHostAddress();
      PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, text);
      pluginResult.setKeepCallback(true); // keep callback after this call
      // receivingCallbackContext.sendPluginResult(pluginResult); //modified by
      // lianghuiyuan
    }
  };
}
