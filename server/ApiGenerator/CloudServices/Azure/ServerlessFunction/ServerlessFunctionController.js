const serverlessFunctionHelper = require('./ServerlessFunctionHelper');

exports.GetServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await serverlessFunctionHelper.getAzureFunctionInfo(req.body.serverlessFunctionAppName, req.body.resourceGroupName, req.body.functionName, req.body.subscriptionId);

        return res.status(201).send(serverlessFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.ListServerlessFunctions = async (req, res) => {
    try {
        var serverlessFunctions = await serverlessFunctionHelper.getListOfFunctions(req.body.functionAppName, req.body.resourceGroupName, req.body.subscriptionId);

        return res.status(201).send(serverlessFunctions);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await serverlessFunctionHelper.createFunctionApp(req.body.functionAppName, req.body.storageAccountName, req.body.resourceGroupName, req.body.appServicePlanConfig, req.body.location, req.body.subscriptionId);

        return res.status(201).send(serverlessFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UploadFunctionCode = async (req, res) => {
    try {
        var serverlessFunctionResponse = await serverlessFunctionHelper.uploadFunctionCode(req.body.storageAccountName, req.body.containerName, req.body.zipFilePath, req.body.blobName);

        return res.status(201).send(serverlessFunctionResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await serverlessFunctionHelper.updateFunction(req.body.subscriptionId, req.body.zipFilePath, req.body.resourceGroupName, req.body.functionAppName);

        return res.status(200).send(serverlessFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.ScaleServerlessFunction = async (req, res) => {
    try {
        var serverlessFunction = await serverlessFunctionHelper.scaleFunctionApp(req.body.subscriptionId, req.body.servicePlanName, req.body.scaleConfig);

        return res.status(201).send(serverlessFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateFunctionAppSettings = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.updateFunctionAppSettings(req.body.subscriptionId, req.body.resourceGroupName, req.body.functionAppName, req.body.appSettings);

        return res.status(200).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.BackupServerlessFunction = async (req, res) => {
    try {
        var backupResponse = await serverlessFunctionHelper.backupFunctionApp(req.body.resourceGroupName, req.body.functionAppName, req.body.storageAccountUrl, req.body.backupName, req.body.subscriptionId);

        return res.status(201).send(backupResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.RestoreServerlessFunction = async (req, res) => {
    try {
        var restoreResponse = await serverlessFunctionHelper.restoreFunctionApp(req.body.resourceGroupName, req.body.functionAppName, req.body.storageAccountUrl, req.body.backupName, req.body.subscriptionId);

        return res.status(201).send(restoreResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteServerlessFunction = async (req, res) => {
    try {
        await serverlessFunctionHelper.deleteFunctionApp(req.body.functionAppName, req.body.resourceGroupName, req.body.subscriptionId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteCascadeServerlessFunction = async (req, res) => {
    try {
        await serverlessFunctionHelper.deleteCascadeFunctionApp(req.body.functionAppName, req.body.resourceGroupName, req.body.subscriptionId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetServerlessFunctionMetrics = async (req, res) => {
    try {
        var metrics = await serverlessFunctionHelper.getFunctionAppMetrics(req.body.httpFunctionUri, req.body.subscriptionId, req.body.resourceId, req.body.metrics, req.body.interval, req.body.intervalInMinutes, req.body.autoTrigger);

        return res.status(201).send(metrics);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.InvokeServerlessFunction = async (req, res) => {
    try {
        var response = await serverlessFunctionHelper.invokeFunction(req.body.subscriptionId, req.body.resourceGroupName, req.body.functionAppName, req.body.functionName, req.body.functionKey, req.body.payload);

        return res.status(201).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};