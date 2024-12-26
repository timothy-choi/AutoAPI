const AWS = require('aws-sdk');

exports.QueryOne = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("filter" in req.body.payloadInfo) || (req.body.payloadInfo.filter == null || req.body.payloadInfo.projection == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.QueryMany = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("filter" in req.body.payloadInfo) || (req.body.payloadInfo.filter == null || req.body.payloadInfo.options == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.Aggregate = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("pipeline" in req.body.payloadInfo) || (req.body.payloadInfo.pipeline == null || req.body.payloadInfo.pipeline == {})) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.CountDocuments = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("query" in req.body.payloadInfo) || (req.body.payloadInfo.query == null || req.body.payloadInfo.query == "")) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.InsertOne = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("document" in req.body.payloadInfo) || (req.body.payloadInfo.document == null)) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.InsertMany = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("documents" in req.body.payloadInfo) || (req.body.payloadInfo.documents == null || req.body.payloadInfo.documents.length == 0)) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.Upsert = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("query" in req.body.payloadInfo) || req.body.payloadInfo.query == null || !("updateDoc" in req.body.payload) || req.body.payloadInfo.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(201).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.UpdateOne = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("query" in req.body.payloadInfo) || req.body.payloadInfo.query == null || !("updateDoc" in req.body.payloadInfo) || req.body.payloadInfo.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }

        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};

exports.UpdateMany = async (req, res) => {
    try {
        let userCredentials = {};
        if (req.body.userCredentialsInfo) {
            userCredentials = req.body.userCredentialsInfo;
        } else {
            userCredentials = await AWSHelper.getAWSCredentials(req.body.secretAccessName);
        }

        const lambda = new AWS.Lambda({
            credentials: new AWS.Credentials(userCredentials.accessKey, userCredentials.userSecretKey, userCredentials.sessionToken),
            region: req.body.userRegion
        });

        if (!("query" in req.body.payloadInfo) || req.body.payloadInfo.query == null || !("updateDoc" in req.body.payloadInfo) || req.body.payloadInfo.updateDoc == null) {
            return res.status(400).send({"error": "invalid input"});
        }

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        const lambdaResponse = await lambda.invoke(params).promise();
        const queryResponse = JSON.parse(lambdaResponse.Payload);

        if (queryResponse.status !== 200) {
            return res.status(500).send({ error: "Could not get usage report and health status" });
        }


        return res.status(200).send({"queryResponse": queryResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};