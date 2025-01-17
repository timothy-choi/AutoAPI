const ApiMonitoringService = require('./ApiMonitoringService');

exports.getMonitoringLogById = async (req, res) => {
    try {
        var monitoringLog = await ApiMonitoringService.getMonitoringLogById(req.monitoringLogId);

        return res.status(200).send(monitoringLog);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getMonitoringLogByProjectId = async (req, res) => {
    try {
        var monitoringLog = await ApiMonitoringService.getMonitoringLogByProjectId(req.projectId);

        return res.status(200).send(monitoringLog);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createMonitoringLog = async (req, res) => {
    try {
        var monitoringLog = await ApiMonitoringService.createMonitoringLog(req.projectId);

        return res.status(201).send(monitoringLog);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteMonitoringLog = async (req, res) => {
    try {
        await ApiMonitoringService.deleteMonitoringLog(req.monitoringLogId);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
}