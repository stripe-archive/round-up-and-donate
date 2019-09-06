<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

$ENV_PATH = '../..';

$dotenv = Dotenv\Dotenv::create(realpath($ENV_PATH));
$dotenv->load();

require './config.php';

if (PHP_SAPI == 'cli-server') {
  $_SERVER['SCRIPT_NAME'] = '/index.php';
}

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});

$app->get('/', function (Request $request, Response $response, array $args) {   
  // Display checkout page
  return $response->write(file_get_contents(getenv('STATIC_DIR') . '/index.html'));
});

function calculateOrderAmount($isDonating)
{
  // Replace this constant with a calculation of the order's amount
  // Calculate the order total on the server to prevent
  // people from directly manipulating the amount on the client
  return $isDonating ? 1400 : 1354;
}

$app->post('/create-payment-intent', function (Request $request, Response $response, array $args) {
    $pub_key = getenv('STRIPE_PUBLIC_KEY');
    $body = json_decode($request->getBody());

    // Required if we want to transfer part of the payment as a donation
    // A transfer group is a unique ID that lets you associate transfers with the original payment
    $transfer_group = "group_" . rand(0, 10000);

    // Create a PaymentIntent with the order amount and currency
    $payment_intent = \Stripe\PaymentIntent::create([
      "amount" => calculateOrderAmount(false),
      "currency" => $body->currency,
      "transfer_group" => $transfer_group
    ]);
    
    // Send public key and PaymentIntent details to client
    return $response->withJson(array('publicKey' => $pub_key, 'paymentIntent' => $payment_intent));
});

$app->post('/update-payment-intent', function (Request $request, Response $response, array $args) {
  $organization_account_id = getenv('ORGANIZATION_ACCOUNT_ID');
  $body = json_decode($request->getBody());

  $payment_intent = \Stripe\PaymentIntent::retrieve($body->id);

  if($body->isDonating) {
    // Add metadata to track the amount being donated
    $metadata = ["donationAmount" => 46, "organizationAccountId" => $organization_account_id];
  } else {
    $metadata = ["donationAmount" => 0, "organizationAccountId" => ""];
  }

  // Create a PaymentIntent with the order amount and currency
  $updated_payment_intent = \Stripe\PaymentIntent::update($body->id, [
    "amount" => calculateOrderAmount($body->isDonating),
    "metadata" => $metadata
  ]);
  
  // Send public key and PaymentIntent details to client
  return $response->withJson(array('amount' => $updated_payment_intent->amount));
});


$app->post('/webhook', function(Request $request, Response $response) {
    $logger = $this->get('logger');
    $event = $request->getParsedBody();
    // Parse the message body (and check the signature if possible)
    $webhookSecret = getenv('STRIPE_WEBHOOK_SECRET');
    if ($webhookSecret) {
      try {
        $event = \Stripe\Webhook::constructEvent(
          $request->getBody(),
          $request->getHeaderLine('stripe-signature'),
          $webhookSecret
        );
      } catch (\Exception $e) {
        return $response->withJson([ 'error' => $e->getMessage() ])->withStatus(403);
      }
    } else {
      $event = $request->getParsedBody();
    }
    $type = $event['type'];
    $object = $event['data']['object'];
    
    if ($type == 'payment_intent.succeeded') {
      if ($object->metadata->donationAmount) {
        // Customer made a donation
        // Use Stripe Connect to transfer funds to organization's Stripe account
        $transfer = \Stripe\Transfer::create([
          "amount" => $object->metadata->donationAmount,
          "currency" => "usd",
          "destination" => $object->metadata->organizationAccountId,
          "transfer_group" => $object->transfer_group  
        ]);

        $logger->info('😀 Customer donated ' . $transfer->amount . ' to ' . $transfer->destination . ' send them a thank you email at ' . $object->receipt_email . ' !');
      } else {
        $logger->info('😶 Payment received -- customer did not donate.');
      }
    } else if ($type == 'payment_intent.payment_failed') {
      $logger->info('❌ Payment failed.');
    }

    return $response->withJson([ 'status' => 'success' ])->withStatus(200);
});

$app->run();
