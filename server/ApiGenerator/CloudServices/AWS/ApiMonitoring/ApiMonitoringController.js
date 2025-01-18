const ApiMonitoringHelper = require('./ApiMonitoringHelper');

exports.CreateTrail = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var trail = await ApiMonitoringHelper.createTrail(userCredentials, req.body.trailInfo, req.body.region);

        return res.status(201).send(trail);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.StartLogging = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await ApiMonitoringHelper.startLogging(userCredentials, req.body.trailName, req.body.region);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.StopLogging = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await ApiMonitoringHelper.stopLogging(userCredentials, req.body.trailName, req.body.region);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.GetTrail = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var trail = await ApiMonitoringHelper.getTrail(userCredentials, req.body.trailName, req.body.region);

        return res.status(201).send(trail);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.DeleteTrail = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await ApiMonitoringHelper.deleteTrail(userCredentials, req.body.trailName, req.body.region);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};