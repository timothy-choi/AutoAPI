const AWS = require('aws-sdk');

const waitUntil = async (conditionFn, interval = 5000, timeout = 60000) => {
    const start = Date.now();
    while (true) {
        if (await conditionFn()) return true;
        if (Date.now() - start > timeout) throw new Error('Operation timed out');
        await new Promise(resolve => setTimeout(resolve, interval));
    }
};

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

        await waitUntil(async () => {
            const gateway = await exports.getApiGateway(response.id, userCredentials, userRegion);
            return gateway && gateway.status === 'AVAILABLE';
        }, 5000, 60000);

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

        await waitUntil(async () => {
            const gateway = await exports.getApiGateway(gatewayId, userCredentials, userRegion);
            return gateway.resources.some(r => r.id === response.id); 
        }, 5000, 60000);

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createMethod = async (gatewayId, authId, authType, resourceId, httpMethod, integrationParams, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            authorizationType: authType,
            httpMethod: httpMethod,
            resourceId: resourceId,
            restApiId: gatewayId,
            authorizerId: authId
        };

        const response = await apiGateway.putMethod(params).promise();

        await apiGateway.putIntegration(integrationParams).promise();

        await waitUntil(async () => {
            const methods = await apiGateway.getResource({ restApiId: gatewayId, resourceId }).promise();
            return methods.resourceMethods && methods.resourceMethods[httpMethod];
        }, 5000, 60000);

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

        await waitUntil(async () => {
            try {
                await exports.getApiGateway(gatewayId, userCredentials, userRegion);
                return false;
            } catch (error) {
                if (error.code === 'NotFoundException') return true;
                throw error;
            }
        }, 5000, 60000);

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};