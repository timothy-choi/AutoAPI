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