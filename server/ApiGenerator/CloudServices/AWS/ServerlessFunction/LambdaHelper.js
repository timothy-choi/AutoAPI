const AWS = require('aws-sdk');
const fs = require('fs');

exports.getLambdaFunction = async (functionName, userCredentials, region) => {
    try {
        const lambda = new AWS.Lambda({
            accessKeyId: userCredentials.accessKeyId,
            secretAccessKey: userCredentials.secretAccessKey,
            region: region,
        });

        const params = {
            FunctionName: functionName,
        };

        const data = await lambda.getFunction(params).promise();

        return data;
    } catch (error) {
        throw new Error(error.message);
    }
};

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

exports.listLambdaFunctions = async (userCredentials, region) => {
    const params = {};

    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    try {
        const data = await lambda.listFunctions(params).promise();
        return data.Functions;
    } catch (err) {
        throw new Error('Error listing Lambda functions:', err);
    }
};

exports.updateLambdaFunction = async (functionName, zipFilePath, userCredentials, region) => {
    const zipFile = fs.readFileSync(zipFilePath);

    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    const params = {
        FunctionName: functionName,
        ZipFile: zipFile, 
    };

    try {
        const data = await lambda.updateFunctionCode(params).promise();

        return data;
    } catch (err) {
        throw new Error('Error updating Lambda function:', err);
    }
};

exports.createLambdaVersion = async (functionName, userCredentials, region) => {
    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    const params = {
        FunctionName: functionName,
    };

    try {
        const data = await lambda.publishVersion(params).promise();
        
        return data;
    } catch (err) {
        throw new Error('Error creating Lambda version:', err);
    }
};