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