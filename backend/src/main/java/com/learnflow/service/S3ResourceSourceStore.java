package com.learnflow.service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;

public class S3ResourceSourceStore implements ResourceSourceStore, AutoCloseable {
    private final S3Client client;
    private final String bucket;

    public S3ResourceSourceStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String put(String key, InputStream input, long contentLength, String contentType) {
        client.putObject(builder -> builder.bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(input, contentLength));
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(builder -> builder.bucket(bucket).key(key));
            return stream;
        } catch (RuntimeException failure) {
            throw new IOException("Unable to open resource source object", failure);
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(builder -> builder.bucket(bucket).key(key));
    }

    @Override public void close() { client.close(); }
}
