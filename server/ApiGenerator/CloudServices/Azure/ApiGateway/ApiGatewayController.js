const apiGatewayHelper = require('./ApiGatewayHelper');

exports.createApiManagementService = async (req, res) => {
    try {
        const result = await apiGatewayHelper.createApiManagementService(req.body.subscriptionId, req.body.resourceGroupName, req.body.serviceName, req.body.parameters);

        return res.status(201).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApi = async (req, res) => {
    try {
        const result = await apiGatewayHelper.createApi(req.body.subscriptionId, req.body.resourceGroupName, req.body.serviceName, req.body.apiId, req.body.parameters);

        return res.status(201).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createApiOperation = async (req, res) => {
    try {
        const result = await apiGatewayHelper.createOperation(req.body.subscriptionId, req.body.resourceGroupName, req.body.serviceName, req.body.apiId, req.body.operationId, req.body.parameters);

        return res.status(201).send(result);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteApi = async (req, res) => {
    try {
        await apiGatewayHelper.deleteApi(req.body.subscriptionId, req.body.resourceGroupName, req.body.serviceName, req.body.apiId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteApiManagementService = async (req, res) => {
    try {
        await apiGatewayHelper.deleteApiManagementService(req.body.subscriptionId, req.body.resourceGroupName, req.body.serviceName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};