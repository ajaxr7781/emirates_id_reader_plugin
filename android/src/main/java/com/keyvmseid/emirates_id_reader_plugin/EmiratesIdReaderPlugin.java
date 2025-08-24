package com.keyvmseid.emirates_id_reader_plugin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Base64;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import ae.emiratesid.idcard.toolkit.CardPublicData;
import ae.emiratesid.idcard.toolkit.CardReader;
import ae.emiratesid.idcard.toolkit.Toolkit;
import ae.emiratesid.idcard.toolkit.ToolkitException;
import ae.emiratesid.idcard.toolkit.models.NonModifiablePublicData;

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
      try {
        toolkit.cleanup();
      } catch (ToolkitException e) {
        Log.e(TAG, "Error cleaning up Toolkit: " + e.getMessage());
      }
      toolkit = null;
    }
  }

  /** Generate a secure random 40-byte Base64 encoded requestId */
  private String generateRequestId() {
    byte[] randomBytes = new byte[40];
    new SecureRandom().nextBytes(randomBytes);
    return Base64.encodeToString(randomBytes, Base64.NO_WRAP);
  }

  /** Background task to read ID card data */
  private class ReadCardTask extends AsyncTask<Void, Void, Map<String, String>> {
    private Result flutterResult;
    private Context context;
    private String errorMessage;

    public ReadCardTask(Result result, Context context) {
      this.flutterResult = result;
      this.context = context;
    }

    @Override
    protected Map<String, String> doInBackground(Void... voids) {
      Map<String, String> cardData = new HashMap<>();
      CardReader cardReader = null;
      try {
        String configDirectory = context.getExternalFilesDir(null) + "/EIDAToolkit";
        String logDirectory = context.getExternalFilesDir(null) + "/EIDAToolkit/logs";
        String vgUrl = "https://10.10.10.1/VGPreProd/ValidationGateway";

        StringBuilder configParamsBuilder = new StringBuilder();
        configParamsBuilder.append("config_directory=").append(configDirectory).append("\n");
        configParamsBuilder.append("log_directory=").append(logDirectory).append("\n");
        configParamsBuilder.append("vg_url=").append(vgUrl).append("\n");

        toolkit = new Toolkit(true, configParamsBuilder.toString(), context);

        CardReader[] readers = toolkit.listReaders();
        if (readers == null || readers.length == 0) {
          errorMessage = "No compatible ID card readers found.";
          return null;
        }

        cardReader = toolkit.getReaderWithEmiratesId();
        if (cardReader == null) {
          errorMessage = "No Emirates ID card found in reader.";
          return null;
        }

        cardReader.connect();

        // 🔑 Generate secure requestId here
        String requestId = toolkit.prepareRequest(generateRequestId());

        CardPublicData publicData = cardReader.readPublicData(
                requestId,
                true,   // Non-modifiable data
                true,   // Modifiable data
                false,  // Photo
                false,  // Signature
                false   // Address
        );

        if (publicData != null) {
          NonModifiablePublicData nonModifiableData = publicData.getNonModifiablePublicData();
          if (nonModifiableData != null) {
            cardData.put("idNumber", nonModifiableData.getIdNumber());
            cardData.put("cardNumber", nonModifiableData.getCardNumber());
            cardData.put("fullNameEnglish", nonModifiableData.getFullNameEnglish());
            cardData.put("fullNameArabic", nonModifiableData.getFullNameArabic());
            cardData.put("dateOfBirth", nonModifiableData.getDateOfBirth());
            cardData.put("gender", nonModifiableData.getGender());
            cardData.put("nationalityEnglish", nonModifiableData.getNationalityEnglish());
            cardData.put("expiryDate", nonModifiableData.getExpiryDate());
          }
        } else {
          errorMessage = "Failed to read public data.";
        }

      } catch (Exception e) {
        errorMessage = "Error: " + e.getMessage();
        Log.e(TAG, "Exception: " + e.getMessage(), e);
      } finally {
        if (cardReader != null) {
          try { cardReader.disconnect(); } catch (Exception ignored) {}
        }
      }
      return cardData;
    }

    @Override
    protected void onPostExecute(Map<String, String> cardData) {
      if (errorMessage != null) {
        flutterResult.error("ERROR", errorMessage, null);
      } else if (cardData.isEmpty()) {
        flutterResult.error("ERROR", "No data extracted.", null);
      } else {
        flutterResult.success(cardData);
      }
    }
  }
}
