const ApiGatewayHelper = require('./ApiGatewayHelper');
const AWSHelper = require('../AWSHelper');

exports.getApiGateway = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const apiGateway = await ApiGatewayHelper.getApiGateway(req.body.apiGatewayId, userCredentials, req.body.userRegion);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApiGateway = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const apiGateway = await ApiGatewayHelper.createApiGateway(req.body.apiName, req.body.description, req.body.endpointConfig, userCredentials, req.body.userRegion);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createResource = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const resource = await ApiGatewayHelper.createResource(req.body.parentPathId, req.body.gatewayId, req.body.resourcePath, userCredentials, req.body.userRegion);

        return res.status(201).send(resource);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMethod = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const method = await ApiGatewayHelper.createMethod(req.body.gatewayId, req.body.authId, req.body.authType, req.body.resourceId, req.body.httpMethod, req.body.integrationParams, userCredentials, req.body.userRegion);

        return res.status(201).send(method);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteApiGateway = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await ApiGatewayHelper.deleteApiGateway(req.body.apiGatewayId, userCredentials, req.body.userRegion);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};