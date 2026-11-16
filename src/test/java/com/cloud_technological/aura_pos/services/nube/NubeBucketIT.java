package com.cloud_technological.aura_pos.services.nube;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Prueba manual contra el bucket real aura-nube: solo corre si están las
 * variables R2_NUBE_* (no corre en CI). Sube, lee y borra un objeto de 1 byte.
 */
@EnabledIfEnvironmentVariable(named = "R2_NUBE_ACCESS_KEY_ID", matches = ".+")
class NubeBucketIT {

    private static final String ENDPOINT = "https://56109a941c2f5386b35510659185b275.r2.cloudflarestorage.com";

    @Test
    void el_token_puede_escribir_leer_y_borrar() {
        String bucket = System.getenv().getOrDefault("R2_NUBE_BUCKET", "aura-nube");
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        System.getenv("R2_NUBE_ACCESS_KEY_ID"), System.getenv("R2_NUBE_SECRET_ACCESS_KEY"))))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            String key = "nube/_salud/" + UUID.randomUUID();
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(new byte[] { 7 }));
            byte[] leido = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            assertArrayEquals(new byte[] { 7 }, leido);
        }
    }
}
