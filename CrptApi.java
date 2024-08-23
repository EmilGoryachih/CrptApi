package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestCount;
    private final int requestLimit;
    private final TimeUnit timeUnit;

    // Конструктор с указанием лимита запросов и интервала времени
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestCount = new AtomicInteger(0);
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // Сбрасываем счетчик запросов по истечении каждого интервала времени
        scheduler.scheduleAtFixedRate(() -> requestCount.set(0), 0, 1, timeUnit);
    }

    // Метод для создания документа
    public String createDocument(Document document, String signature) throws IOException, InterruptedException {
        // Блокируем выполнение, если лимит запросов достигнут
        if (requestCount.incrementAndGet() > requestLimit) {
            synchronized (this) {
                while (requestCount.get() > requestLimit) {
                    this.wait(timeUnit.toMillis(1));
                }
            }
        }

        // Преобразуем документ в JSON
        String requestBody = objectMapper.writeValueAsString(document);

        // Создаем запрос
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Выполняем запрос и получаем ответ
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        synchronized (this) {
            this.notifyAll();
        }

        return response.body();
    }

    // Класс для представления документа
    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        // Вложенный класс для описания
        public static class Description {
            public String participantInn;
        }

        // Вложенный класс для продукта
        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    // Пример использования класса в методе main
    public static void main(String[] args) {
        // Создаем экземпляр CrptApi с ограничением в 5 запросов в минуту
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5);

        // Создаем объект документа
        Document document = new Document();
        document.doc_id = "12345";
        document.doc_status = "NEW";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "1234567890";
        document.participant_inn = "0987654321";
        document.producer_inn = "1122334455";
        document.production_date = "2024-01-01";
        document.production_type = "manufactured";
        document.reg_date = "2024-01-02";
        document.reg_number = "56789";

        Document.Description description = new Document.Description();
        description.participantInn = "111222333444";
        document.description = description;

        Document.Product product = new Document.Product();
        product.certificate_document = "doc_cert";
        product.certificate_document_date = "2024-01-01";
        product.certificate_document_number = "cert123";
        product.owner_inn = "1234567890";
        product.producer_inn = "0987654321";
        product.production_date = "2024-01-01";
        product.tnved_code = "1234";
        product.uit_code = "5678";
        product.uitu_code = "91011";
        document.products = new Document.Product[]{product};

        // Подпись для документа
        String signature = "sample_signature";

        // Выполняем вызов метода createDocument
        try {
            String response = crptApi.createDocument(document, signature);
            System.out.println("Response: " + response);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
