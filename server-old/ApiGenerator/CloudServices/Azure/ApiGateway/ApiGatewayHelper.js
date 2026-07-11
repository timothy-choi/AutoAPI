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

exports.createOperation = async (subscriptionId, resourceGroupName, serviceName, apiId, operationId, operationParams) => {
    try {
        const credential = new DefaultAzureCredential();
        const apimClient = new ApiManagementClient(credential, subscriptionId);

        var operation = async () => {
            const result = await apimClient.apiOperation.createOrUpdate(
                resourceGroupName,
                serviceName,
                apiId,
                operationId,
                operationParams
            );
        };

        await retryOperation(operation);

        const checkOperationStatus = async () => {
            try {
                const operation = await apimClient.apiOperation.get(
                    resourceGroupName,
                    serviceName,
                    apiId,
                    operationId
                );
                return operation ? operation : null;
            } catch (error) {
                if (error.code === 'ResourceNotFound') {
                    return null; 
                }
                throw error; 
            }
        };

        const provisionedOperation = await pollOperation(checkOperationStatus, POLLING_TIMEOUT_MS);

        return provisionedOperation;
    } catch (error) {
      console.error('Error creating operation:', error);
    }
};

exports.deleteApi = async (subscriptionId, resourceGroupName, serviceName, apiId) => {
    try {
        const credential = new DefaultAzureCredential();
        const apimClient = new ApiManagementClient(credential, subscriptionId);

        await retryOperation(() =>
            apimClient.api.deleteMethod(resourceGroupName, serviceName, apiId)
        );

        await pollOperation(async () => {
            try {
                await apimClient.api.get(resourceGroupName, serviceName, apiId);
                return false; 
            } catch (error) {
                if (error.statusCode === 404) {
                    return true; 
                }
                throw error; 
            }
        });
    } catch (error) {
        throw new Error('Error deleting API:', error.message);
    }
};

exports.deleteApiManagementService = async (subscriptionId, resourceGroupName, serviceName) => {
    try {
        const credential = new DefaultAzureCredential();
        const apimClient = new ApiManagementClient(credential, subscriptionId);

        await retryOperation(() =>
            apimClient.apiManagementService.deleteMethod(resourceGroupName, serviceName)
        );

        await pollOperation(async () => {
            try {
                await apimClient.apiManagementService.get(resourceGroupName, serviceName);
                return false;
            } catch (error) {
                if (error.statusCode === 404) {
                    return true; 
                }
                throw error; 
            }
        });
    } catch (error) {
        throw new Error('Error deleting API Management Service:', error.message);
    }
};
