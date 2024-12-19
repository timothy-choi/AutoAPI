const GetApiClient = require('./MongoDBApiHelper');

exports.createServiceAccount = async (mongoServiceAccountUri, name, apiKey) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const response = await apiClient.post(mongoServiceAccountUri, {
            name,
            description: "Service account for connecting to cloud services",
        });

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
};
  