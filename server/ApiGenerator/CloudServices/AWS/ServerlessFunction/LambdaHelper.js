const AWS = require('aws-sdk');
const fs = require('fs');

exports.createLambdaFunction = async (functionInfo, userCredentials) => {
    try {
        const zipFile = fs.readFileSync(functionInfo.zipFilePath);

        const lambda = new AWS.Lambda({
            accessKeyId: userCredentials.accessKeyId,
            secretAccessKey: userCredentials.secretAccessKey,
            region: functionInfo.region,
        });

        const params = {
            FunctionName: functionInfo.functionName,
            Runtime: functionInfo.runtime, 
            Role: functionInfo.roleArn,
            Handler: functionInfo.handler, 
            Code: {
                ZipFile: zipFile, 
            },
            Environment: {
                Variables: functionInfo.environmentVariables,
            },
        };

        const data = await lambda.createFunction(params).promise();

        return data;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deleteLambdaFunction = async (functionName, userCredentials, region) => {
    try {
        const lambda = new AWS.Lambda({
            accessKeyId: userCredentials.accessKeyId,
            secretAccessKey: userCredentials.secretAccessKey,
            region: region,
        });

        const params = {
            FunctionName: functionName,
        };

        await lambda.deleteFunction(params).promise();
    } catch (error) {
        throw new Error(error.message);
    }
};