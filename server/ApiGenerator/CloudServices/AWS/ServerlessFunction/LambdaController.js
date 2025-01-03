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