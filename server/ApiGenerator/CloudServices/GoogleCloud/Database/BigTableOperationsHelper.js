const { Bigtable } = require('@google-cloud/bigtable');

const bigtable = new Bigtable();

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const retryOperation = async (operation, retries = 3, delayMs = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation();
        } catch (err) {
            attempt++;
            if (attempt >= retries) {
                throw new Error(`Operation failed after ${retries} attempts: ${err.message}`);
            }
            console.log(`Retrying operation (attempt ${attempt + 1})...`);
            await delay(delayMs);
            delayMs *= 2;  
        }
    }
};

const waitForOperationCompletion = async (operation, interval = 5000, maxAttempts = 20) => {
    let attempts = 0;

    while (attempts < maxAttempts) {
        const [status] = await operation.getMetadata();
        if (status.done) {
            return status;
        }
        attempts++;
        console.log(`Waiting for operation to complete... Attempt ${attempts}/${maxAttempts}`);
        await delay(interval); 
    }

    throw new Error('Operation did not complete within the expected time');
};

exports.createInstance = async (options, clusters, instanceId) => {
    try {
        options.clusters = clusters;

        var operation = async () => {

            var [createInstanceResponse] = await bigtable.createInstance(instanceId, options);

            return createInstanceResponse;
        };

        const instanceResponse = await retryOperation(operation);
        
        await waitForOperationCompletion(instanceResponse);

        return instanceResponse;
    } catch (err) {
      throw new Error('Error creating instance:', err.message);
    }
};

exports.updateInstance = async (instanceId, options) => {
    try {
      const instance = bigtable.instance(instanceId); 

      var operation = async () => {
        const [updatedInstance] = await instance.partialUpdate(options);

        return updatedInstance;
      };

      var instanceResponse = await retryOperation(operation);

      await waitForOperationCompletion(instanceResponse);

      return instanceResponse;
    } catch (err) {
      throw new Error('Error updating instance:', err.message);
    }
};

exports.deleteInstance = async (instanceId) => {
    try {
        const instance = bigtable.instance(instanceId);

        var operation = async () => {
            var deleteResponse = await instance.delete();

            return deleteResponse;
        };

        var deleteResponse = await retryOperation(operation);

        await waitForOperationCompletion(deleteResponse);
    } catch (err) {
        throw new Error('Error updating instance:', err.message);
    }
};

exports.getInstance = async (instanceId) => {
    try {
      const instance = bigtable.instance(instanceId);

      const [metadata] = await instance.getMetadata();

      return metadata;
    } catch (err) {
      throw new Error('Error getting instance:', err.message);
    }
};

exports.createCluster = async (instanceId, clusterId, options) => {
    try {
      const instance = bigtable.instance(instanceId);
  
      const cluster = instance.cluster(clusterId);

      var operation = async () => {
        var clusterResponse = await cluster.create(options);
  
        return clusterResponse;
      };

      var clusterResponse = await retryOperation(operation);

      await waitForOperationCompletion(clusterResponse);
      
      return clusterResponse;
    } catch (err) {
      throw new Error('Error creating cluster:', err.message);
    }
};

exports.deleteCluster = async (instanceId, clusterId) => {
    try {
      const instance = bigtable.instance(instanceId);

      const cluster = instance.cluster(clusterId);

      var operation = async () => {
        var deleteResponse = await cluster.delete();

        return deleteResponse;
      };

      var deleteResponse = await retryOperation(operation);

      await waitForOperationCompletion(deleteResponse);
    } catch (err) {
      throw new Error('Error deleting cluster:', err.message);
    }
};

exports.updateCluster = async (instanceId, clusterId, options, ) => {
    try {
        const instance = bigtable.instance(instanceId);
        const cluster = instance.cluster(clusterId);

        const updateClusterOperation = async () => {
            const [operation] = await cluster.setMetadata(options);
            return operation;
        };

        const res = await retryOperation(updateClusterOperation);

        await waitForOperationCompletion(res);

        return res;
    } catch (err) {
        throw new Error('Error updating cluster:', err.message);
    }
};

exports.getClusterInfo = async (instanceId, clusterId) => {
    try {
        const instance = bigtable.instance(instanceId);
        const cluster = instance.cluster(clusterId);
    
        const [metadata] = await cluster.get();
    
        return metadata;
    } catch (err) {
        throw new Error('Error getting cluster information:', err.message);
    }
};

