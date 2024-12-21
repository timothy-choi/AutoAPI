const RelationalDBHelper = require('./RelationalDBHelper');

exports.CreateGCloudDBInstance = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.createGCloudDBInstance(authClient, req.body.projectId, req.body.instanceConfig);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.data.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteGCloudDBInstance = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.deleteGCloudDBInstance(authClient, req.body.projectId, req.body.instanceId);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.data.name, authClient, req.body.timeoutMS);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetGCloudDBInstanceInfo = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceInfo = await RelationalDBHelper.getGCloudDBInstanceDetails(authClient, req.projectId, req.instanceId);

        return res.status(200).send({"instanceInfo": instanceInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.StartOrStopGCloudDBInstance = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.startOrStopGCloudDBInstance(authClient, req.body.projectId, req.body.instanceId, req.body.action);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.CreateGCloudBackup = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.createGCloudBackup(authClient, req.body.projectId, req.body.instanceId);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.RestoreGCloudBackup = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.restoreGCloudBackup(authClient, req.body.projectId, req.body.instanceId, req.body.backupId);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.UpdateGCloudDBInstanceSettings = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.updateGCloudDBInstanceSettings(authClient, req.body.projectId, req.body.instanceId, req.body.settings);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.HandleGCloudDBInstanceFailover = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceResponse = await RelationalDBHelper.FailoverGCloudDBInstance(authClient, req.body.projectId, req.body.instanceId);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, instanceResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"instanceResponse": instanceResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetGCloudInstanceStatus = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var instanceInfo = await RelationalDBHelper.getGCloudDBInstanceDetails(authClient, req.projectId);

        return res.status(200).send({"instanceStatus": instanceInfo.status});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetGCloudInstanceHealthStatus = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var healthStatusInfo = await RelationalDBHelper.getGCloudInstancesHealthStatus(authClient, req.projectId, req.instanceId);

        return res.status(200).send({"healthStatus": healthStatusInfo});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.RebootGCloudInstance = async (req, res) => {
    try {
        const authHeader = req.headers['authorization'];

        const accessToken = authHeader.split(' ')[1];

        var authClient = await RelationalDBHelper.createOAuth2Client(accessToken, req.body.refreshToken);

        var rebootResponse = await RelationalDBHelper.RebootGCloudInstance(authClient, req.body.projectId, req.body.instanceId);

        await RelationalDBHelper.trackGCloudDBOperationStatus(req.body.projectId, rebootResponse.name, authClient, req.body.timeoutMS);

        return res.status(201).send({"rebootResponse": rebootResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};