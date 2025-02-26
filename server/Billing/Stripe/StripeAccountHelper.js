const Stripe = require('stripe');
require('dotenv').config();

const stripe = Stripe(process.env.STRIPE_SECRET_KEY);

exports.createStripeAccount = async (userInfo) => {
    if (userInfo.StripeAccountId) {
        throw new Error('User already has a Stripe account');
    }

    const account = await stripe.accounts.create({
        type: userInfo.acctType, 
        country: userInfo.country, 
        email: user.email,
        capabilities: {
            card_payments: { requested: true },
            transfers: { requested: true },
        },
    });

    return account;
};

exports.generateOnboardingLink = async (userInfo) => {
    if (!userInfo.StripeAccountId) {
        throw new Error('User does not have a Stripe account');
    }

    const accountLink = await stripe.accountLinks.create({
        account: userInfo.StripeAccountId,
        refresh_url: '',
        return_url: '', 
        type: 'account_onboarding',
    });

    return accountLink.url;
};