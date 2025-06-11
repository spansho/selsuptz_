package com.example;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import java.util.Date;

public class CrptApi {
    private final TimeUnit timeUnit;
    private CloseableHttpClient httpClient;
    private ObjectMapper objectMapper;
    private ScheduledExecutorService scheduler;
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0 || timeUnit == null) {
            throw new IllegalArgumentException("requestLimit must be positive and timeUnit not null");
        }
        this.timeUnit = timeUnit;
        this.httpClient=HttpClients.createDefault();
        this.objectMapper=new ObjectMapper();
        this.semaphore=new Semaphore(requestLimit,true);
        scheduler.scheduleAtFixedRate(
                () -> semaphore.release(requestLimit - semaphore.availablePermits()),
                0,
                1,
                timeUnit
        );
    }

    public void createDocument(Object document, String signature) throws IOException, InterruptedException, ParseException {

        semaphore.acquire();
        try (CloseableHttpResponse response = sendRequest(document, signature)) {
            int statusCode = response.getCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity, "UTF-8");
                System.out.println("Response: " + responseBody);
            } else {
                System.err.println("API error: " + statusCode);
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            httpClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CloseableHttpResponse sendRequest(Object document, String signature) throws IOException {
        HttpPost httpPost = new HttpPost(API_URL);
        String json = objectMapper.writeValueAsString(document);
        StringEntity entity = new StringEntity(json);
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Signature", signature);
        return httpClient.execute(httpPost);
    }


    @Data
    public static class Description {
        private String participantInn;
    }

    @Data
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @Data
    public static class Root {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private ArrayList<Product> products;
        private String reg_date;
        private String reg_number;


    }

    public static void main(String[] args)
            throws IOException, InterruptedException, ParseException {

        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        try {
            Root document = new Root();
            String signature = "signature";
            crptApi.createDocument(document, signature);
        }
        finally {
            crptApi.shutdown();
        }
    }
}