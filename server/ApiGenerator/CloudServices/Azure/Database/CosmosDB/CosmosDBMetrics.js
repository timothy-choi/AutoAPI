const axios = require('axios');
const { enableDiagnostics } = require('../AzureRDSMetrics');

exports.GetCosmosDBMetrics = async (azureFunctionUri, metricsRequestInfo, stopVal = false) => {
    try {
        if (stopVal) {
            throw new Error('Instance is not recieving any metrics');
        }

        const response = await axios.post(azureFunctionUri, metricsRequestInfo);

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.enableCosmosDBDiagnostics = async (resourceId, subscriptionId, diagnosticRequestInfo, diagosticRequestName) => {
    try {
        await enableDiagnostics(resourceId, subscriptionId, diagnosticRequestInfo, diagosticRequestName);
    } catch (error) {
        throw new Error(error.message);
    }
};

