const GetApiClient = require('./MongoDBApiHelper');

const retryOperation = async (operation, retries = 3, delay = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation(); 
        } catch (error) {
            attempt++;
            if (attempt >= retries) {
                throw new Error(`Operation failed after ${retries} attempts: ${error.message}`);
            }
            await new Promise(resolve => setTimeout(resolve, delay * Math.pow(2, attempt - 1))); 
        }
    }
  };

exports.createServiceAccount = async (mongoServiceAccountUri, name, apiKey) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {
            const response = await apiClient.post(mongoServiceAccountUri, {
                name,
                description: "Service account for connecting to cloud services",
            });

            return response.data;
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(error.message);
    }
};
  