const ApiGatewayHelper = require('./ApiGatewayHelper');

exports.getApiGateway = async (req, res) => {
    try {
        const apiGateway = await ApiGatewayHelper.getApiGateway(req.body.apiGatewayId, req.body.userCredentials, req.body.userRegion);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApiGateway = async (req, res) => {
    try {
        const apiGateway = await ApiGatewayHelper.createApiGateway(req.body.apiName, req.body.description, req.body.endpointConfig, req.body.userCredentials, req.body.userRegion);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createResource = async (req, res) => {
    try {
        const resource = await ApiGatewayHelper.createResource(req.body.parentPathId, req.body.gatewayId, req.body.resourcePath, req.body.userCredentials, req.body.userRegion);

        return res.status(201).send(resource);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteApiGateway = async (req, res) => {
    try {
        await ApiGatewayHelper.deleteApiGateway(req.body.apiGatewayId, req.body.userCredentials, req.body.userRegion);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};