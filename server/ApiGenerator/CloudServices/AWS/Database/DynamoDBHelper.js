const AWS = require('aws-sdk');

const executeWithRetry = async (operation, maxRetries = 3, initialDelay = 100, backoffFactor = 2) => {
    let attempt = 0;
    let delay = initialDelay;

    while (attempt <= maxRetries) {
        try {
            return await operation();
        } catch (error) {
            attempt++;
            if (attempt > maxRetries) {
                throw new Error(error.message);
            }

            await new Promise(resolve => setTimeout(resolve, delay));
            delay *= backoffFactor;
        }
    }
}

exports.CreateDynamoDBTable = async (userCredentials, tableParams) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const operation = async () => {
            const tableResponse = await dynamodb.createTable(tableParams).promise();
    
            return tableResponse;
        };

        return executeWithRetry(operation, 3, 20, 2);
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

        const operation = async () => {
            const params = {
                TableName: tableName, 
            };

            const tableResponse = await dynamodb.describeTable(params).promise();

            return tableResponse;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                TableName: tableName, 
            };

            const tableResponse = await dynamodb.describeTable(params).promise();

            return tableResponse.TableDescription.TableStatus;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = { TableName: tableName, Item: entryInfo };
    
            await docClient.put(params).promise();
        }

        executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = { TableName: tableName, Key: keyValue };
        
            await docClient.delete(params).promise();
        }

        executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = { TableName: tableName, Key: keyValue };
        
            var data = await docClient.get(params).promise();

            return data.Item;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                TableName: tableName,
                KeyConditionExpression: keyConditionExpression,
                ExpressionAttributeValues: expressionAttribute,
            };
        
            var data = await docClient.query(params).promise();

            return data.Items;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                RequestItems: requestItems
            };
        
            var data = await docClient.batchGet(params).promise();

            return data.Responses;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = { TableName: tableName };
            if (filterExpression) {
                params.FilterExpression = filterExpression;
            }

            const data = await docClient.scan(params).promise();

            return data.Items;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                TableName: tableName,
                Key: key,
                UpdateExpression: updateExpression,
                ExpressionAttributeValues: expressionAttributeValues,
                ReturnValues: returnValues
              };
        
            await docClient.update(params).promise();
        };

        executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                RequestItems: requests
            };

            await docClient.batchWrite(params).promise();
        };

        executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
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
        };

        return executeWithRetry(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.UpdateProvisionedThroughPut = async (userCredentials, tableName, readCapacity, writeCapacity) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const operation = async () => {
            const params = {
                TableName: tableName,
                ProvisionedThroughput: {
                ReadCapacityUnits: readCapacity,
                WriteCapacityUnits: writeCapacity,
                },
            };

            const data = await dynamodb.updateTable(params).promise();

            return data;
        };

        return executeWithRetry(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.EnableStreams = async (userCredentials, tableName, streamViewType) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const operation = async () => {
            const params = {
                TableName: tableName,
                StreamSpecification: {
                StreamEnabled: true,
                StreamViewType: streamViewType,
                },
            };

            const data = await dynamodb.updateTable(params).promise();

            return data;
        };

        return executeWithRetry(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.UpdateTTL = async (userCredentials, tableName, ttlAttribute) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const operation = async () => {
            const params = {
                TableName: tableName,
                TimeToLiveSpecification: {
                AttributeName: ttlAttribute,
                Enabled: true, 
                },
            };

            const data = await dynamodb.updateTimeToLive(params).promise();

            return data;
        };

        return executeWithRetry(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.ModifyTagTable = async (userCredentials, resourceArn, tags) => {
    try {
        const dynamodb = new AWS.DynamoDB({
            accessKeyId: userCredentials.accessKey,
            secretAccessKey: userCredentials.secretKey,
            sessionToken: userCredentials.sessionToken,
            region: userCredentials.region
        });

        const operation = async () => {
            const params = {
                ResourceArn: resourceArn, 
                Tags: tags, 
            };

            const data = await dynamodb.tagResource(params).promise();

            return data;
        };

        return executeWithRetry(operation, 3, 20, 3);
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

        const operation = async () => {
            const params = {
                TableName: tableName, 
            };

            await dynamodb.deleteTable(params).promise();
        };

        executeWithRetry(operation, 3, 20, 3);
    } catch (error) {
        throw new Error(error.message);
    }
}