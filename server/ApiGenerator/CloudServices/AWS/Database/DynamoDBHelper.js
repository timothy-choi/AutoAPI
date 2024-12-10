const AWS = require('aws-sdk');

exports.CreateDynamoDBTable = async (userCredentials, tableParams) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const tableResponse = await dynamodb.createTable(tableParams).promise();
    
        return tableResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DescribeTable = async (userCredentials, tableName) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = {
            TableName: tableName, 
        };

        const tableResponse = await dynamodb.describeTable(params).promise();

        return tableResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.GetTableStatus = async (userCredentials, tableName) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = {
            TableName: tableName, 
        };

        const tableResponse = await dynamodb.describeTable(params).promise();

        return tableResponse.TableDescription.TableStatus;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.PutTableEntry = async (userCredentials, tableName, entryInfo) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = { TableName: tableName, Item: entryInfo };
    
        await docClient.put(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteTableEntry = async (userCredentials, tableName, keyValue) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = { TableName: tableName, Key: keyValue };
    
        await docClient.delete(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.GetTableEntry = async (userCredentials, tableName, keyValue) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = { TableName: tableName, Key: keyValue };
    
        var data = await docClient.get(params).promise();

        return data.Item;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.QueryTable = async (userCredentials, tableName, keyConditionExpression, expressionAttribute) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = {
            TableName: tableName,
            KeyConditionExpression: keyConditionExpression,
            ExpressionAttributeValues: expressionAttributeValues,
          };
    
        var data = await docClient.query(params).promise();

        return data.Items;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.BatchQueryTable = async (userCredentials, requestItems) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = {
            RequestItems: requestItems
          };
    
        var data = await docClient.batchGet(params).promise();

        return data.Responses;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.ScanTable = async (userCredentials, tableName, filterExpression = null) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = { TableName: tableName };
        if (filterExpression) {
            params.FilterExpression = filterExpression;
        }

        const data = await docClient.scan(params).promise();

        return data.Items;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.UpdateItem = async (userCredentials, tableName, key, updateExpression, expressionAttributeValues, returnValues) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });
    
        const params = {
            TableName: tableName,
            Key: key,
            UpdateExpression: updateExpression,
            ExpressionAttributeValues: expressionAttributeValues,
            ReturnValues: returnValues
          };
    
        await docClient.update(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.BatchWriteItems = async (userCredentials, requests) => {
    try {
        const docClient = new AWS.DynamoDB.DocumentClient({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = {
            RequestItems: requests
        };

        await docClient.batchWrite(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.CreateGSI = async (userCredentials, tableName, indexParams) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = {
            TableName: tableName,
            GlobalSecondaryIndexUpdates: [
              {
                Create: {
                  IndexName: indexParams.IndexName,
                  KeySchema: indexParams.KeySchema,
                  Projection: indexParams.Projection,
                  ProvisionedThroughput: indexParams.ProvisionedThroughput,
                },
              },
            ],
            AttributeDefinitions: indexParams.AttributeDefinitions,
        };

        const data = await dynamodb.updateTable(params).promise();

        return data;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteDynamoDBTable = async (userCredentials, tableName) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const params = {
            TableName: tableName, 
        };

        await dynamodb.deleteTable(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
}