exports.createTable = async (instanceId, tableId, columnFamilies) => {
    try {
        const instance = bigtable.instance(instanceId);
        const table = instance.table(tableId);

        var operation = async () => {
            var tableResponse = await table.create({ columnFamilies });

            return tableResponse;
        };

        var tableResponse = await retryOperation(operation);

        await waitForOperationCompletion(tableResponse);

        return tableResponse;
    } catch (err) {
        throw new Error('Error creating table:', err.message);
    }
};

exports.updateTableColumnFamily = async (instanceId, tableId, columnFamilyId, options) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const [metadata] = await table.getMetadata();
  
      metadata.columnFamilies[columnFamilyId] = options;

      var operation = async () => {
        const [updateResponse] = await table.setMetadata(metadata);

        return updateResponse;
      };

      var updateResponse = await retryOperation(operation);

      await waitForOperationCompletion(updateResponse);

      return updateResponse;
    } catch (err) {
      throw new Error('Error updating table:', err.message);
    }
};

exports.deleteColumnFamily = async (instanceId, tableId, columnFamilyId) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const [metadata] = await table.getMetadata();
      const columnFamilies = metadata.columnFamilies || {};
  
      if (!columnFamilies[columnFamilyId]) {
        throw new Error(`Column family ${columnFamilyId} does not exist.`);
      }

      var operation = async () => {
        delete columnFamilies[columnFamilyId];
    
        var res = await table.setMetadata({ columnFamilies });

        return res;
      };

      var deleteResponse = await retryOperation(operation);

      await waitForOperationCompletion(deleteResponse);
    } catch (err) {
      throw new Error('Error deleting column family:', err.message);
    }
};

exports.deleteTable = async (instanceId, tableId) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);

      var operation = async () => {
        var deleteResponse = await table.delete();

        return deleteResponse;
      };

      var deleteResponse = await retryOperation(operation);

      await waitForOperationCompletion(deleteResponse);
    } catch (err) {
      throw new Error('Error deleting table:', err.message);
    }
};

//query operations

exports.getRowsByPrefix = async (instanceId, tableId, rowKeyPrefix) => {
  try {
    const table = bigtable.instance(instanceId).table(tableId);

    const filter = {
      rowKey: { regex: `^${rowKeyPrefix}` },  
    };

    const [rows] = await table.getRows({ filter });

    return rows;
  } catch (err) {
    throw new Error('Error querying rows by prefix:', err.message);
  }
};

exports.getRowsByColumnFamily = async (instanceId, tableId, columnFamilyId) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const filter = {
        columnFamilyId: columnFamilyId,  
      };
  
      const [rows] = await table.getRows({ filter });
  
      return rows;
    } catch (err) {
      throw new Error('Error querying rows by prefix:', err.message);
    }
};

exports.getSpecificRow = async (instanceId, tableId, rowKey) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);

      const row = await table.row(rowKey).get();
  
      return row;
    } catch (err) {
      throw new Error('Error querying rows by prefix:', err.message);
    }
};

exports.getFilteredRows = async (instanceId, tableId, rowKeyPrefix, columnFamilyId) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const filter = {
        rowKey: { regex: `^${rowKeyPrefix}` },
        columnFamilyId: columnFamilyId,  
      };
  
      const [rows] = await table.getRows({ filter });
  
      return rows;
    } catch (err) {
      throw new Error('Error querying rows by prefix:', err.message);
    }
};

exports.getColumnsByQualifier = async (instanceId, tableId, columnFamilyId, columnQualifier) => {
    try {
        const table = bigtable.instance(instanceId).table(tableId);

        const filter = {
          columnQualifier: columnQualifier,
          columnFamilyId: columnFamilyId,
        };
    
        const [rows] = await table.getRows({ filter });
  
        return rows;
    } catch (err) {
        throw new Error('Error querying rows by prefix:', err.message);
    }
};

exports.insertData = async (instanceId, tableId, rowKey, data) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const mutations = [];
      for (const [columnFamily, columnData] of Object.entries(data)) {
        for (const [columnQualifier, value] of Object.entries(columnData)) {
          mutations.push({
            setCell: {
              columnFamilyId: columnFamily,
              columnQualifier: columnQualifier,
              value: value,
            },
          });
        }
      }
  
      await table.row(rowKey).mutate(mutations);
    } catch (err) {
      throw new Error('Error inserting data:', err.message);
    }
};

