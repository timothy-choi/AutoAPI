const { ApiManagementClient } = require('@azure/arm-apimanagement');
const { DefaultAzureCredential } = require('@azure/identity');

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));


const retryOperation = async (operation) => {
    let attempts = 0;
    while (attempts < 5) {
        try {
            return await operation();
        } catch (error) {
            attempts++;
            if (attempts >= 5) throw error;
            await wait(2000);
        }
    }
};

const pollOperation = async (checkCondition, timeout = 5000) => {
    const start = Date.now();
    while (Date.now() - start < timeout) {
        const result = await checkCondition();
        if (result) return result;
        await wait(6000);
    }
    throw new Error('Polling operation timed out.');
};

exports.createApiManagementService = async (subscriptionId, resourceGroupName, serviceName, parameters) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);
  
    try {
        const result = await client.apiManagementService.beginCreateOrUpdateAndWait(
            resourceGroupName,
            serviceName,
            parameters
        );

        return result;
    } catch (error) {
      throw new Error('Error creating API Management service:', error.message);
    }
};

exports.createApi = async (subscriptionId, resourceGroupName, serviceName, apiId, apiParameters) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);

    try {
        var operation = async () => {
            const result = await client.api.createOrUpdate(
                resourceGroupName,
                serviceName,
                apiId,
                apiParameters
            );

            return result;
        }; 

        const result = await retryOperation(operation);

        const checkProvisioningStatus = async () => {
            try {
                const api = await apimClient.api.get(resourceGroupName, serviceName, apiId);
                return api.isCurrent ? api : null;
            } catch (error) {
                if (error.code === 'ResourceNotFound') {
                    return null;
                }
                throw error;
            }
        };

        const provisionedApi = await pollOperation(checkProvisioningStatus);

        return provisionedApi;
    } catch (error) {
        throw new Error('Error creating API:', error.message);
    }
};
  