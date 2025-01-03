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

exports.DeleteServerlessFunction = async (req, res) => {
    try {
        await serverlessFunctionHelper.deleteFunctionApp(req.body.functionAppName, req.body.resourceGroupName, req.body.subscriptionId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};