const AWS = require('aws-sdk');
const awsHelper = require('./AWSHelper');

const waitUntil = async (conditionFn, interval = 5000, timeout = 60000) => {
    const start = Date.now();
    while (true) {
        if (await conditionFn()) return true;
        if (Date.now() - start > timeout) throw new Error('Operation timed out');
        await new Promise(resolve => setTimeout(resolve, interval));
    }
};

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
        const zipFile = await awsHelper.downloadFile(functionInfo.bucketName, functionInfo.key, userCredentials, functionInfo.region);

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

        await waitUntil(async () => {
            const status = await lambda.getFunction({ FunctionName: functionInfo.functionName }).promise();
            return status.Configuration.State === 'Active';
        });

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

        await waitUntil(async () => {
            try {
                await lambda.getFunction({ FunctionName: functionName }).promise();
                return false; 
            } catch (err) {
                if (err.code === 'ResourceNotFoundException') return true;
                throw new Error(err.message); 
            }
        });
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

exports.updateLambdaFunction = async (functionName, bucketName, key, userCredentials, region) => {
    const zipFile = await awsHelper.downloadFile(bucketName, key, userCredentials, region);

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

        await waitUntil(async () => {
            const status = await lambda.getFunction({ FunctionName: functionName }).promise();
            return status.Configuration.LastUpdateStatus === 'Successful';
        });

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

        if (!data.Version) {
            throw new Error('Version creation failed.');
        }
        
        return data;
    } catch (err) {
        throw new Error('Error creating Lambda version:', err);
    }
};

exports.updateLambdaConfiguration = async (functionName, configUpdates, userCredentials, region) => {
    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    const params = {
        FunctionName: functionName,
        ...configUpdates, 
    };

    try {
        const data = await lambda.updateFunctionConfiguration(params).promise();

        await waitUntil(async () => {
            const status = await lambda.getFunction({ FunctionName: functionName }).promise();
            return status.Configuration.LastUpdateStatus === 'Successful';
        });

        return data;
    } catch (err) {
        throw new Error('Error updating Lambda configuration:', err);
    }
};

exports.addLambdaPermission = async (functionName, statementId, action, principal, sourceArn, userCredentials, region) => {
    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    const params = {
        FunctionName: functionName,
        StatementId: statementId,
        Action: action, 
        Principal: principal, 
        SourceArn: sourceArn,
    };

    try {
        const data = await lambda.addPermission(params).promise();

        return data;
    } catch (err) {
        throw new Error('Error adding permission to Lambda function:', err);
    }
};

exports.removeLambdaPermission = async (functionName, statementId, userCredentials, region) => {
    const lambda = new AWS.Lambda({
        accessKeyId: userCredentials.accessKeyId,
        secretAccessKey: userCredentials.secretAccessKey,
        region: region,
    });

    const params = {
        FunctionName: functionName,
        StatementId: statementId,
    };

    try {
        const data = await lambda.removePermission(params).promise();

        return data;
    } catch (err) {
        throw new Error('Error removing permission from Lambda function:', err);
    }
};