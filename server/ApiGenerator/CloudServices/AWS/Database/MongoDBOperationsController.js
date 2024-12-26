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

        const params = {
            FunctionName: req.body.lambdaFunctionName,
            Payload: JSON.stringify(req.body.payloadInfo)
        };

        var dataPayloadResponse = null;

        lambda.invoke(params, (err, data) => {
            if (err) {
                throw new Error(err.message);
            } else {
                var queryResponse = JSON.parse(data.Payload);

                if (dataResponse.status != 200) {
                    throw new Error('Could not get usage report and health status');
                } else {
                    dataPayloadResponse = queryResponse;
                }
            }
        });

        return res.status(200).send({"queryResponse": dataPayloadResponse});
    } catch (error) {
        return res.status(500).send(null);
    }
};