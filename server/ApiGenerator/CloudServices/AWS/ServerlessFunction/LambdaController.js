const { protos } = require('@google-cloud/bigtable');
const lambdaHelper = require('./LambdaHelper');

exports.getLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunction = await lambdaHelper.getLambdaFunction(req.lambdaFunctionName, userCredentials, req.body.region);

        return res.status(201).send(lambdaFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.listLambdaFunctions = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunctions = await lambdaHelper.listLambdaFunctions(userCredentials, req.body.region);

        return res.status(201).send(lambdaFunctions);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunction = await lambdaHelper.createLambdaFunction(req.body.lambdaFunctionParams, userCredentials);

        return res.status(201).send(lambdaFunction);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunctionResponse = await lambdaHelper.updateLambdaFunction(req.body.lambdaFunctionName, req.body.bucketName, req.body.key, userCredentials, req.body.region);

        return res.status(200).send(lambdaFunctionResponse);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.createLambdaFunctionVersion = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunctionVersion = await lambdaHelper.createLambdaVersion(req.lambdaFunctionName, userCredentials, req.region);

        return res.status(201).send(lambdaFunctionVersion);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.updateLambdaFunctionConfiguration = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var lambdaFunctionConfiguration = await lambdaHelper.updateLambdaConfiguration(req.body.lambdaFunctionName, req.body.configuration, userCredentials, req.body.region);

        return res.status(200).send(lambdaFunctionConfiguration);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.addLambdaFunctionPermission = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var response = await lambdaHelper.addLambdaPermission(req.body.lambdaFunctionName, req.body.action, req.body.principal, req.body.sourceArn, req.body.statementId, userCredentials, req.body.region);

        return res.status(201).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.removeLambdaFunctionPermission = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await lambdaHelper.removeLambdaPermission(req.body.lambdaFunctionName, req.body.statementId, userCredentials, req.body.region);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.deleteLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await lambdaHelper.deleteLambdaFunction(req.lambdaFunctionName, userCredentials, req.region);

        return res.status(200).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.backupLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await lambdaHelper.backupLambdaFunction(req.body.functionName, req.body.bucketName, req.body.key, req.body.zipPath, userCredentials, req.body.region);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.restoreLambdaFunction = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await lambdaHelper.restoreLambdaFunction(req.body.functionName, req.body.bucketName, req.body.key, req.body.zipPath, userCredentials, req.body.region);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.getLambdaMetricsAndHealthStatus = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.userCredentialsInfo) {
            userCredentials = req.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        var response = await lambdaHelper.getLambdaMetricsAndHealthStatus(req.body.lambdaFunctionName, req.body.payload, userCredentials, req.body.region);

        return res.status(201).send(response);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};

exports.startOrStopLambdaMetricsPolling = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.userCredentialsInfo) {
            userCredentials = req.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretName);
        }

        await lambdaHelper.startOrStopLambdaMetricsPolling(req.body.functionName, req.body.payloadInfo, userCredentials, req.body.userRegion);

        return res.status(201).send(null);
    } catch (error) {
        return res.status(500).send(error.message);
    }
};