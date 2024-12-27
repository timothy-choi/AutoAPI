const { Bigtable } = require('@google-cloud/bigtable');

const bigtable = new Bigtable();

exports.createInstance = async (options, clusters, instanceId) => {
    try {
        options.clusters = clusters;

        var [createInstanceResponse] = await bigtable.createInstance(instanceId, options);

        return createInstanceResponse;
    } catch (err) {
      throw new Error('Error creating instance:', err.message);
    }
};

exports.updateInstance = async (instanceId, options) => {
    try {
      const instance = bigtable.instance(instanceId); 
  
      const [updatedInstance] = await instance.partialUpdate(options);

      return updatedInstance;
    } catch (err) {
      throw new Error('Error updating instance:', err.message);
    }
};

exports.deleteInstance = async (instanceId) => {
    try {
        const instance = bigtable.instance(instanceId);
    
        await instance.delete();
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

      var clusterResponse = await cluster.create(options);
  
      return clusterResponse;
    } catch (err) {
      throw new Error('Error creating cluster:', err.message);
    }
};

exports.deleteCluster = async (instanceId, clusterId) => {
    try {
      const instance = bigtable.instance(instanceId);

      const cluster = instance.cluster(clusterId);

      await cluster.delete();
    } catch (err) {
      console.error('Error deleting cluster:', err.message);
    }
};

exports.updateCluster = async (instanceId, clusterId, options, ) => {
    try {
        const instance = bigtable.instance(instanceId);
        const cluster = instance.cluster(clusterId);

        const [operation] = await cluster.setMetadata(options);
        await operation.promise();
    
        return operation;
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
    
        var tableResponse = await table.create({ columnFamilies });

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
  
      const [operation] = await table.setMetadata(metadata);

      await operation.promise();

      return operation;
    } catch (err) {
      throw new Error('Error updating table:', err.message);
    }
};

exports.deleteTable = async (instanceId, tableId) => {
    try {
      const table = bigtable.instance(instanceId).table(tableId);
  
      await table.delete();
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
  