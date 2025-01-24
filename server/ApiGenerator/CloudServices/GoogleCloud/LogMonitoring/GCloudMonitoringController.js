const GCloudMonitoringService = require('./GCloudMonitoringService');

exports.enableLogging = async (req, res) => {
    try {
        await GCloudMonitoringService.enableLogging(req.body.typeName, req.body.requestInfo, req.body.responseInfo);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createLoggingSink = async (req, res) => {
    try {
        var sink = await GCloudMonitoringService.createLoggingSink(req.body.destination, req.body.logName, req.body.sinkName);

        return res.status(201).send(sink);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};