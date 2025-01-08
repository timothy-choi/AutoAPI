const serverlessFunctionHelper = require('./ServerlessFunctionHelper');

exports.GetServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await serverlessFunctionHelper.getAzureFunctionInfo(req.body.serverlessFunctionAppName, req.body.projectId, req.body.region);

        return res.status(201).send(serverlessFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.ListServerlessFunctions = async (req, res) => {
    try {
        var serverlessFunctions = await serverlessFunctionHelper.listServerlessFunctions(req.projectId, req.region);

        return res.status(200).send(serverlessFunctions);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeployServerlessFunction = async (req, res) => {
    try {
        await serverlessFunctionHelper.deployFunction(req.body.functionName, req.body.entryPoint, req.body.runtime, req.body.sourcePath, req.body.region, req.body.projectId);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateServerlessFunction = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.UpdateServerlessFunction(req.body.functionName, req.body.projectId, req.body.region, req.body.updatedConfig);

        return res.status(200).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteServerlessFunction = async (req, res) => {
    try {
        await serverlessFunctionHelper.deleteFunction(req.functionName, req.projectId, req.region);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CloneServerlessFunction = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.cloneFunction(req.body.sourcefunctionName, req.body.targetFunctionName, req.body.projectId, req.body.region);

        return res.status(201).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.RollbackServerlessFunction = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.rollbackFunction(req.body.functionName, req.body.projectId, req.body.region, req.body.rollbackVersionId);

        return res.status(200).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.SetBackupFunctionConfiguration = async (req, res) => {
    try {
        await serverlessFunctionHelper.BackupFunctionConfiguration(req.body.functionName, req.body.projectId, req.body.region, req.body.bucketName, req.body.fileName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.RestoreFunctionConfiguration = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.RestoreFunctionConfiguration(req.body.bucketName, req.body.fileName, req.body.projectId, req.body.region);

        return res.status(200).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetFunctionMetrics = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.getServerlessFunctionMetrics(req.body.httpFunctionUri, req.body.accessToken, req.body.refreshToken, req.body.projectName, req.body.minutes, req.body.metricTypes);

        return res.status(201).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};