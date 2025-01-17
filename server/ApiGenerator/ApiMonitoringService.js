const ApiMonitoring = require('./ApiMonitoring');

exports.getMonitoringLogById = async (monitoringLogId) => {
    var uuidVal = mongoose.types.ObjectId(monitoringLogId);

    return await ApiMonitoring.findById(uuidVal);
};

exports.getMonitoringLogByProjectId = async (projectId) => {
    return await ApiMonitoring.findOne({ where: { ProjectId: projectId } });
};

exports.createMonitoringLog = async (projectId) => {
    try {
        var monitoringLog = await this.getMonitoringLogByProjectId(projectId);

        if (monitoringLog) {
            throw new Error("Instance already exists");
        }

        monitoringLog = new ApiMonitoring({
            ProjectId: projectId,
            CreatedAt: Date.now()
        });
    
        await monitoringLog.save();
    
        return monitoringLog;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.deleteMonitoringLog = async (monitoringLogId) => {
    try {
        var monitoringLog = await this.getMonitoringLogById(monitoringLogId);

        if (!monitoringLog) {
            throw new Error("Instance does not exist");
        }

        await monitoringLog.destroy();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
}