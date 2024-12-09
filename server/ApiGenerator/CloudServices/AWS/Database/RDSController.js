const RDSHelper = require("./RDSHelper");
const AWSHelper = require("../AWSHelperFunctions");

exports.getRDSInstanceAvailability = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var availability = await RDSHelper.checkRDSInstanceAvailability(req.currDbId, userCredentials, req.body.userRegion);

        return res.status(200).send({"availability": availability});
    } catch (error) {
        return res.status(500).send("Error getting RDS instance availability: " + error);
    }
}

exports.GetRDSInstanceStatus = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var status = await RDSHelper.GetRDSInstanceStatus(req.currDbId, userCredentials, req.body.userRegion);

        return res.status(200).send({"status": status});
    } catch (error) {
        return res.status(500).send("Error getting RDS instance availability: " + error);
    }
}

exports.createRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const rdsInstanceInfo = await RDSHelper.createRDSInstance(req.body.dbInstanceInfo, userCredentials, req.body.userRegion);

        return res.status(201).json(rdsInstanceInfo);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}

exports.startRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.startRDSInstance(req.body.dbInstanceInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
} 

exports.removeRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.stopRDSInstance(req.body.dbInstanceInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}

exports.modifyRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.modifyRDSInstance(req.body.changedDBAttributes, userCredentials, req.body.userRegion);

        return res.status(201).send(rebootData);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}

exports.rebootRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var rebootData = await RDSHelper.rebootRDSInstance(req.body.dbInstanceInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(rebootData);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}

exports.deleteRDSInstance = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.deleteRDSInstance(req.body.currDbId, req.body.skipSnapshot, userCredentials, req.body.userRegion);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}