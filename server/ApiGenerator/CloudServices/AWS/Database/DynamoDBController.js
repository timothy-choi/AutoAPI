const DynamoDBHelper = require('./DynamoDBHelper');
const AWSHelper = require("../AWSHelperFunctions");

exports.CreateDynamoDBTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const tableResponse = await DynamoDBHelper.CreateDynamoDBTable(userCredentials, req.body.tableParams);

        return res.status(201).send({"tableResponse": tableResponse});
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}

exports.DeleteDynamoDBTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await DynamoDBHelper.DeleteDynamoDBTable(userCredentials, req.body.tableName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}