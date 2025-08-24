package com.keyvmseid.emirates_id_reader_plugin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import ae.emiratesid.idcard.toolkit.Toolkit;
import ae.emiratesid.idcard.toolkit.CardReader;
import ae.emiratesid.idcard.toolkit.ToolkitException;
import ae.emiratesid.idcard.toolkit.datamodel.CardPublicData;
import ae.emiratesid.idcard.toolkit.datamodel.NonModifiablePublicData;

public class EmiratesIdReaderPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
  private static final String TAG = "EmiratesIdReaderPlugin";
  private MethodChannel channel;
  private Context applicationContext;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    applicationContext = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "emirates_id_reader_plugin");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if ("getPublicCardData".equals(call.method)) {
      new ReadCardTask(result, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (channel != null) {
      channel.setMethodCallHandler(null);
      channel = null;
    }
  }

  /** Generate a secure random 40-byte Base64 requestId */
  private static String generateRequestId() {
    byte[] randomBytes = new byte[40];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.encodeToString(randomBytes, Base64.NO_WRAP);
  }

  private static class ReadCardTask extends AsyncTask<Void, Void, Map<String, Object>> {
    private final Result flutterResult;
    private final Context context;
    private String errorMessage;

    ReadCardTask(Result result, Context context) {
      this.flutterResult = result;
      this.context = context;
    }

    @Override
    protected Map<String, Object> doInBackground(Void... voids) {
      Toolkit toolkit = null;
      CardReader cardReader = null;

      try {
        // ---- Build Toolkit config (adjust keys/values to your environment) ----
        String configDirectory = context.getExternalFilesDir(null) + "/EIDAToolkit";
        String logDirectory    = context.getExternalFilesDir(null) + "/EIDAToolkit/logs";
        String vgUrl           = "https://10.10.10.1/VGPreProd/ValidationGateway"; // TODO: put the real VG URL

        StringBuilder params = new StringBuilder()
            .append("config_directory=").append(configDirectory).append("\n")
            .append("log_directory=").append(logDirectory).append("\n")
            .append("vg_url=").append(vgUrl).append("\n");
        // add other vendor-required flags here, e.g. read_publicdata_offline=true

        // ---- Init & connect Toolkit ----
        toolkit = new Toolkit(true, params.toString(), context);
        toolkit.connect();

        // ---- Find a reader ----
        CardReader[] readers = toolkit.listReaders();
        if (readers == null || readers.length == 0) {
          errorMessage = "No compatible ID card readers found.";
          return null;
        }

        cardReader = readers[0];
        cardReader.connect();

        // ---- Prepare request + read public data ----
        String requestId = toolkit.prepareRequest(generateRequestId());

        CardPublicData pub = cardReader.readPublicData(
            requestId,
            true,   // readNonModifiableData
            true,   // readModifiableData
            true,   // readPhotography (set true if you want photo; false to skip)
            false,  // readSignatureImage
            false   // readAddress
        );

        if (pub == null) {
          errorMessage = "Failed to read public data from the ID card.";
          return null;
        }

        // ---- Map out result ----
        Map<String, Object> cardData = new HashMap<>();

        // From CardPublicData (per your AAR): idNumber + cardNumber + optional photo
        cardData.put("idNumber",   safe(pub.getIdNumber()));
        cardData.put("cardNumber", safe(pub.getCardNumber()));

        try {
          byte[] photo = pub.getCardHolderPhoto(); // may be null if not requested/available
          if (photo != null && photo.length > 0) {
            cardData.put("photoBase64", Base64.encodeToString(photo, Base64.NO_WRAP));
          }
        } catch (Throwable ignore) { /* optional */ }

        // From NonModifiablePublicData: names, nationality, dob, gender, expiry, etc.
        NonModifiablePublicData nonMod = pub.getNonModifiablePublicData();
        if (nonMod != null) {
          // Names
          putIfNotEmpty(cardData, "fullNameEnglish",  nonMod.getFullNameEnglish());
          putIfNotEmpty(cardData, "fullNameArabic",   nonMod.getFullNameArabic());
          // Nationality
          putIfNotEmpty(cardData, "nationalityEnglish", nonMod.getNationalityEnglish());
          putIfNotEmpty(cardData, "nationalityArabic",  nonMod.getNationalityArabic());
          putIfNotEmpty(cardData, "nationalityCode",    nonMod.getNationalityCode());
          // Demographics
          putIfNotEmpty(cardData, "dateOfBirth", nonMod.getDateOfBirth());
          putIfNotEmpty(cardData, "gender",      nonMod.getGender());
          putIfNotEmpty(cardData, "expiryDate",  nonMod.getExpiryDate());
          // There are more fields (placeOfBirth, titles, etc.) in this class if needed.
        }

        // success
        return cardData;

      } catch (Throwable t) {
        errorMessage = "EIDA SDK error: " + (t.getMessage() == null ? t.toString() : t.getMessage());
        Log.e(TAG, "EIDA SDK error", t);

      } finally {
        try { if (cardReader != null) cardReader.disconnect(); } catch (Throwable ignore) {}
        try { if (toolkit != null) toolkit.disconnect(); } catch (Throwable ignore) {}
        try { if (toolkit != null) toolkit.cleanup(); } catch (Throwable ignore) {}
      }

      return null;
    }

    @Override
    protected void onPostExecute(Map<String, Object> cardData) {
      if (errorMessage != null) {
        flutterResult.error("ERROR", errorMessage, null);
      } else if (cardData == null || cardData.isEmpty()) {
        flutterResult.error("ERROR", "No data extracted.", null);
      } else {
        flutterResult.success(cardData);
      }
    }

    private static void putIfNotEmpty(Map<String, Object> map, String key, String value) {
      if (value != null && !value.isEmpty()) map.put(key, value);
    }

    private static String safe(String s) { return (s == null) ? "" : s; }
  }
}