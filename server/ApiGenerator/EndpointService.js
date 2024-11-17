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

exports.AddEndpointHeader = async (endpointId, endpointHeader, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointHeaders.push(endpointHeader);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointModel = async (endpointId, endpointModel, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointModels.push(endpointModel);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointDatabase = async (endpointId, endpointModelDatabase, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointDatabases.push(endpointModelDatabase);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointServerlessFunctions = async (endpointId, endpointServerlessFunctions, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointServerlessFunction.push(endpointServerlessFunctions);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.ModifyEndpointCreationFile = async (endpointId, endpointCreationFileInfo, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointsCreationFile = endpointCreationFileInfo;

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointRequestInfo = async (endpointId, endpointRequestInfo, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.AllEndpointRequestInfo.push(endpointRequestInfo);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
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