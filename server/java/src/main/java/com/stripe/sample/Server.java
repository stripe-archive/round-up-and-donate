package com.stripe.sample;

import java.nio.file.Paths;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;
import static spark.Spark.port;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import com.stripe.Stripe;
import com.stripe.net.ApiResource;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.exception.*;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentUpdateParams;
import com.stripe.param.TransferCreateParams;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();

    static class CreatePaymentBody {
        @SerializedName("items")
        Object[] items;

        @SerializedName("currency")
        String currency;

        public Object[] getItems() {
            return items;
        }

        public String getCurrency() {
            return currency;
        }
    }

    static class CreatePaymentResponse {
        private String publicKey;
        private PaymentIntent paymentIntent;

        public CreatePaymentResponse(String publicKey, PaymentIntent paymentIntent) {
            this.publicKey = publicKey;
            this.paymentIntent = paymentIntent;
        }
    }

    static class UpdatePaymentBody {
        @SerializedName("isDonating")
        Boolean isDonating;

        @SerializedName("id")
        String id;

        public Boolean getIsDonating() {
            return isDonating;
        }

        public String getId() {
            return id;
        }
    }

    static class UpdatePaymentResponse {
        private Long amount;

        public UpdatePaymentResponse(Long amount) {
            this.amount = amount;
        }
    }

    static int calculateOrderAmount(Boolean isDonating) {
        // Replace this constant with a calculation of the order's amount
        // Calculate the order total on the server to prevent
        // users from directly manipulating the amount on the client
        return (isDonating == true) ? 1400 : 1354;
    }

    public static void main(String[] args) {
        port(4242);
        String ENV_PATH = "../../";
        Dotenv dotenv = Dotenv.configure().directory(ENV_PATH).load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        post("/create-payment-intent", (request, response) -> {
            response.type("application/json");

            Random rand = new Random();
            // Required if we want to transfer part of the payment as a donation
            // A transfer group is a unique ID that lets you associate transfers with the
            // original payment
            String transferGroup = "group_" + Integer.toString(rand.nextInt(10000));

            CreatePaymentBody postBody = gson.fromJson(request.body(), CreatePaymentBody.class);
            PaymentIntentCreateParams createParams = new PaymentIntentCreateParams.Builder()
                    .setCurrency(postBody.getCurrency()).setAmount(new Long(calculateOrderAmount(false)))
                    .setTransferGroup(transferGroup).build();

            // Create a PaymentIntent with the order amount and currency
            PaymentIntent intent = PaymentIntent.create(createParams);
            // Send public key and PaymentIntent details to client
            return gson.toJson(new CreatePaymentResponse(dotenv.get("STRIPE_PUBLIC_KEY"), intent));
        });

        post("/update-payment-intent", (request, response) -> {
            response.type("application/json");
            UpdatePaymentBody postBody = gson.fromJson(request.body(), UpdatePaymentBody.class);
            Boolean isDonating = postBody.getIsDonating();

            // Add metadata to the PaymentIntent to track the amount being donated
            Map<String, String> metadata = new HashMap<>();
            metadata.put("donationAmount", isDonating ? "46" : "0");
            metadata.put("organizationAccountId", isDonating ? dotenv.get("ORGANIZATION_ACCOUNT_ID") : "");

            PaymentIntentUpdateParams updateParams = new PaymentIntentUpdateParams.Builder()
                    .setAmount(new Long(calculateOrderAmount(postBody.getIsDonating()))).putAllMetadata(metadata)
                    .build();

            PaymentIntent intent = PaymentIntent.retrieve(postBody.getId());
            // Create a PaymentIntent with the order amount and currency
            PaymentIntent updatedIntent = intent.update(updateParams);
            // Send public key and PaymentIntent details to client
            return gson.toJson(new UpdatePaymentResponse(updatedIntent.getAmount()));
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }

            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            PaymentIntent intent = ApiResource.GSON.fromJson(deserializer.getRawJson(), PaymentIntent.class);

            switch (event.getType()) {

            case "payment_intent.succeeded":
                long donationAmount = intent.getMetadata().get("donationAmount") == null ? 0
                        : Long.parseLong(intent.getMetadata().get("donationAmount"));
                if (donationAmount > 0) {
                    // Customer made a donation
                    // Use Stripe Connect to transfer funds to organization's Stripe account
                    TransferCreateParams createParams = new TransferCreateParams.Builder().setCurrency("usd")
                            .setTransferGroup(intent.getTransferGroup())
                            .setDestination(intent.getMetadata().get("organizationAccountId"))
                            .setAmount(new Long(intent.getMetadata().get("donationAmount"))).build();
                    Transfer transfer = Transfer.create(createParams);
                    System.out.println("😀 Customer donated " + Long.toString(transfer.getAmount()) + " to "
                            + transfer.getDestination() + "send them a thank you email at " + intent.getReceiptEmail());
                } else {
                    System.out.println("😶 Payment received -- customer did not donate.");
                }
                break;
            case "payment_intent.payment_failed":
                System.out.println("❌ Payment failed.");
                break;
            default:
                // Unexpected event type
                response.status(400);
                return "";
            }

            response.status(200);
            return "";
        });
    }
}