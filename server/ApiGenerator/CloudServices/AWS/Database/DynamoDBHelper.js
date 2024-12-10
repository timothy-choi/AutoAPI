const AWS = require('aws-sdk');

exports.CreateDynamoDBTable = async (userCredentials, tableParams) => {
    const dynamodb = new AWS.DynamoDB({
        accessKeyId: userCredentials.accessKey,
        secretAccessKey: userCredentials.secretKey,
        sessionToken: userCredentials.sessionToken,
        region: userCredentials.region
    });

    const tableResponse = await dynamodb.createTable(tableParams).promise();

    return tableResponse;
}

exports.DescribeTable = async (userCredentials, tableName) => {
    const dynamodb = new AWS.DynamoDB({
        accessKeyId: userCredentials.accessKey,
        secretAccessKey: userCredentials.secretKey,
        sessionToken: userCredentials.sessionToken,
        region: userCredentials.region
    });

    const params = {
        TableName: tableName, 
    };

    const tableResponse = await dynamodb.createTable(params).promise();

    return tableResponse;
}

exports.GetTableStatus = async (userCredentials, tableName) => {
    const dynamodb = new AWS.DynamoDB({
        accessKeyId: userCredentials.accessKey,
        secretAccessKey: userCredentials.secretKey,
        sessionToken: userCredentials.sessionToken,
        region: userCredentials.region
    });

    const params = {
        TableName: tableName, 
    };

    const tableResponse = await dynamodb.createTable(params).promise();

    return tableResponse.TableDescription.TableStatus;
}

exports.DeleteDynamoDBTable = async (userCredentials, tableName) => {
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
}