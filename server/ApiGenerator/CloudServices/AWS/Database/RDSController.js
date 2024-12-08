const RDSHelper = require("./RDSHelper");
const AWSHelper = require("../AWSHelperFunctions");

exports.getRDSInstanceAvailability = async (req, res) => {
    try {
        const userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);

        var availability = await RDSHelper.checkRDSInstanceAvailability(req.currDbId, userCredentials, req.body.userRegion);

        return res.status(200).send({"availability": availability});
    } catch (error) {
        return res.status(500).send("Error getting RDS instance availability: " + error);
    }
}