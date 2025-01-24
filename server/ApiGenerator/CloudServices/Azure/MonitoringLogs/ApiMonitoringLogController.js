const ApiMonitoringLogService = require('./ApiMonitoringLogService');

exports.enableLoggingDiagnostics = async (req, res) => {
    try {
        await ApiMonitoringLogService.enableLoggingDiagnostics(req.body.resourceId, req.body.subscriptionId, req.body.diagnosticRequestInfo, req.body.diagnosticRequestName);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};