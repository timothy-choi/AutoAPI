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

exports.scaleCluster = async (apiKey, clusterUri, scaleParams) => {
  try {
      const apiClient = GetApiClient(apiKey);

      const operation = async () => {
          const response = await apiClient.patch(clusterUri, scaleParams);
          return response.data;
      };

      return await retryOperation(operation, 3, 1000);
  } catch (error) {
      throw new Error(`Failed to scale cluster: ${error.message}`);
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

exports.deleteDatabase = async (databaseUri, apiKey) => {
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

exports.enableAutomatedBackups = async (apiKey, clusterUri, backupConfig) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
      await apiClient.post(clusterUri, JSON.stringify(backupConfig));
    };

    await retryOperation(operation, 3, 20, 3); 
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

exports.deleteIndex = async (apiKey, indexUri) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
        const response = await apiClient.delete(indexUri);

        return response.data;
    };

    return await retryOperation(operation, 3, 1000);
  } catch (error) {
    throw new Error(`Failed to delete index: ${error.message}`);
  }
};

exports.addIPWhitelist = async (apiKey, projectUri, ipAddress) => {
  try {
    const payload = {
      cidrBlock: ipAddress,
      comment: "Added via API",
    };

    const uri = `${projectUri}/accessList`;
    const response = await axios.post(uri, payload, {
        headers: BASE_HEADERS(apiKey),
    });

    return response.data; 
  } catch (error) {
    throw new Error(`Failed to delete index: ${error.message}`);
  }
};

exports.enableEncryptionAtRest = async (apiKey, projectUri, encryptionAtRestConfig) => {
  try {
      const headers = { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" };

      const response = await axios.patch(projectUri, encryptionAtRestConfig, { headers });
      return response.data;
  } catch (error) {
      throw new Error(`Failed to enable encryption at rest: ${error.message}`);
  }
}

exports.updateSecuritySettings = async (apiKey, projectUri, securityConfig) => {
  try {
      const headers = { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" };

      const response = await axios.patch(projectUri, securityConfig, { headers });
      return response.data;
  } catch (error) {
      throw new Error(`Failed to update security settings: ${error.message}`);
  }
}

exports.updateClusterMaintenanceWindow = async (apiKey, clusterUri, maintenanceConfig) => {
  try {
    const apiClient = GetApiClient(apiKey);

    var operation = async () => {
        const response = await apiClient.post(clusterUri, JSON.stringify(maintenanceConfig));

        return response.data; 
    };

    return retryOperation(operation, 3, 20, 3);
  } catch (error) {
    throw new Error(error.message);
  }
}

exports.createDatabaseUser = async (apiKey, projectId, username, password, dbName, roles) => {
  try {
      const url = `${BASE_URL}/groups/${projectId}/databaseUsers`;
      const headers = { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" };
      const data = {
          username,
          password,
          databaseName: dbName,
          roles,
      };

      const response = await axios.post(url, data, { headers });
      return response.data;
  } catch (error) {
      throw new Error(`Failed to create database user: ${error.message}`);
  }
}

exports.updateDatabaseUser = async (apiKey, projectId, username, roles) => {
  try {
      const url = `${BASE_URL}/groups/${projectId}/databaseUsers/admin/${username}`;
      const headers = { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" };
      const data = { roles };

      const response = await axios.patch(url, data, { headers });
      return response.data;
  } catch (error) {
      throw new Error(`Failed to update database user: ${error.message}`);
  }
}

exports.deleteDatabaseUser = async (apiKey, projectId, username) => {
  try {
      const url = `${BASE_URL}/groups/${projectId}/databaseUsers/admin/${username}`;
      const headers = { "Authorization": `Bearer ${apiKey}` };

      const response = await axios.delete(url, { headers });
      return response.data;
  } catch (error) {
      throw new Error(`Failed to delete database user: ${error.message}`);
  }
}