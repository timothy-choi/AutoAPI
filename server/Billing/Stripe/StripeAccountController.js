const stripeAccountService = require('./StripeAccountHelper');

exports.createStripeAccount = async (req, res) => {
    try {
        var acct = stripeAccountService.createStripeAccount(req.body);

        return res.status(201).send(acct);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.generateOnboardingLink = async (req, res) => {
    try {
        var onboardingLink = stripeAccountService.generateOnboardingLink(req.body);

        return res.status(201).send(onboardingLink);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};