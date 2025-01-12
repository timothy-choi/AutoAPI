const AWS = require('aws-sdk');

exports.getApiGateway = async (gatewayId, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            restApiId: gatewayId
        };

        const response = await apiGateway.getRestApi(params).promise();

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};