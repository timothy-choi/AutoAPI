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