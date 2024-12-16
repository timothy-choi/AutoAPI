const { DefaultAzureCredential } = require("@azure/identity");
const { OperationalInsightsManagementClient } = require("@azure/arm-operationalinsights");
const { enableDiagnostics } = require('./Database/AzureRDSMetrics');

exports.createWorkspace = async (resourceGroupName, workspaceName, workspaceParams, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();
        const operationalInsightsClient = new OperationalInsightsManagementClient(credential, subscriptionId);

        const workspaceResponse = await operationalInsightsClient.workspaces.createOrUpdate(
            resourceGroupName,
            workspaceName,
            workspaceParams
        );

        return workspaceResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.enableLoggingAnalyticsDiagnosis = async (resourceId, subscriptionId, diagnosticRequestInfo, diagnosticRequestName) => {
    try {
        await enableDiagnostics(resourceId, subscriptionId, diagnosticRequestInfo, diagnosticRequestName);
    } catch (error) {
        throw new Error(error.message);
    }
}