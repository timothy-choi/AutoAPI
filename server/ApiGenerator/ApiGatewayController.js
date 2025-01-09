const ApiGatewayService = require('./ApiGatewayService');

exports.GetApiGatewayById = async (req, res) => {
    try {
        var apiGateway = await ApiGatewayService.GetApiGateway(req.apiGatewayId);

        return res.status(200).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetApiGatewayByProjectId = async (req, res) => {
    try {
        var apiGateway = await ApiGatewayService.GetApiGatewayByProjectId(req.projectId);

        return res.status(200).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateApiGateway = async (req, res) => {
    try {
        var apiGateway = await ApiGatewayService.CreateApiGateway(req.body.projectId, req.body.endpointsId, req.body.createdBy);

        return res.status(201).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.AddRoute = async (req, res) => {
    try {
        var apiGateway = await ApiGatewayService.addRoute(req.body.apiGatewayId, req.body.route, req.body.updatedBy);

        return res.status(200).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteRoute = async (req, res) => {
    try {
        await ApiGatewayService.deleteRoute(req.body.apiGatewayId, req.body.routeId, req.body.updatedBy);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateRoute = async (req, res) => {
    try {
        var apiGateway = await ApiGatewayService.updateRoute(req.body.apiGatewayId, req.body.routeId, req.body.route, req.body.updatedBy);

        return res.status(200).send(apiGateway);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteApiGateway = async (req, res) => {
    try {
        await ApiGatewayService.DeleteApiGateway(req.apiGatewayId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};