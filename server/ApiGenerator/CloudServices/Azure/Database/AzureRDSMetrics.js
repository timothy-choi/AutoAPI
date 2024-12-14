const { DefaultAzureCredential } = require("@azure/identity");
const { ResourceHealthManagementClient } = require("@azure/arm-resourcehealth");

exports.getDatabaseHealthStatus = async (resourceId, subscriptionId) => {
    try {
        const credential = new DefaultAzureCredential();

        const healthClient = new ResourceHealthManagementClient(credential, subscriptionId);

        const healthStatus = await healthClient.availabilityStatuses.get(resourceId);

        return healthStatus;
    } catch (error) {
        throw new Error(error.message);
    }
}