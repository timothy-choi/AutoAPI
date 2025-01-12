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

exports.createApiGateway = async (apiName, description, endpointConfig, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            name: apiName,
            description: description,
            endpointConfiguration: endpointConfig
        };

        const response = await apiGateway.createRestApi(params).promise();

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createResource = async (parentPathId, gatewayId, resourcePath, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            parentId: parentPathId,
            pathPart: resourcePath,
            restApiId: gatewayId
        };

        const response = await apiGateway.createResource(params).promise();

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.deleteApiGateway = async (gatewayId, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            restApiId: gatewayId
        };

        const response = await apiGateway.deleteRestApi(params).promise();

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};