const ApiDeployment = require('./ApiDeployment');

exports.getApiDeploymentById = async (deploymentId) => {
    try {
        var uuidVal = mongoose.types.ObjectId(documentationId);

        var deployment = await ApiDeployment.findById(uuidVal);

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.getApiDeploymentByProjectId = async (projectId) => {
    try {
        var deployment = await ApiDeployment.findOne({ where: { ProjectId: projectId } });

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message);
    }
};

exports.createApiDeployment = async (deploymentInfo) => {
    try {
        var deployment = await this.getApiDeploymentByProjectId(deploymentInfo.projectId);

        if (deployment) {
            throw new Error("Instance already exists");
        }

        deployment = new ApiDeployment({
            ProjectId: ProjectId
        });

        await deployment.save();

        return deployment;
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setVersion = async (deploymentId, version) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Version = version;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setBaseUrl = async (deploymentId, baseUrl) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.BaseUrl = baseUrl;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setStatus = async (deploymentId, status) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Status = status;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setEnvironment = async (deploymentId, environment) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.Environment = environment;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedAt = async (deploymentId) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeployedAt = Date.now;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedDuration = async (deploymentId, deployedDuration) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeployedDuration = deployedDuration;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedTarget = async (deploymentId, deployedTarget) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeploymentTarget = deployedTarget;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedMetadata = async (deploymentId, deployedMetadata) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeploymentMetadata = deployedMetadata;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDeployedRollbackInfo = async (deploymentId, rollbackInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.RollbackInfo = rollbackInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.addApiModel = async (deploymentId, apiModel) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiModels.push(apiModel);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.removeApiModel = async (deploymentId, apiModelId) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiModels.filter(model => model.id != apiModelId);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.EditApiModel = async (deploymentId, apiModelId, updatedModel) => {
    try {
        var deployment = await this.getApiDeploymentById(deploymentId);

        if (!deployment) {
            throw new Error('Model does not exist');
        } 

        var index = deployment.AllApiModels.findIndex(model = model.id != apiModelId);

        deployment.AllApiModels.splice(index, 1);

        deployment.AllApiModels.splice(index, 0, updatedModel);

        deployment.UpdatedAt = Date.now();

        await deployment.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
};

exports.addApiEndpoint = async (deploymentId, apiEndpoint) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiEndpoints.push(apiEndpoint);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.removeApiEndpoint = async (deploymentId, apiEndpointId) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiEndpoints.filter(endpoint => endpoint.id != apiEndpointId);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.EditApiEndpoint = async (deploymentId, apiEndpointId, updatedEndpoint) => {
    try {
        var deployment = await this.getApiDeploymentById(deploymentId);

        if (!deployment) {
            throw new Error('Model does not exist');
        } 

        var index = deployment.AllApiEndpoints.findIndex(endpoint = endpoint.id != apiEndpointId);

        deployment.AllApiEndpoints.splice(index, 1);

        deployment.AllApiEndpoints.splice(index, 0, updatedEndpoint);

        deployment.UpdatedAt = Date.now();

        await deployment.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
};

exports.addApiDatabase = async (deploymentId, apiDB) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiDatabases.push(apiDB);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.removeApiDatabase = async (deploymentId, apiDbId) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.AllApiDatabases.filter(db => db.id != apiDbId);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.EditApiDatabase = async (deploymentId, apiDbId, updatedDb) => {
    try {
        var deployment = await this.getApiDeploymentById(deploymentId);

        if (!deployment) {
            throw new Error('Model does not exist');
        } 

        var index = deployment.AllApiDatabases.findIndex(db = db.id != apiDbId);

        deployment.AllApiDatabases.splice(index, 1);

        deployment.AllApiDatabases.splice(index, 0, updatedDb);

        deployment.UpdatedAt = Date.now();

        await deployment.save();
    } catch (error) {
        throw new Error('could not delete model');
    }
};

exports.setAuthentication = async (deploymentId, authInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.ApiAuthentication = authInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setDocumentation = async (deploymentId, docInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.ApiDocumentation = docInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setMonitoring = async (deploymentId, monitoringInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.ApiMonitoring = monitoringInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.setGateway = async (deploymentId, gatewayInfo) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.ApiGateway = gatewayInfo;

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.addDeploymentLog = async (deploymentId, deploymentLog) => {
    try {
        var documentation = await this.getApiDeploymentById(deploymentId);

        if (!documentation) {
            throw new Error("Instance does not exist");
        }

        documentation.DeploymentLogs.push(deploymentLog);

        documentation.UpdatedAt = Date.now;

        await documentation.save();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};

exports.deleteApiDeployment = async (deploymentId) => {
    try {
        var deployment = await this.getApiDeploymentById(deploymentId);

        if (!deployment) {
            throw new Error("Instance does not exist");
        }

        await deployment.destroy();
    } catch (error) {
        throw new Error("Error:", error.message)
    }
};