// A reference to Stripe.js
var stripe;

var orderData = {
  items: [{ id: "photo-subscription" }],
  currency: "usd"
};

fetch("/create-payment-intent", {
  method: "POST",
  headers: {
    "Content-Type": "application/json"
  },
  body: JSON.stringify(orderData)
})
  .then(function(result) {
    return result.json();
  })
  .then(function(data) {
    return setupElements(data);
  })
  .then(function(stripeData) {
    document.querySelector("#submit").addEventListener("click", function(evt) {
      evt.preventDefault();
      // Initiate payment
      pay(stripeData.stripe, stripeData.card, stripeData.clientSecret);
    });

    document
      .querySelector('input[type="checkbox"]')
      .addEventListener("change", function(evt) {
        handleCheckboxEvent(stripeData.id, evt.target.checked);
      });
  });

// Set up Stripe.js and Elements to use in checkout form
var setupElements = function(data) {
  stripe = Stripe(data.publicKey);
  console.log("data", data);
  var elements = stripe.elements();
  var style = {
    base: {
      color: "#32325d",
      fontFamily: '"Helvetica Neue", Helvetica, sans-serif',
      fontSmoothing: "antialiased",
      fontSize: "16px",
      "::placeholder": {
        color: "#aab7c4"
      }
    },
    invalid: {
      color: "#fa755a",
      iconColor: "#fa755a"
    }
  };

  var card = elements.create("card", { style: style });
  card.mount("#card-element");

  return {
    stripe,
    card,
    clientSecret: data.paymentIntent.client_secret,
    id: data.paymentIntent.id
  };
};

/*
 * Calls stripe.handleCardPayment which creates a pop-up modal to
 * prompt the user to enter  extra authentication details without leaving your page
 */
var pay = function(stripe, card, clientSecret) {
  var cardholderEmail = document.querySelector("#email").value;

  var data = {
    billing_details: {}
  };

  if (cardholderEmail) {
    data["billing_details"]["email"] = cardholderEmail;
  }

  changeLoadingState(true);

  // Initiate the payment.
  // If authentication is required, handleCardPayment will display a modal
  stripe
    .handleCardPayment(clientSecret, card, {
      payment_method_data: data,
      receipt_email: cardholderEmail
    })
    .then(function(result) {
      if (result.error) {
        changeLoadingState(false);
        var errorMsg = document.querySelector(".sr-field-error");
        errorMsg.textContent = result.error.message;
        setTimeout(function() {
          errorMsg.textContent = "";
        }, 4000);
      } else {
        orderComplete(clientSecret);
      }
    });
};

var handleCheckboxEvent = function(id, isDonating) {
  changeLoadingState(true);

  const orderData = {
    isDonating: isDonating,
    email: document.getElementById("email").value,
    id: id
  };
  fetch("/update-payment-intent", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(orderData)
  })
    .then(function(response) {
      return response.json();
    })
    .then(function(data) {
      changeLoadingState(false);
      updateTotal(data.amount);
    });
};

/* ------- Post-payment helpers ------- */

/* Shows a success / error message when the payment is complete */
var orderComplete = function(clientSecret) {
  stripe.retrievePaymentIntent(clientSecret).then(function(result) {
    var paymentIntent = result.paymentIntent;
    var paymentIntentJson = JSON.stringify(paymentIntent, null, 2);
    document.querySelectorAll(".payment-view").forEach(function(view) {
      view.classList.add("hidden");
    });
    document.querySelectorAll(".completed-view").forEach(function(view) {
      view.classList.remove("hidden");
    });
    document.querySelector("pre").textContent = paymentIntentJson;
  });
};

// Show a spinner on payment submission
var changeLoadingState = function(isLoading) {
  if (isLoading) {
    document.querySelector("button").disabled = true;
    document.querySelector("#spinner").classList.remove("hidden");
    document.querySelector("#button-text").classList.add("hidden");
  } else {
    document.querySelector("button").disabled = false;
    document.querySelector("#spinner").classList.add("hidden");
    document.querySelector("#button-text").classList.remove("hidden");
  }
};

var updateTotal = function(newAmount) {
  document.querySelector(".order-amount").textContent =
    "$" + (newAmount / 100).toFixed(2);
  if (newAmount === 1400) {
    document.querySelector(".donation-text").classList.remove("hidden");
  } else {
    document.querySelector(".donation-text").classList.add("hidden");
  }
};
