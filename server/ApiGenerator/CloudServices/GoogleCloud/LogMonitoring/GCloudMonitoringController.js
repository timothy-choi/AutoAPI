const GCloudMonitoringService = require('./GCloudMonitoringService');

exports.enableLogging = async (req, res) => {
    try {
        await GCloudMonitoringService.enableLogging(req.body.typeName, req.body.requestInfo, req.body.responseInfo);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};