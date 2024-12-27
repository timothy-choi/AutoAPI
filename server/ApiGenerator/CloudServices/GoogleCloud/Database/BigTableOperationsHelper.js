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
