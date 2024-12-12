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

exports.GetTableStatus = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        const tableStatus = await DynamoDBHelper.GetTableStatus(userCredentials, req.body.tableName);

        return res.status(200).send({"tableStatus": tableStatus});
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}

exports.AddItemToTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await DynamoDBHelper.PutTableEntry(userCredentials, req.body.tableName, req.body.entryInfo);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}

exports.RemoveItemFromTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await DynamoDBHelper.DeleteTableEntry(userCredentials, req.body.tableName, req.body.keyValue);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}

exports.GetItemFromTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var itemResponse = await DynamoDBHelper.GetTableEntry(userCredentials, req.body.tableName, req.body.keyValue);

        return res.status(201).send({"itemResponse": itemResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.QueryDynamoDBTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var queryResponse = await DynamoDBHelper.QueryTable(userCredentials, req.body.tableName, req.body.keyConditionExpression, req.body.expressionAttribute);

        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.GetBatchQuery = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var batchQueryResponse = await DynamoDBHelper.BatchQueryTable(userCredentials, req.body.requestItems);

        return res.status(201).send({"batchQueryResponse": batchQueryResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.ScanTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var scanTableResponse = await DynamoDBHelper.ScanTable(userCredentials, req.body.tableName, req.body.filterExpression);

        return res.status(201).send({"scanTableResponse": scanTableResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.UpdateItemInTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await DynamoDBHelper.UpdateItem(userCredentials, req.body.tableName, req.body.key, req.body.updateExpression, req.body.expressionAttributeValues, req.body.returnValues);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.BatchWriteItems = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await DynamoDBHelper.BatchWriteItems(userCredentials, req.body.requests);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
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