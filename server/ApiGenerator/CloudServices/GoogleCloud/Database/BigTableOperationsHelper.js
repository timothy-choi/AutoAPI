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
}