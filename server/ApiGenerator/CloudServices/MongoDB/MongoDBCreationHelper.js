const GetApiClient = require('./MongoDBApiHelper');

exports.createProject = async (apiKey, projectUri, projectName, organizationId) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const response = await apiClient.post(projectUri, {
            name: projectName,
            orgId: organizationId,  
        });

        return response.data; 
    } catch (error) {
        throw new Error(`Failed to create project: ${error.message}`);
      }
};

exports.createCluster = async (apiKey, clusterUri, clusterInfo) => {
    try {
      const apiClient = GetApiClient(apiKey);
            
      const response = await apiClient.post(clusterUri, clusterInfo);
  
      return response.data;
    } catch (error) {
      throw new Error(`Failed to create cluster: ${error.message}`);
    }
};

exports.createDatabase = async (apiKey, dbName, collectionName, dbUri) => {
    try {
        const apiClient = GetApiClient(apiKey);

        const response = await apiClient.post(dbUri, {
            databaseName: dbName,
            collection: collectionName,
        });

        return response.data;
    } catch (error) {
        throw new Error(`Failed to create database: ${error.message}`);
      }
};