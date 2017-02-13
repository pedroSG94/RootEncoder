package com.pedro.rtsp.rtsp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pedro on 13/02/17.
 */

public class Response {

    private static final String TAG = "Response";
    // Parses method & uri
    private static final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)", Pattern.CASE_INSENSITIVE);
    // Parses a request header
    private static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)", Pattern.CASE_INSENSITIVE);
    // Parses a WWW-Authenticate header
    private static final Pattern rexegAuthenticate = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"", Pattern.CASE_INSENSITIVE);
    // Parses a Session header
    private static final Pattern rexegSession = Pattern.compile("(\\d+)", Pattern.CASE_INSENSITIVE);
    // Parses a Transport header
    private static final Pattern rexegTransport = Pattern.compile("client_port=(\\d+)-(\\d+).+server_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);


    private int status;
    private HashMap<String, String> headers = new HashMap<String, String>();

    /**
     * Parse the method, URI & headers of a RTSP request
     */
    public static Response parseResponse(BufferedReader input) throws IOException, IllegalStateException, SocketException {
        Response response = new Response();
        String line;
        Matcher matcher;
        // Parsing request method & URI
        if ((line = input.readLine()) == null) throw new SocketException("Connection lost");
        matcher = regexStatus.matcher(line);
        matcher.find();
        response.status = Integer.parseInt(matcher.group(1));

        // Parsing headers of the request
        while ((line = input.readLine()) != null) {
            Log.i("Response", line);
            if (line.length() > 3) {
                matcher = rexegHeader.matcher(line);
                matcher.find();
                response.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));
            } else {
                break;
            }
        }
        if (line == null) throw new SocketException("Connection lost");
        Log.i(TAG, "Response from server: " + response.status);

        return response;
    }
}
