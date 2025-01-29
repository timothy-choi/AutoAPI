const AWS = require('aws-sdk');

exports.createApiKey = async (gatewayId, apiKeyName, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            name: apiKeyName,
            enabled: true,
            stageKeys: [{ restApiId: gatewayId, stageName: 'prod' }]
        };

        const response = await apiGateway.createApiKey(params).promise();

        await waitUntil(async () => {
            const keys = await apiGateway.getApiKeys().promise();
            return keys.items.some(k => k.id === response.id);
        }, 5000, 60000);

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createUsagePlan = async (usagePlanName, limitInfo, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            name: usagePlanName,
            throttle: {
                rateLimit: limitInfo.rateLimit,
                burstLimit: limitInfo.burstLimit
            }
        };

        const response = await apiGateway.createUsagePlan(params).promise();

        await waitUntil(async () => {
            const plans = await apiGateway.getUsagePlans().promise();
            return plans.items.some(p => p.id === response.id);
        }, 5000, 60000);

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.linkApiKeyToUsagePlan = async (usagePlanId, apiKeyId, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            usagePlanId: usagePlanId,
            keyId: apiKeyId,
            keyType: 'API_KEY'
        };

        const response = await apiGateway.createUsagePlanKey(params).promise();

        await waitUntil(async () => {
            const keys = await apiGateway.getUsagePlanKeys({ usagePlanId: usagePlanId }).promise();
            return keys.items.some(k => k.id === response.id);
        });

        return response;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.deleteApiKey = async (apiKeyId, userCredentials, userRegion) => {
    try {
        const apiGateway = new AWS.APIGateway({ credentials: userCredentials, region: userRegion });

        const params = {
            apiKey: apiKeyId
        };

        await apiGateway.deleteApiKey(params).promise();
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};