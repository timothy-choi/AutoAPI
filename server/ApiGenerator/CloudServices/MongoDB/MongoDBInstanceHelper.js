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

exports.createProject = async (apiKey, projectUri, projectName, organizationId) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {

            const response = await apiClient.post(projectUri, {
                name: projectName,
                orgId: organizationId,  
            });

            return response.data; 
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(`Failed to create project: ${error.message}`);
      }
};

exports.deleteProject = async (projectUri, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
        const response = await apiClient.delete(projectUri);
        
        return response.data;
    };

    return await retryOperation(operation, 3, 1000);
  } catch (error) {
    throw new Error(error.message);
  }
};

exports.createCluster = async (apiKey, clusterUri, clusterInfo) => {
    try {
      const apiClient = GetApiClient(apiKey);
      
      var operation = async () => {
          const response = await apiClient.post(clusterUri, clusterInfo);
      
          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(`Failed to create cluster: ${error.message}`);
    }
};

exports.updateCluster = async (apiKey, clusterUri, clusterConfig) => {
    try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
            
          const response = await apiClient.patch(clusterUri, clusterConfig);
      
          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(`Failed to update cluster: ${error.message}`);
    }
};

exports.pauseCluster = async (apiKey, clusterUri) => {
  try {
      const apiClient = GetApiClient(apiKey);

      const operation = async () => {
          const response = await apiClient.patch(clusterUri, {
              paused: true,
          });
          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
  } catch (error) {
      throw new Error(`Failed to pause cluster: ${error.message}`);
  }
};

exports.resumeCluster = async (apiKey, clusterUri) => {
  try {
      const apiClient = GetApiClient(apiKey);

      const operation = async () => {
          const response = await apiClient.patch(clusterUri, {
              paused: false,
          });
          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
  } catch (error) {
      throw new Error(`Failed to resume cluster: ${error.message}`);
  }
};

exports.deleteCluster = async (apiKey, clusterUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
 
          const response = await apiClient.delete(clusterUri);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(`Failed to delete cluster: ${error.message}`);
    }
};

exports.createDatabase = async (apiKey, dbName, collectionName, dbUri) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {

            const response = await apiClient.post(dbUri, {
                databaseName: dbName,
                collection: collectionName,
            });

            return response.data;
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(`Failed to create database: ${error.message}`);
      }
};

exports.deleteDatabase = async (databaseUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {

          const response = await apiClient.delete(databaseUri, apiKey);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(error.message);
    }
};

exports.backupDatabase = async (databaseUri, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
        const response = await apiClient.post(databaseUri);

        return response.data;
    };

    return await retryOperation(operation, 3, 1000);
  } catch (error) {
    throw new Error(error.message);
  }
};

exports.restoreDatabase = async (projectUri, backupId, apiKey) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
        const response = await apiClient.post(projectUri,
          { backupId }
        );
        
        return response.data;
    };

    return await retryOperation(operation, 3, 1000);
  } catch (error) {
    throw new Error(error.message);
  }
};

exports.createCollection = async (apiKey, collectionParams, collectionUri) => {
    try {
        const apiClient = GetApiClient(apiKey);

        var operation = async () => {
            const response = await apiClient.post( collectionUri, collectionParams,
                {
                  headers: {
                    'Authorization': `Bearer ${apiKey}`,
                    'Content-Type': 'application/json',
                  },
                }
            );

            return response.data;
        };

        return await retryOperation(operation, 3, 1000);
    } catch (error) {
        throw new Error(`Failed to create database: ${error.message}`);
    }
};

exports.updateCollection = async (apiKey, collectionUri, updates) => {
    try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
          const response = await apiClient.patch(collectionUri, updates);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(error.message);
    }
};

exports.deleteCollection = async (apiKey, collectionUri) => {
    try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
          const response = await apiClient.delete(collectionUri);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
    } catch (error) {
      throw new Error(error.message);
    }
};

exports.createIndex = async (apiKey, indexUri, indexDetails) => {
  try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
          const response = await apiClient.post(indexUri, indexDetails);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
  } catch (error) {
      throw new Error(`Failed to create index: ${error.message}`);
  }
};

exports.updateIndex = async (apiKey, indexUri, indexConfig) => {
  try {
      const apiClient = GetApiClient(apiKey);

      var operation = async () => {
          const response = await apiClient.patch(indexUri, indexConfig);

          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
  } catch (error) {
      throw new Error(`Failed to update index: ${error.message}`);
  }
};