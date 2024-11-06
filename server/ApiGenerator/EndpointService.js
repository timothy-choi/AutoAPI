const EndpointsDefinition = require('./Endpoints');

exports.GetEndpointsById = async (endpointId) => {
    var uuidVal = mongoose.types.ObjectId(endpointId);

    var endpoints = await EndpointsDefinition.findById(uuidVal);

    return endpoints;
}

exports.GetEndpointByProject = async (projectName) => {
    var endpoints = await EndpointsDefinition.findOne({ProjectName: projectName});

    return endpoints;
}

exports.CreateEndpoints = async (endpointsInfo) => {
    var endpointVal = await this.GetEndpointByProject(endpointsInfo.projectName);

    if (endpointVal) {
        throw new Error('Endpoints already exist');
    }

    var endpoints = await EndpointsDefinition.Create({
        ProjectId: endpointsInfo.projectId,
        ProjectName: endpointsInfo.projectName,
        EndpointType: endpointsInfo.type,
        CreatedAt: Date.now,
        EndpointDescription: endpointsInfo.description
    });

    return endpoints;
}

exports.DeleteEndpoints = async (endpointId) => {
    try {
        var endpoints = await this.GetEndpointsById(endpointId);

        if (!endpoints) {
            throw new Exception('endpoints does not exist');
        }

        await endpoints.destroy();
    } catch (error) {
        throw new Exception('Can not delete endpoints');
    }
}