package com.learnflow.service;

import java.io.IOException;
import java.io.InputStream;

public interface ResourceSourceStore {
    String put(String key, InputStream input, long contentLength, String contentType) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key) throws IOException;
}
