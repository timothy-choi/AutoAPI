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

exports.GetListOfRDSInstances = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var instances = await RDSHelper.ListAllRDSInstances(userCredentials, req.userRegion);

        return res.status(200).send({"rdsInstances": instances});
    } catch (error) {
        return res.status(500).send("Error getting RDS instance availability: " + error);
    }
}

exports.StartOrStopRDSInstanceMetricsFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.StartOrStopRDSInstanceUsageMetricsFunction(req.body.lambdaFunctionName, req.body.payloadInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send("Error starting or stopping RDS instance metrics function: " + error);
    }
}

exports.GetRDSInstanceUsageAndHealthStatus = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var metricsResponse = await RDSHelper.GetRDSInstanceUsageAndHealthStatus(req.body.lambdaFunctionName, req.body.payloadInfo, userCredentials, req.body.userRegion);

        return res.status(200).send({"metricsResponse": metricsResponse});
    } catch (error) {
        return res.status(500).send("Error getting metrics and health status data: " + error);
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

exports.createReadReplica = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var replicaResponse = await RDSHelper.createReadReplica(req.body.sourceDbId, req.body.replicaDbId, userCredentials, req.body.userRegion);

        return res.status(200).send({"replicaResponse": replicaResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.DeleteReadReplica = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var replicaResponse = await RDSHelper.deleteReadReplica(req.body.replicaDbId, userCredentials, req.body.userRegion);

        return res.status(200).send({"replicaResponse": replicaResponse});
    } catch (error) {
        return res.status(500).send(error.message);
    }
}

exports.createRDSBackup = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var snapshotData = await RDSHelper.createRDSBackup(req.body.currDbId, req.body.snapshotId, userCredentials, req.body.userRegion);

        return res.status(201).send(snapshotData);
    } catch (error) {
        return res.status(500).send("Error creating RDS instance: " + error);
    }
}

exports.restoreRDSBackup = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await RDSHelper.restoreRDSBackup(req.body.currDbId, req.body.snapshotId, req.body.instanceClass, req.body.publiclyAccessible, userCredentials, req.body.userRegion);

        return res.status(201).send(null);
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