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

exports.deleteProject = async (projectUri, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    const response = await apiClient.delete(projectUri);
    
    return response.data;
  } catch (error) {
    throw new Error(error.message);
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

exports.updateCluster = async (apiKey, clusterUri, clusterConfig) => {
    try {
      const apiClient = GetApiClient(apiKey);
            
      const response = await apiClient.patch(clusterUri, clusterConfig);
  
      return response.data;
    } catch (error) {
      throw new Error(`Failed to update cluster: ${error.message}`);
    }
};

exports.deleteCluster = async (apiKey, clusterUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      const response = await apiClient.delete(clusterUri);

      return response.data;
    } catch (error) {
      throw new Error(`Failed to delete cluster: ${error.message}`);
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

exports.deleteDatabase = async (databaseUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      const response = await apiClient.delete(databaseUri, apiKey);

      return response.data;
    } catch (error) {
      throw new Error(error.message);
    }
};

exports.backupDatabase = async (databaseUri, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    const response = await apiClient.post(databaseUri);

    return response.data;
  } catch (error) {
    throw new Error(error.message);
  }
};

exports.restoreDatabase = async (projectUri, backupId, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    const response = await apiClient.post(projectUri,
      { backupId }
    );
    
    return response.data;
  } catch (error) {
    throw new Error(error.message);
  }
};

exports.createCollection = async (apiKey, collectionParams, collectionUri) => {
    try {
        const apiClient = GetApiClient(apiKey);
        
        const response = await apiClient.post( collectionUri, collectionParams,
            {
              headers: {
                'Authorization': `Bearer ${apiKey}`,
                'Content-Type': 'application/json',
              },
            }
        );

        return response.data;
    } catch (error) {
        throw new Error(`Failed to create database: ${error.message}`);
    }
};

exports.updateCollection = async (apiKey, collectionUri, updates) => {
    try {
      const apiClient = GetApiClient(apiKey);

      const response = await apiClient.patch(collectionUri, updates);

      return response.data;
    } catch (error) {
      throw new Error(error.message);
    }
};

exports.deleteCollection = async (apiKey, collectionUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      const response = await apiClient.delete(collectionUri);

      return response.data;
    } catch (error) {
      throw new Error(error.message);
    }
};