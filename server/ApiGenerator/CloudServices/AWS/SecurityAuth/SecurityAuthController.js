const SecurityAuthService = require('./SecurityAuthService');
const AWSHelper = require('../AWSHelper');

exports.CreateApiKey = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var apiKey = await SecurityAuthService.createApiKey(req.body.gatewayId, req.body.apiKey, userCredentials, req.body.userRegion);

        return res.status(201).send(apiKey);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateUsagePlan = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var usagePlan = await SecurityAuthService.createUsagePlan(req.body.usagePlanName, req.body.limitInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(usagePlan);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.LinkApiKeyToUsagePlan = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var linkResponse = await SecurityAuthService.linkApiKeyToUsagePlan(req.body.usagePlanId, req.body.apiKeyId, userCredentials, req.body.userRegion);

        return res.status(201).send(linkResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteApiKey = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var deleteResponse = await SecurityAuthService.deleteApiKey(req.body.apiKeyId, userCredentials, req.body.userRegion);

        return res.status(200).send(deleteResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};