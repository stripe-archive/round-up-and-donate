# Round up and donate

Round up and donate features are used in checkout flows to raise money for an organization or nonprofit the business wants to support. It's easy to create your own round up and donate feature using Stripe [Connect](https://stripe.com/docs/connect) and [Payments](https://stripe.com/docs/payments). A good round up and donate experience would:

1. **👥 Onboard the organization.** Use Connect to onboard the organization as a [connected account](https://stripe.com/docs/connect/accounts) to your Stripe platform account before launching the round up feature. This creates a new Stripe account for the organization so they can accept transfers and connect their bank account to receive [payouts](https://stripe.com/docs/payouts).
2. **💶 Build round up feature.** Build a feature in your checkout flow to allow a customer to round the order up to the nearest dollar or euro.
3. **➡️ Transfer donations after each purchase.** Use Stripe to transfer the donation amount to the connected account.
4. **📧 Send itemized receipts.** Send an email receipt to your customer detailing the order total + donation amounts. If multiple purchases and donations are made, send an email at the end of the year detailing how much the customer donated throughout the year.

This sample covers steps 2 and 3: calculating the new total and using the [Transfers API](https://stripe.com/docs/api/transfers) to transfer the donation to the organization's connected Stripe account.

Note that the platform and the connected account must be in the same region (e.g. both in the U.S or both in Europe) in order to use the Transfers API.

**Demo**

[View](https://lrg4x.sse.codesandbox.io/) a hosted version of the sample or [fork](https://codesandbox.io/s/round-up-and-donate-lrg4x) the sample on CodeSandbox.

The hosted demo is running in test mode -- use `4242424242424242` as a test card number with any CVC + future expiration date.

Use the `4000000000003220` test card number to trigger a 3D Secure challenge flow.

Read more about test cards on Stripe at https://stripe.com/docs/testing.

<img src="./round-up-and-donate.gif" alt="Checkout page that lets you round up and donate to an organization" align="center">


## How to run locally
Each sample implementation includes 5 servers in Node, Ruby, Python, Java, and PHP in the /server/ directory. 

Before you run the sample, be sure to you have a Stripe account with its own set of [API keys](https://stripe.com/docs/development#api-keys).

To run the sample locally, copy the .env.example file to your own .env file: 

You will need a connected account to accept the transfers. Learn about [onboarding connected accounts](https://stripe.com/docs/connect/accounts) in the Stripe docs. 
Once you have a connected account onboarded, replace `ORGANIZATION_ACCOUNT_ID` in .env with the id of the connected account.

```
cp .env.example .env
```

## FAQ
Q: Why did you pick these frameworks?

A: We chose the most minimal framework to convey the key Stripe calls and concepts you need to understand. These demos are meant as an educational tool that helps you roadmap how to integrate Stripe within your own system independent of the framework.

Q: Can you show me how to build X?

A: We are always looking for new sample ideas, please email dev-samples@stripe.com with your suggestion!

## Author
[@adreyfus-stripe](https://twitter.com/adrind)
