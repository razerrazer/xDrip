package com.eveningoutpost.dexdrip.tidepool;

// jamorham

// Manages the session data

import okhttp3.Headers;

public class Session {

    private final String SESSION_TOKEN_HEADER;
    final TidePoolUploader.TidePool service = TidePoolUploader.getRetrofitInstance().create(TidePoolUploader.TidePool.class);
    final String authHeader;

    String token;
    MAuthReply authReply;
    MStartReply MStartReply;
    long start;
    long end;
    volatile int iterations;


    Session(String authHeader, String session_token_header) {
        this.authHeader = authHeader;
        this.SESSION_TOKEN_HEADER = session_token_header;
    }

    void populateHeaders(final Headers headers) {
        if (this.token == null) {
            this.token = headers.get(SESSION_TOKEN_HEADER);
        }
    }

    void populateBody(final Object obj) {
        if (obj == null) return;
        if (obj instanceof MAuthReply) {
            authReply = (MAuthReply) obj;
        } else if (obj instanceof MStartReply) {
            MStartReply = (MStartReply) obj;
        }
    }

    boolean exceededIterations() {
        return iterations > 50;
    }

}
