const { DefaultAzureCredential } = require("@azure/identity");
const { ResourceHealthManagementClient } = require("@azure/arm-resourcehealth");
const axios = require('axios');
const { MonitorManagementClient } = require("@azure/arm-monitor");

exports.getDatabaseHealthStatus = async (resourceId, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();

        const healthClient = new ResourceHealthManagementClient(credential, subscriptionId);

        const healthStatus = await healthClient.availabilityStatuses.get(resourceId);

        return healthStatus;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.getDatabaseMetrics = async (azureFunctionUri, metricsRequestInfo, stopVal = false) => {
    try {
        if (stopVal) {
            throw new Error('Instance is not recieving any metrics');
        }

        const response = await axios.post(azureFunctionUri, metricsRequestInfo);

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.enableDiagnostics = async (resourceId, subscriptionId, diagnosticRequestInfo, diagosticRequestName) => {
    try {
        const credential = new DefaultAzureCredential();
        const monitorClient = new MonitorManagementClient(credential, subscriptionId);

        await monitorClient.diagnosticSettings.createOrUpdate(resourceId, diagosticRequestName, diagnosticRequestInfo);
    } catch (error) {
        throw new Error(error.message);
    }
}