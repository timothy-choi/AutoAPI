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
        EndpointDescription: endpointsInfo.description,
        ServerlessFunctionMode: endpointsInfo.servelessFunctionMode
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

exports.RemoveEndpointHeader = async (endpointId, endpointHeaderId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointHeaders.filter(endpointHeader => endpointHeader.id != endpointHeaderId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointHeader = async (endpointId, endpointHeaderId, updatedEndpointHeader, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointHeaders.findIndex(endpointHeader = endpointHeader.id != endpointHeaderId);

        endpoint.EndpointHeaders.splice(index, 1);

        endpoint.EndpointHeaders.splice(index, 0, updatedEndpointHeader);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
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

exports.RemoveEndpointModel = async (endpointId, endpointModelId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointModels.filter(endpointModel => endpointModel.id != endpointModelId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointModel = async (endpointId, endpointModelId, updatedEndpointModel, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointModels.findIndex(endpointModel = endpointModel.id != endpointModelId);

        endpoint.EndpointModels.splice(index, 1);

        endpoint.EndpointModels.splice(index, 0, updatedEndpointModel);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
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

exports.RemoveEndpointDatabase = async (endpointId, endpointDatabaseId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointDatabases.filter(endpointDatabase => endpointDatabase.id != endpointDatabaseId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointDatabase = async (endpointId, endpointDatabaseId, updatedEndpointDatabase, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointDatabases.findIndex(endpointDatabase = endpointDatabase.id != endpointDatabaseId);

        endpoint.EndpointDatabases.splice(index, 1);

        endpoint.EndpointDatabases.splice(index, 0, updatedEndpointDatabase);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
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

exports.RemoveEndpointServerlessFunctions = async (endpointId, endpointServerlessFunctionId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointServerlessFunction.filter(endpointFunction => endpointFunction.id != endpointServerlessFunctionId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointServerlessFunction = async (endpointId, endpointServerlessFunctionId, updatedEndpointServerlessFunction, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointServerlessFunction.findIndex(endpointFunction = endpointFunction.id != endpointServerlessFunctionId);

        endpoint.EndpointServerlessFunction.splice(index, 1);

        endpoint.EndpointServerlessFunction.splice(index, 0, updatedEndpointServerlessFunction);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
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

exports.RemoveEndpointRequestInfo = async (endpointId, endpointRequestInfoId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.AllEndpointRequestInfo.filter(endpointRequestInfo => endpointRequestInfo.id != endpointRequestInfoId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointRequestInfo = async (endpointId, endpointRequestInfoId, updatedEndpointRequestInfo, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.AllEndpointRequestInfo.findIndex(endpointRequestInfo = endpointRequestInfo.id != endpointRequestInfoId);

        endpoint.AllEndpointRequestInfo.splice(index, 1);

        endpoint.AllEndpointRequestInfo.splice(index, 0, updatedEndpointRequestInfo);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}


exports.AddEndpointImplementationInfo = async (endpointId, endpointImplementationInfo, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.AllEndpointImplementationInfo.push(endpointImplementationInfo);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.RemoveEndpointImplementationInfo = async (endpointId, endpointImplementationInfoId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.AllEndpointImplementationInfo.filter(endpointImplementationInfo => endpointImplementationInfo.id != endpointImplementationInfoId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditEndpointImplementationInfo = async (endpointId, endpointImplementationInfoId, updatedEndpointImplementationInfo, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.AllEndpointImplementationInfo.findIndex(endpointImplementtaionInfo = endpointImplementationInfo.id != endpointImplementationInfoId);

        endpoint.AllEndpointImplementationInfo.splice(index, 1);

        endpoint.AllEndpointImplementationInfo.splice(index, 0, updatedEndpointImplementationInfo);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.AddApiEndpointStatus = async (endpointId, apiEndpointStatus, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointStatus.push(apiEndpointStatus);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.EditApiEndpointStatus = async (endpointId, endpointStatusId, updatedEndpointStatus, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointStatus.findIndex(endpointStatus = endpointStatus.id != endpointStatusId);

        endpoint.EndpointStatus.splice(index, 1);

        endpoint.EndpointStatus.splice(index, 0, updatedEndpointStatus);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.ModifyEndpointsDescription = async (endpointId, endpointsDescription, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointsDescription = endpointsDescription;

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointResponseSchema = async (endpointId, endpointResponseSchema, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointResponseSchema.push(endpointResponseSchema);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.RemoveApiEndpointResponseSchema = async (endpointId, endpointResponseSchemaId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointResponseSchema.filter(endpointResponseSchema => endpointResponseSchema.id != endpointResponseSchemaId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditApiEndpointResponseSchema = async (endpointId, endpointResponseSchemaId, updatedEndpointResponseSchema, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointResponseSchema.findIndex(endpointResponseSchema = endpointResponseSchema.id != endpointResponseSchemaId);

        endpoint.EndpointResponseSchema.splice(index, 1);

        endpoint.EndpointResponseSchema.splice(index, 0, updatedEndpointResponseSchema);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.AddEndpointResponseExample = async (endpointId, endpointResponseExample, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointResponseExample.push(endpointResponseExample);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.RemoveApiEndpointResponseExample = async (endpointId, endpointResponseExampleId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointResponseExample.filter(endpointResponseExample => endpointResponseExample.id != endpointResponseExampleId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditApiEndpointResponseExample = async (endpointId, endpointResponseExampleId, updatedEndpointResponseExample, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointResponseExample.findIndex(endpointResponseExample = endpointResponseExample.id != endpointResponseExampleId);

        endpoint.EndpointResponseExample.splice(index, 1);

        endpoint.EndpointResponseExample.splice(index, 0, updatedEndpointResponseExample);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.SetLastAccessedAt = async (endpointId, accessDate) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.LastAccessedAt = accessDate;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.AddEndpointDependencies = async (endpointId, endpointDependencies, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointDependencies.push(endpointDependencies);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.RemoveApiEndpointDependency = async (endpointId, endpointDependencyId, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('endpoint does not exist');
        } 

        endpoint.EndpointDependencies.filter(endpointDependency => endpointDependency.id != endpointDependencyId);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}

exports.EditApiEndpointDependency = async (endpointId, endpointDependencyId, updatedEndpointDependency, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Error('Model does not exist');
        } 

        var index = endpoint.EndpointDependencies.findIndex(endpointDependency = endpointDependency.id != endpointDependencyId);

        endpoint.EndpointDependencies.splice(index, 1);

        endpoint.EndpointDependencies.splice(index, 0, updatedEndpointDependency);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
}


exports.AddEndpointVersionHistory = async (endpointId, endpointVersionHistory, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointVersionHistory.push(endpointVersionHistory);

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.ModifyEndpointMetrics = async (endpointId, endpointMetrics, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointMetrics = endpointMetrics;

        endpoint.DidUpdate = true;

        endpoint.UpdatedAt = Date.now();

        endpoint.UpdatedBy = username;

        await endpoint.save();
    } catch (error) {
        throw new Exception('Can not edit database');
    }
}

exports.ModifyServerlessFunctionFile = async (endpointId, serverlessFunctionFile, username) => {
    try {
        var endpoint = await this.GetEndpointsById(endpointId);

        if (!endpoint) {
            throw new Exception('database does not exist');
        }

        endpoint.EndpointServerlessFunctionFile = serverlessFunctionFile;

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