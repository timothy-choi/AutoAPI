const { enableDiagnostics } = require('./Database/AzureRDSMetrics');

exports.enableLoggingDiagnostics = async (resourceId, subscriptionId, diagnosticRequestInfo, diagnosticRequestName) => {
    try {
        await enableDiagnostics(resourceId, subscriptionId, diagnosticRequestInfo, diagnosticRequestName);
    } catch (error) {
        throw new Error(error.message);
    }
};