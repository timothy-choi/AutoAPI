const ApiDeploymentService = require('./ApiDeploymentService');

exports.GetApiDeploymentById = async (req, res) => {
    try {
        var deployment = await ApiDeploymentService.getApiDeploymentById(req.deploymentId);

        return res.status(200).send(deployment);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.GetApiDeploymentByProjectId = async (req, res) => {
    try {
        var deployment = await ApiDeploymentService.getApiDeploymentByProjectId(req.projectId);

        return res.status(200).send(deployment);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.CreateApiDeployment = async (req, res) => {
    try {
        var deployment = await ApiDeploymentService.createApiDeployment(req.body);

        return res.status(201).send(deployment);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.SetVersion = async (req, res) => {
    try {
        await ApiDeploymentService.setVersion(req.deploymentId, req.version);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.SetBaseUrl = async (req, res) => {
    try {
        await ApiDeploymentService.setBaseUrl(req.deploymentId, req.body.baseUrl);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.SetStatus = async (req, res) => {
    try {
        await ApiDeploymentService.setStatus(req.deploymentId, req.status);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.SetEnvironment = async (req, res) => {
    try {
        await ApiDeploymentService.setEnvironment(req.deploymentId, req.body.environment);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.SetDeployedAt = async (req, res) => {
    try {
        await ApiDeploymentService.deleteApiDeployment(req.deploymentId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
};

exports.DeleteApiDeployment = async (req, res) => {
    try {
        await ApiDeploymentService.deleteApiDeployment(req.deploymentId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).json(error.message);
    }
}