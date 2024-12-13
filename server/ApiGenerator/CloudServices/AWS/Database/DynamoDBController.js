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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        await DynamoDBHelper.BatchWriteItems(userCredentials, req.body.requests);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.CreateGSI = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var gsiResponse = await DynamoDBHelper.CreateGSI(userCredentials, req.body.tableName, req.body.indexParams);

        return res.status(200).send({"gsiResponse": gsiResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.UpdatedProvisionedThroughPut = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var provisionedThroughputResponse = await DynamoDBHelper.UpdateProvisionedThroughPut(userCredentials, req.body.tableName, req.body.readCapacity, req.body.writeCapacity);

        return res.status(200).send({"provisionedThroughputResponse": provisionedThroughputResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.EnableStreams = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var streamsResponse = await DynamoDBHelper.EnableStreams(userCredentials, req.body.tableName, req.body.streamViewType);

        return res.status(200).send({"streamsResponse": streamsResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.UpdateTTL = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var ttlResponse = await DynamoDBHelper.UpdateTTL(userCredentials, req.body.tableName, req.body.ttlAttribute);

        return res.status(200).send({"ttlResponse": ttlResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.ModifyTagTable = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var tagTableResponse = await DynamoDBHelper.ModifyTagTable(userCredentials, req.body.resourceArn, req.body.tags);

        return res.status(200).send({"tagTableResponse": tagTableResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.CreateBackup = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var backupResponse = await DynamoDBHelper.CreateBackup(userCredentials, req.body.tableName, req.body.backupName);

        return res.status(201).send({"backupResponse": backupResponse});
    } catch (error) {
        return res.status(500).send("Error getting item from DynamoDB table: " + error);
    }
}

exports.RestoreBackup = async (req, res) => {
    try {
        var userCredentials = {};
        if (req.body.userCredentials) {
            userCredentials = req.body.userCredentials;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        var backupRestoreResponse = await DynamoDBHelper.RestoreBackup(userCredentials, req.body.backupArn, req.body.restoredTableName);

        return res.status(201).send({"backupRestoreResponse": backupRestoreResponse});
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

        if (!userCredentials.region) {
            userCredentials.region = req.body.userRegion;
        } 

        await DynamoDBHelper.DeleteDynamoDBTable(userCredentials, req.body.tableName);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send("Error creating DynamoDB table: " + error);
    }
}