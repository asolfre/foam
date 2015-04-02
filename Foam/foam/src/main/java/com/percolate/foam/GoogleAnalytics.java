package com.percolate.foam;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.client.Response;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.http.Body;
import retrofit.http.POST;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;
import retrofit.mime.TypedString;

/**
 * Copyright (c) 2015 Percolate Industries Inc. All rights reserved.
 * Project: Foam
 *
 * @author brent
 */
class GoogleAnalytics extends ServiceImpl implements EventTrackingService {

    private String trackingId;

    GoogleAnalytics(Context context) {
        super(context);
    }

    @Override
    public void enable(String trackingId) {
        this.trackingId = trackingId;
    }

    @Override
    public boolean isEnabled() {
        return trackingId != null;
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.GOOGLE_ANALYTICS;
    }

    public void logEvent(Context context, String activityName){
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("https://www.google-analytics.com")
                .setConverter(new StringConverter())
                .build();

        try {
            String payload = createPayloadData(activityName);
            GoogleAnalyticsService service = restAdapter.create(GoogleAnalyticsService.class);
            service.createEvent(payload, new NoOpCallback());
        } catch (Exception ex) {
            Utils.logIssue("Could not send google analytics data", ex);
        }
    }

    /**
     * See: https://developers.google.com/analytics/devguides/collection/protocol/v1/devguide
     */
    private String createPayloadData(String activityName){
        String payload = null;
        try {
            String androidId = Utils.getAndroidId(context);
            UUID deviceUUID = UUID.nameUUIDFromBytes(androidId.getBytes("utf8"));

            Map<String, String> data = new LinkedHashMap<String, String>();
            data.put("v", "1"); // Version.
            data.put("tid", this.trackingId); // Tracking ID / Property ID.
            data.put("cid", deviceUUID.toString()); // Anonymous Client ID.
            data.put("t", "screenview"); // Hit Type.
            data.put("an", Utils.getApplicationName(context)); // App name.
            data.put("av", Utils.getVersionName(context)); // App version.
            data.put("aid", Utils.getApplicationPackageName(context));  // App Id.
            data.put("cd", activityName); // Screen name / content description.

            StringBuilder sb = new StringBuilder(8192); //8192 bytes max
            for (Map.Entry<String, String> entry : data.entrySet()) {
                sb.append(entry.getKey());
                sb.append("=");
                sb.append(entry.getValue());
                sb.append("&");
            }
            sb.deleteCharAt(sb.length() - 1);// Remove last "&"

            payload = new String(sb.toString().getBytes(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Utils.logIssue("Encoding exception", ex);
        } catch(Exception ex){
            Utils.logIssue("Error creating google analytics payload data", ex);
        }
        return payload;
    }

    interface GoogleAnalyticsService {

        @POST("/collect")
        //@POST("/debug/collect")
        void createEvent(@Body String payload, Callback<Response> callback);

    }

    static class StringConverter implements Converter {
        @Override
        public Object fromBody(TypedInput typedInput, Type type) throws ConversionException {
            String text = null;
            try {
                typedInput.in();
                BufferedReader reader = new BufferedReader(new InputStreamReader(typedInput.in()));
                StringBuilder out = new StringBuilder();
                String newLine = System.getProperty("line.separator");
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                    out.append(newLine);
                }
                text = out.toString();
            } catch (IOException ignored) {/*NOP*/ }
            return text;
        }

        @Override
        public TypedOutput toBody(Object output) {
            return new TypedString(output.toString());
        }

    }

}