exports.insertMultipleRows = async (instanceId, tableId, rowsData) => {
  try {
    const table = bigtable.instance(instanceId).table(tableId);

    const rows = rowsData.map(row => {
      const mutations = [];
      for (const [columnFamily, columnData] of Object.entries(row.data)) {
        for (const [columnQualifier, value] of Object.entries(columnData)) {
          mutations.push({
            setCell: {
              columnFamilyId: columnFamily,
              columnQualifier: columnQualifier,
              value: value,
            },
          });
        }
      }
      return { key: row.rowKey, mutations };
    });

    await table.insertRows(rows);
  } catch (err) {
    throw new Error('Error inserting multiple rows:', err.message);
  }
};

exports.insertWithCondition = async (instanceId, tableId, rowKey, data, condition) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const mutations = [];
      for (const [columnFamily, columnData] of Object.entries(data)) {
        for (const [columnQualifier, value] of Object.entries(columnData)) {
          mutations.push({
            setCell: {
              columnFamilyId: columnFamily,
              columnQualifier: columnQualifier,
              value: value,
            },
          });
        }
      }

      await table.row(rowKey).mutate(mutations, { condition });
    } catch (err) {
      console.error('Error inserting with condition:', err.message);
    }
  }
  
  exports.updateSingleRow = async (instanceId, tableId, rowKey, mutations) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).mutate(mutations);
    } catch (err) {
        throw new Error('Error updating row:', err.message);
    }
   };

   exports.updateMultipleRows = async (instanceId, tableId, rowsData) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const rows = rowsData.map(row => {
        const mutations = [];
        for (const [columnFamily, columnData] of Object.entries(row.data)) {
          for (const [columnQualifier, value] of Object.entries(columnData)) {
            mutations.push({
              setCell: {
                columnFamilyId: columnFamily,
                columnQualifier: columnQualifier,
                value: value,
              },
            });
          }
        }
        return { key: row.rowKey, mutations };
      });
  
      await table.mutate(rows);
    } catch (err) {
      throw new Error('Error updating rows:', err.message);
    }
  };

  exports.conditionalUpdate = async (instanceId, tableId, rowKey, filter, mutations) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).mutate(mutations, { filter });
    } catch (err) {
      throw new Error('Error performing conditional update:', err.message);
    }
  };

  exports.incrementCellValue = async (instanceId, tableId, rowKey, columnFamily, columnQualifier, incrementValue) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).increment(columnFamily, columnQualifier, incrementValue);
    } catch (err) {
      throw new Error('Error incrementing cell:', err.message);
    }
  };

  exports.deleteAndUpdateRow = async (instanceId, tableId, rowKey, newData) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).delete();
  
      const mutations = [];
      for (const [columnFamily, columnData] of Object.entries(newData)) {
        for (const [columnQualifier, value] of Object.entries(columnData)) {
          mutations.push({
            setCell: {
              columnFamilyId: columnFamily,
              columnQualifier: columnQualifier,
              value: value,
            },
          });
        }
      }
  
      await table.row(rowKey).mutate(mutations);
    } catch (err) {
      throw new Error('Error updating row:', err.message);
    }
  };

  exports.deleteRow = async (instanceId, tableId, rowKey) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).delete();
    } catch (err) {
      throw new Error('Error deleting row:', err.message);
    }
  };

  exports.deleteCells = async (instanceId, tableId, rowKey, columnFamily, columnQualifier) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.row(rowKey).deleteCells([
        `${columnFamily}:${columnQualifier}`,
      ]);
    } catch (err) {
      throw new Error('Error deleting cell:', err.message);
    }
  };

  exports.deleteRows = async (instanceId, tableId, rowKeys) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      const rows = rowKeys.map(rowKey => ({
        key: rowKey,
        mutations: [
          { deleteFromRow: {} }, 
        ],
      }));
  
      await table.mutate(rows);
    } catch (err) {
      throw new Error('Error deleting rows:', err.message);
    }
  };

  exports.deleteRowsByPrefix = async (instanceId, tableId, rowKeyPrefix) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.deleteRows(rowKeyPrefix);
    } catch (err) {
      throw new Error('Error deleting rows by prefix:', err.message);
    }
  };