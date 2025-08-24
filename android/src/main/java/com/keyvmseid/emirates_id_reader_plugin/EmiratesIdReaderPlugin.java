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

// === ICP Toolkit imports (common for 3.x SDKs) ===
import com.eida.card.sdk.Toolkit;
import com.eida.card.sdk.CardReader;
import com.eida.card.sdk.response.CardPublicData;
// If your SDK exports a specific exception, you can import it; we’ll just catch Throwable below.
// import com.eida.card.sdk.ToolKitException;

public class EmiratesIdReaderPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
  private static final String TAG = "EmiratesIdReaderPlugin";
  private MethodChannel channel;
  private Context applicationContext;
  private Toolkit toolkit;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    applicationContext = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "emirates_id_reader_plugin");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPublicCardData")) {
      new ReadCardTask(result, applicationContext).execute();
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    if (toolkit != null) {
      try { toolkit.cleanup(); } catch (Throwable t) { Log.e(TAG, "Toolkit cleanup error", t); }
      toolkit = null;
    }
  }

  /** Generate a secure random 40-byte Base64 requestId */
  private String generateRequestId() {
    byte[] randomBytes = new byte[40];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.encodeToString(randomBytes, Base64.NO_WRAP);
  }

  private class ReadCardTask extends AsyncTask<Void, Void, Map<String, String>> {
    private final Result flutterResult;
    private final Context context;
    private String errorMessage;

    ReadCardTask(Result result, Context context) {
      this.flutterResult = result;
      this.context = context;
    }

    @Override
    protected Map<String, String> doInBackground(Void... voids) {
      Map<String, String> cardData = new HashMap<>();
      CardReader cardReader = null;
      try {
        String configDirectory = context.getExternalFilesDir(null) + "/EIDAToolkit";
        String logDirectory    = context.getExternalFilesDir(null) + "/EIDAToolkit/logs";
        String vgUrl           = "https://10.10.10.1/VGPreProd/ValidationGateway"; // TODO: set real VG URL

        StringBuilder params = new StringBuilder()
            .append("config_directory=").append(configDirectory).append("\n")
            .append("log_directory=").append(logDirectory).append("\n")
            .append("vg_url=").append(vgUrl).append("\n");

        // inProcessMode=true on Android
        toolkit = new Toolkit(true, params.toString(), context);

        CardReader[] readers = toolkit.listReaders();
        if (readers == null || readers.length == 0) {
          errorMessage = "No compatible ID card readers found.";
          return null;
        }

        // Pick the first available reader (SDKs differ; avoid non-portable helpers)
        cardReader = readers[0];
        cardReader.connect();

        String requestId = toolkit.prepareRequest(generateRequestId());

        CardPublicData publicData = cardReader.readPublicData(
            requestId,
            true,   // readNonModifiableData
            true,   // readModifiableData
            false,  // readPhotography
            false,  // readSignatureImage
            false   // readAddress
        );

        if (publicData != null) {
          // Avoid binding to a specific class name; use 'var' (Java 10+) so we don’t care
          var nonMod = publicData.getNonModifiablePublicData();
          if (nonMod != null) {
            cardData.put("idNumber",            safe(nonMod.getIdNumber()));
            cardData.put("cardNumber",          safe(nonMod.getCardNumber()));
            cardData.put("fullNameEnglish",     safe(nonMod.getFullNameEnglish()));
            cardData.put("fullNameArabic",      safe(nonMod.getFullNameArabic()));
            cardData.put("dateOfBirth",         safe(nonMod.getDateOfBirth()));
            cardData.put("gender",              safe(nonMod.getGender()));
            cardData.put("nationalityEnglish",  safe(nonMod.getNationalityEnglish()));
            cardData.put("expiryDate",          safe(nonMod.getExpiryDate()));
          }
        } else {
          errorMessage = "Failed to read public data from the ID card.";
        }

      } catch (Throwable t) {
        errorMessage = "EIDA SDK error: " + t.getMessage();
        Log.e(TAG, "EIDA SDK error", t);
      } finally {
        if (cardReader != null) {
          try { cardReader.disconnect(); } catch (Throwable ignore) {}
        }
      }
      return cardData;
    }

    @Override
    protected void onPostExecute(Map<String, String> cardData) {
      if (errorMessage != null) {
        flutterResult.error("ERROR", errorMessage, null);
      } else if (cardData == null || cardData.isEmpty()) {
        flutterResult.error("ERROR", "No data extracted.", null);
      } else {
        flutterResult.success(cardData);
      }
    }

    private String safe(String s) { return (s == null) ? "" : s; }
  }
}