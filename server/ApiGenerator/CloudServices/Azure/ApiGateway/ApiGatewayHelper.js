const { ApiManagementClient } = require('@azure/arm-apimanagement');
const { DefaultAzureCredential } = require('@azure/identity');

exports.createApiManagementService = async (subscriptionId, resourceGroupName, serviceName, parameters) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);
  
    try {
      const result = await client.apiManagementService.beginCreateOrUpdateAndWait(
        resourceGroupName,
        serviceName,
        parameters
      );
      console.log(`API Management service created: ${result.name}`);
    } catch (error) {
      console.error('Error creating API Management service:', error);
    }
};

exports.createApi = async (subscriptionId, resourceGroupName, serviceName, apiId, apiParameters) => {
    const credential = new DefaultAzureCredential();
    const client = new ApiManagementClient(credential, subscriptionId);

    try {
      const result = await client.api.createOrUpdate(
        resourceGroupName,
        serviceName,
        apiId,
        apiParameters
      );
      console.log(`API created: ${result.displayName}`);
    } catch (error) {
      console.error('Error creating API:', error);
    }
};
  