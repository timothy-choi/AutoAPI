const ApiSecurityAuthService = require('./SecurityAuthService');

exports.CreateApiKey = async (req, res) => {
    try {
        var apiKey = await ApiSecurityAuthService.createAzureApiKey(req.body.userId, req.body.productId, req.body.resourceGroup, req.body.serviceName, req.body.subscriptionId);

        return res.status(201).send(apiKey);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteApiKey = async (req, res) => {
    try {
        await ApiSecurityAuthService.deleteAzureApiKey(req.body.resourceGroup, req.body.serviceName, req.body.subscriptionId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateAzureJwtToken = async (req, res) => {
    try {
        var jwtToken = await ApiSecurityAuthService.createAzureJwtToken(req.body.scope, req.body.username, req.body.password, req.body.clientId, req.body.tenantId);

        return res.status(201).send(jwtToken);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteAzureJwtToken = async (req, res) => {
    try {
        await ApiSecurityAuthService.revokeRefreshToken(req.userId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}