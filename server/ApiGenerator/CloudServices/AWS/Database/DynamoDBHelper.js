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